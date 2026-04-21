package com.vergepay.wallet.tor;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.vergepay.wallet.Constants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.torproject.jni.TorService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class TorManager {
    private static final Logger log = LoggerFactory.getLogger(TorManager.class);
    private static final long BOOTSTRAP_TIMEOUT_MS = 180_000L;
    private static final long BOOTSTRAP_POLL_INTERVAL_MS = 1_000L;

    private final Context context;
    private final Object lock = new Object();
    private final ServiceConnection torServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TorService.LocalBinder binder = (TorService.LocalBinder) service;
            synchronized (lock) {
                torService = binder.getService();
            }
            startBootstrapMonitor();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            synchronized (lock) {
                torService = null;
                monitoringBootstrap = false;
            }
        }
    };
    private final BroadcastReceiver torServiceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context receiverContext, Intent intent) {
            String action = intent.getAction();
            if (TorService.ACTION_STATUS.equals(action)) {
                handleTorServiceStatus(intent.getStringExtra(TorService.EXTRA_STATUS));
            } else if (TorService.ACTION_ERROR.equals(action)) {
                String error = intent.getStringExtra(Intent.EXTRA_TEXT);
                log.error("tor-android reported an error: {}", error);
                handleFailure();
            }
        }
    };

    private boolean receiverRegistered;
    private boolean starting;
    private boolean ready;
    private boolean serviceBound;
    private boolean monitoringBootstrap;
    private String lastStatus = Constants.TOR_STATUS_STOPPED;
    @Nullable private TorService torService;

    public TorManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public void start() {
        synchronized (lock) {
            if (starting || ready) {
                return;
            }
            starting = true;
        }

        try {
            registerReceiverIfNeeded();
            prepareTorrc();
            TorService.setBroadcastPackageName(context.getPackageName());
            Intent serviceIntent = new Intent(context, TorService.class);
            serviceIntent.setAction(TorService.ACTION_START);
            context.startService(serviceIntent);
            bindTorService(serviceIntent);
            broadcastStatus(Constants.TOR_STATUS_STARTING);
        } catch (Exception e) {
            log.error("Failed to start tor-android service", e);
            handleFailure();
        }
    }

    public boolean isReady() {
        synchronized (lock) {
            return ready;
        }
    }

    public String getStatus() {
        synchronized (lock) {
            return lastStatus;
        }
    }

    private void registerReceiverIfNeeded() {
        synchronized (lock) {
            if (receiverRegistered) {
                return;
            }
            IntentFilter filter = new IntentFilter();
            filter.addAction(TorService.ACTION_STATUS);
            filter.addAction(TorService.ACTION_ERROR);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(torServiceReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                context.registerReceiver(torServiceReceiver, filter);
            }
            receiverRegistered = true;
        }
    }

    private void prepareTorrc() throws IOException {
        File torrcFile = TorService.getTorrc(context);
        File torDir = torrcFile.getParentFile();
        if (torDir == null) {
            throw new IOException("Missing tor service directory");
        }
        if (!torDir.exists() && !torDir.mkdirs()) {
            throw new IOException("Unable to create " + torDir);
        }

        StringBuilder builder = new StringBuilder();
        builder.append("ClientOnly 1\n");
        builder.append("AvoidDiskWrites 1\n");
        builder.append("SafeLogging 1\n");
        builder.append("DormantCanceledByStartup 1\n");

        try (FileOutputStream outputStream = new FileOutputStream(torrcFile, false)) {
            outputStream.write(builder.toString().getBytes(Constants.UTF_8));
        }
    }

    private void bindTorService(Intent serviceIntent) {
        synchronized (lock) {
            if (serviceBound) {
                return;
            }
            serviceBound = context.bindService(serviceIntent, torServiceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    private void startBootstrapMonitor() {
        synchronized (lock) {
            if (monitoringBootstrap || ready || torService == null) {
                return;
            }
            monitoringBootstrap = true;
        }

        Thread bootstrapMonitor = new Thread(() -> {
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime < BOOTSTRAP_TIMEOUT_MS) {
                TorService service;
                synchronized (lock) {
                    if (!starting || ready) {
                        monitoringBootstrap = false;
                        return;
                    }
                    service = torService;
                }

                String bootstrapPhase = service != null ? service.getInfo("status/bootstrap-phase") : null;
                if (bootstrapPhase != null) {
                    log.info("tor-android bootstrap phase {}", bootstrapPhase);
                    if (bootstrapPhase.contains("PROGRESS=100")) {
                        synchronized (lock) {
                            ready = true;
                            starting = false;
                            monitoringBootstrap = false;
                        }
                        broadcastStatus(Constants.TOR_STATUS_READY);
                        return;
                    }
                }

                try {
                    Thread.sleep(BOOTSTRAP_POLL_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    synchronized (lock) {
                        monitoringBootstrap = false;
                    }
                    return;
                }
            }

            log.warn("Timed out waiting for tor-android bootstrap completion");
            handleFailure();
        }, "tor-bootstrap-monitor");
        bootstrapMonitor.setDaemon(true);
        bootstrapMonitor.start();
    }

    private void handleTorServiceStatus(@Nullable String status) {
        log.info("tor-android status {}", status);
        if (TorService.STATUS_ON.equals(status) || TorService.STATUS_BOOTSTRAPPED_100.equals(status)) {
            synchronized (lock) {
                ready = true;
                starting = false;
                monitoringBootstrap = false;
            }
            broadcastStatus(Constants.TOR_STATUS_READY);
        } else if (TorService.STATUS_STARTING.equals(status)) {
            broadcastStatus(Constants.TOR_STATUS_STARTING);
        } else if (TorService.STATUS_STOPPING.equals(status) || TorService.STATUS_OFF.equals(status)) {
            synchronized (lock) {
                starting = false;
                ready = false;
                monitoringBootstrap = false;
            }
            broadcastStatus(Constants.TOR_STATUS_STOPPED);
        }
    }

    private void handleFailure() {
        synchronized (lock) {
            starting = false;
            ready = false;
            monitoringBootstrap = false;
        }
        broadcastStatus(Constants.TOR_STATUS_FAILED);
    }

    private void broadcastStatus(String status) {
        synchronized (lock) {
            lastStatus = status;
        }
        Intent intent = new Intent(Constants.ACTION_TOR_STATUS);
        intent.setPackage(context.getPackageName());
        intent.putExtra(Constants.EXTRA_TOR_STATUS, status);
        context.sendBroadcast(intent);
    }
}
