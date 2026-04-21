package com.vergepay.wallet.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.vergepay.core.wallet.WalletAccount;
import com.vergepay.wallet.R;
import com.vergepay.wallet.service.CoinService;
import com.vergepay.wallet.ui.summary.WalletSummaryData;
import com.vergepay.wallet.ui.summary.WalletSummaryRefresh;
import com.vergepay.wallet.util.ThrottlingWalletChangeListener;

import org.bitcoinj.utils.Threading;

public class WalletGlanceActivity extends BaseWalletActivity {
    private TextView amountView;
    private TextView valueView;

    @Nullable private BroadcastReceiver summaryReceiver;
    @Nullable private WalletAccount observedAccount;

    private final ThrottlingWalletChangeListener walletChangeListener =
            new ThrottlingWalletChangeListener() {
                @Override
                public void onThrottledWalletChanged() {
                    refreshSummary();
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wallet_glance);

        amountView = findViewById(R.id.wallet_glance_amount);
        valueView = findViewById(R.id.wallet_glance_value);
        findViewById(R.id.glance_panel).setOnClickListener(v -> openWallet());

        WindowInsetsHelper.applyPaddingInsets(findViewById(R.id.glance_root), true, true);
        refreshSummary();
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindPrimaryAccount();
        registerSummaryReceiver();
        refreshSummary();
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        getWalletApplication().startTor();
        getWalletApplication().startBlockchainService(CoinService.ServiceMode.CANCEL_COINS_RECEIVED);

        WalletAccount account = WalletSummaryData.getPrimaryAccount(this);
        if (account != null) {
            getWalletApplication().maybeConnectAccount(account);
        }
    }

    @Override
    protected void onPause() {
        unregisterSummaryReceiver();
        unbindPrimaryAccount();
        super.onPause();
    }

    private void refreshSummary() {
        WalletSummaryData.Snapshot snapshot = WalletSummaryData.load(this);
        amountView.setText(snapshot.amountText);
        valueView.setText(snapshot.fiatText);
    }

    private void bindPrimaryAccount() {
        WalletAccount primaryAccount = WalletSummaryData.getPrimaryAccount(this);
        if (observedAccount == primaryAccount) {
            return;
        }

        if (observedAccount != null) {
            observedAccount.removeEventListener(walletChangeListener);
        }

        observedAccount = primaryAccount;
        if (observedAccount != null) {
            observedAccount.addEventListener(walletChangeListener, Threading.SAME_THREAD);
        }
    }

    private void unbindPrimaryAccount() {
        if (observedAccount != null) {
            observedAccount.removeEventListener(walletChangeListener);
            observedAccount = null;
        }
        walletChangeListener.removeCallbacks();
    }

    private void registerSummaryReceiver() {
        if (summaryReceiver != null) {
            return;
        }

        summaryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                bindPrimaryAccount();
                refreshSummary();
            }
        };

        IntentFilter filter = new IntentFilter(WalletSummaryRefresh.ACTION_SUMMARY_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(summaryReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(summaryReceiver, filter);
        }
    }

    private void unregisterSummaryReceiver() {
        if (summaryReceiver == null) {
            return;
        }

        unregisterReceiver(summaryReceiver);
        summaryReceiver = null;
    }

    private void openWallet() {
        Intent intent = new Intent(this, WalletActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }
}
