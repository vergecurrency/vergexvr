package com.vergepay.wallet.tor;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.AssetManager;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.vergepay.wallet.Constants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.torproject.jni.TorService;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TorManager {
    private static final Logger log = LoggerFactory.getLogger(TorManager.class);
    private static final long DIRECT_BOOTSTRAP_TIMEOUT_MS = 60_000L;
    private static final long TRANSPORT_BOOTSTRAP_TIMEOUT_MS = 90_000L;
    private static final long TOR_STOP_TIMEOUT_MS = 15_000L;
    private static final long BOOTSTRAP_POLL_INTERVAL_MS = 1_000L;
    private static final String LYREBIRD_ASSET_ROOT = Constants.TOR_ASSET_DIR + "/lyrebird";
    private static final String PT_CONFIG_FILE_NAME = "pt_config.json";

    private final Context context;
    private final Object lock = new Object();
    private final TorConnectionAttemptPlan attemptPlan = new TorConnectionAttemptPlan();
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
                serviceBound = false;
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
    private boolean restartInProgress;
    private boolean stoppedForRestart;
    private int attemptGeneration;
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
            ready = false;
            monitoringBootstrap = false;
            restartInProgress = false;
            stoppedForRestart = false;
            attemptPlan.reset();
        }

        try {
            registerReceiverIfNeeded();
            startTor(null);
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

    private void prepareTorrc(@Nullable String bridgeTransport)
            throws IOException, JSONException {
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
        if (bridgeTransport != null) {
            BridgeConfig bridgeConfig = loadBridgeConfig(bridgeTransport);
            builder.append("UseBridges 1\n");
            builder.append(bridgeConfig.transportPluginLine).append('\n');
            for (String bridgeLine : bridgeConfig.bridgeLines) {
                builder.append("Bridge ").append(bridgeLine).append('\n');
            }
        }

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
        final int monitorGeneration;
        synchronized (lock) {
            if (monitoringBootstrap || ready || torService == null) {
                return;
            }
            monitoringBootstrap = true;
            monitorGeneration = attemptGeneration;
        }

        Thread bootstrapMonitor = new Thread(() -> {
            long startTime = System.currentTimeMillis();
            final long bootstrapTimeout = attemptPlan.isDirect()
                    ? DIRECT_BOOTSTRAP_TIMEOUT_MS : TRANSPORT_BOOTSTRAP_TIMEOUT_MS;
            while (System.currentTimeMillis() - startTime < bootstrapTimeout) {
                TorService service;
                synchronized (lock) {
                    if (monitorGeneration != attemptGeneration || !starting || ready) {
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
            synchronized (lock) {
                if (monitorGeneration != attemptGeneration) {
                    return;
                }
            }
            handleFailure("bootstrap timeout");
        }, "tor-bootstrap-monitor");
        bootstrapMonitor.setDaemon(true);
        bootstrapMonitor.start();
    }

    private void handleTorServiceStatus(@Nullable String status) {
        log.info("tor-android status {}", status);
        if (TorService.STATUS_ON.equals(status) || TorService.STATUS_BOOTSTRAPPED_100.equals(status)) {
            synchronized (lock) {
                if (restartInProgress) {
                    log.info("Ignoring ready status while changing Tor transport");
                    return;
                }
                ready = true;
                starting = false;
                monitoringBootstrap = false;
                restartInProgress = false;
            }
            broadcastStatus(Constants.TOR_STATUS_READY);
        } else if (TorService.STATUS_STARTING.equals(status)) {
            synchronized (lock) {
                if (ready) {
                    log.info("Ignoring late STARTING status after Tor is already ready");
                    return;
                }
            }
            broadcastStatus(Constants.TOR_STATUS_STARTING);
        } else if (TorService.STATUS_STOPPING.equals(status) || TorService.STATUS_OFF.equals(status)) {
            boolean wasReady;
            synchronized (lock) {
                if (restartInProgress) {
                    if (TorService.STATUS_OFF.equals(status)) {
                        stoppedForRestart = true;
                        starting = false;
                        ready = false;
                        monitoringBootstrap = false;
                        lock.notifyAll();
                        log.info("Tor stopped before changing transport");
                    } else {
                        log.info("Tor is stopping before changing transport");
                    }
                    return;
                }
                wasReady = ready;
                starting = false;
                ready = false;
                monitoringBootstrap = false;
            }
            if (TorService.STATUS_OFF.equals(status) && !wasReady) {
                handleFailure("tor reported off before bootstrap completed");
            } else {
                broadcastStatus(Constants.TOR_STATUS_STOPPED);
            }
        }
    }

    private void handleFailure() {
        handleFailure("unknown");
    }

    private void handleFailure(String reason) {
        String retryTransport = null;
        synchronized (lock) {
            if (restartInProgress) {
                log.info("Ignoring duplicate Tor failure while changing transport: {}", reason);
                return;
            }
            starting = false;
            ready = false;
            monitoringBootstrap = false;
            retryTransport = attemptPlan.advance();
            if (retryTransport != null) {
                restartInProgress = true;
            }
        }

        if (retryTransport != null) {
            log.warn("Tor startup failed ({}), retrying with {} transport", reason,
                    retryTransport);
            retryWithBridgeFallback(retryTransport);
            return;
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

    private void startTor(@Nullable String bridgeTransport)
            throws IOException, JSONException {
        prepareTorrc(bridgeTransport);
        synchronized (lock) {
            attemptGeneration++;
            starting = true;
            monitoringBootstrap = false;
        }
        TorService.setBroadcastPackageName(context.getPackageName());
        Intent serviceIntent = new Intent(context, TorService.class);
        serviceIntent.setAction(TorService.ACTION_START);
        context.startService(serviceIntent);
        bindTorService(serviceIntent);
        broadcastStatus(Constants.TOR_STATUS_STARTING);
    }

    private void retryWithBridgeFallback(final String bridgeTransport) {
        Thread restartThread = new Thread(() -> {
            try {
                stopTorAndWait();
                startTor(bridgeTransport);
                synchronized (lock) {
                    restartInProgress = false;
                }
                startBootstrapMonitor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                synchronized (lock) {
                    restartInProgress = false;
                }
                broadcastStatus(Constants.TOR_STATUS_FAILED);
            } catch (Exception e) {
                log.error("Failed to restart tor-android with bridge fallback", e);
                synchronized (lock) {
                    restartInProgress = false;
                }
                broadcastStatus(Constants.TOR_STATUS_FAILED);
            }
        }, "tor-bridge-restart");
        restartThread.setDaemon(true);
        restartThread.start();
    }

    private void stopTorAndWait() throws IOException, InterruptedException {
        synchronized (lock) {
            stoppedForRestart = false;
        }

        Intent stopIntent = new Intent(context, TorService.class);
        stopIntent.setAction(TorService.ACTION_STOP);
        context.startService(stopIntent);

        long deadline = System.currentTimeMillis() + TOR_STOP_TIMEOUT_MS;
        synchronized (lock) {
            while (!stoppedForRestart) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    throw new IOException("Timed out waiting for Tor to stop before transport change");
                }
                lock.wait(remaining);
            }
        }
    }

    private BridgeConfig loadBridgeConfig(@Nullable String requestedTransport)
            throws IOException, JSONException {
        String assetAbi = resolveSupportedAssetAbi();
        String assetDir = LYREBIRD_ASSET_ROOT + "/" + assetAbi;
        File ptDir = new File(new File(context.getFilesDir(), Constants.TOR_ASSET_DIR), assetAbi);
        if (!ptDir.exists() && !ptDir.mkdirs()) {
            throw new IOException("Unable to create pluggable transport dir " + ptDir);
        }

        File lyrebirdExecutable = getLyrebirdExecutable();

        File configFile = new File(ptDir, PT_CONFIG_FILE_NAME);
        copyAssetIfDifferent(assetDir + "/" + PT_CONFIG_FILE_NAME, configFile, false);

        String configContents = readFile(configFile);
        JSONObject configJson = new JSONObject(configContents);
        String transport = requestedTransport;
        if (transport == null || transport.trim().isEmpty()) {
            transport = configJson.optString("recommendedDefault", "obfs4");
        }
        transport = transport.trim().toLowerCase(Locale.US);

        JSONObject bridgeSets = configJson.optJSONObject("bridges");
        JSONArray bridges = bridgeSets != null ? bridgeSets.optJSONArray(transport) : null;
        if (bridges == null || bridges.length() == 0) {
            throw new IOException("No bridge definitions found for transport " + transport);
        }

        JSONObject transportPlugins = configJson.optJSONObject("pluggableTransports");
        String pluginName = TorConnectionAttemptPlan.pluginNameFor(transport);
        String pluginTemplate = transportPlugins != null
                ? transportPlugins.optString(pluginName, null) : null;
        if (pluginTemplate == null || pluginTemplate.trim().isEmpty()) {
            throw new IOException("Missing lyrebird transport plugin configuration");
        }

        String ptPrefix = ptDir.getAbsolutePath() + File.separator;
        String pluginLine = pluginTemplate
                .replace("${pt_path}lyrebird", lyrebirdExecutable.getAbsolutePath())
                .replace("${pt_path}", ptPrefix);
        List<String> bridgeLines = new ArrayList<>();
        for (int i = 0; i < bridges.length(); i++) {
            String bridgeLine = bridges.optString(i, "").trim();
            if (!bridgeLine.isEmpty()) {
                bridgeLines.add(bridgeLine);
            }
        }
        if (bridgeLines.isEmpty()) {
            throw new IOException("Bridge list for " + transport + " was empty");
        }

        return new BridgeConfig(pluginLine, bridgeLines);
    }

    private String resolveSupportedAssetAbi() throws IOException {
        for (String abi : Build.SUPPORTED_ABIS) {
            String normalizedAbi = normalizeAssetAbi(abi);
            if (normalizedAbi == null) {
                continue;
            }
            if (assetExists(LYREBIRD_ASSET_ROOT + "/" + normalizedAbi + "/"
                    + PT_CONFIG_FILE_NAME) && getLyrebirdExecutable().isFile()) {
                return normalizedAbi;
            }
        }
        throw new IOException("No bundled lyrebird binary matches supported ABIs");
    }

    private File getLyrebirdExecutable() throws IOException {
        File nativeLibraryDir = new File(context.getApplicationInfo().nativeLibraryDir);
        File executable = new File(nativeLibraryDir, "liblyrebird.so");
        if (!executable.isFile() || !executable.canExecute()) {
            throw new IOException("Bundled lyrebird executable is unavailable for this device");
        }
        return executable;
    }

    @Nullable
    private String normalizeAssetAbi(String abi) {
        if (abi == null) {
            return null;
        }
        String normalized = abi.trim().toLowerCase(Locale.US);
        if ("arm64-v8a".equals(normalized) || "armeabi-v7a".equals(normalized)
                || "x86_64".equals(normalized)) {
            return normalized;
        }
        if ("aarch64".equals(normalized)) {
            return "arm64-v8a";
        }
        return null;
    }

    private boolean assetExists(String assetPath) {
        try (InputStream ignored = context.getAssets().open(assetPath)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void copyAssetIfDifferent(String assetPath, File destination, boolean executable)
            throws IOException {
        byte[] assetBytes = readAssetBytes(assetPath);
        if (destination.exists()) {
            byte[] existingBytes = readFileBytes(destination);
            if (java.util.Arrays.equals(assetBytes, existingBytes)) {
                if (executable && !destination.canExecute() && !destination.setExecutable(true, true)) {
                    throw new IOException("Unable to mark " + destination + " executable");
                }
                return;
            }
        }

        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Unable to create " + parent);
        }

        try (FileOutputStream outputStream = new FileOutputStream(destination, false)) {
            outputStream.write(assetBytes);
        }
        if (!destination.setReadable(true, true)) {
            throw new IOException("Unable to mark " + destination + " readable");
        }
        if (executable && !destination.setExecutable(true, true)) {
            throw new IOException("Unable to mark " + destination + " executable");
        }
    }

    private byte[] readAssetBytes(String assetPath) throws IOException {
        AssetManager assets = context.getAssets();
        try (InputStream inputStream = assets.open(assetPath);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8 * 1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toByteArray();
        }
    }

    private byte[] readFileBytes(File file) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(file);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8 * 1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toByteArray();
        }
    }

    private String readFile(File file) throws IOException {
        return new String(readFileBytes(file), Constants.UTF_8);
    }

    private static final class BridgeConfig {
        private final String transportPluginLine;
        private final List<String> bridgeLines;

        private BridgeConfig(String transportPluginLine, List<String> bridgeLines) {
            this.transportPluginLine = transportPluginLine;
            this.bridgeLines = bridgeLines;
        }
    }
}
