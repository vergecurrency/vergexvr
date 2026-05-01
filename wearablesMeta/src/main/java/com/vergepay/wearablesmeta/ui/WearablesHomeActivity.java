package com.vergepay.wearablesmeta.ui;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.vergepay.core.wallet.WalletSummaryContract;
import com.vergepay.wearablesmeta.R;
import com.vergepay.wearablesmeta.wallet.WalletSummaryRepository;

public class WearablesHomeActivity extends AppCompatActivity {
    private TextView amountView;
    private TextView valueView;
    private TextView statusView;
    private TextView detailView;

    private final ContentObserver summaryObserver =
            new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override
                public void onChange(boolean selfChange) {
                    refreshSummary();
                }
            };

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wearables_home);

        amountView = findViewById(R.id.wearables_amount);
        valueView = findViewById(R.id.wearables_value);
        statusView = findViewById(R.id.wearables_status);
        detailView = findViewById(R.id.wearables_detail);

        findViewById(R.id.wearables_panel).setOnClickListener(v -> openWallet());
        findViewById(R.id.wearables_open_wallet).setOnClickListener(v -> openWallet());

        refreshSummary();
    }

    @Override
    protected void onResume() {
        super.onResume();
        getContentResolver().registerContentObserver(
                WalletSummaryRepository.contentUri(), true, summaryObserver);
        refreshSummary();
    }

    @Override
    protected void onPause() {
        getContentResolver().unregisterContentObserver(summaryObserver);
        super.onPause();
    }

    private void refreshSummary() {
        WalletSummaryRepository.Snapshot snapshot = WalletSummaryRepository.load(this);

        amountView.setText(snapshot.amountText);
        valueView.setText(snapshot.fiatText);
        statusView.setText(snapshot.statusText);

        if (snapshot.connected) {
            statusView.setTextColor(ContextCompat.getColor(this, R.color.wearables_status_ok));
        } else if (!snapshot.hasAccount) {
            statusView.setTextColor(ContextCompat.getColor(this, R.color.wearables_text_secondary));
        } else {
            statusView.setTextColor(ContextCompat.getColor(this, R.color.wearables_accent));
        }

        detailView.setText(buildDetail(snapshot));
    }

    private String buildDetail(final WalletSummaryRepository.Snapshot snapshot) {
        if (!snapshot.hasAccount) {
            return snapshot.detailText;
        }
        if (snapshot.updatedAtMs <= 0L) {
            return snapshot.detailText;
        }

        long ageMs = Math.max(0L, System.currentTimeMillis() - snapshot.updatedAtMs);
        if (ageMs < 10_000L) {
            return getString(R.string.wearables_hint_live_now);
        }
        if (ageMs < 60_000L) {
            return getString(R.string.wearables_hint_live_recent);
        }
        return getString(R.string.wearables_hint_cached_minutes, Math.max(1L, ageMs / 60_000L));
    }

    private void openWallet() {
        try {
            startActivity(new android.content.Intent()
                    .setComponent(new ComponentName(
                            WalletSummaryContract.WALLET_PACKAGE,
                            WalletSummaryContract.WALLET_ACTIVITY_CLASS))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP));
        } catch (final ActivityNotFoundException e) {
            Toast.makeText(this, R.string.wearables_wallet_not_installed, Toast.LENGTH_SHORT).show();
        }
    }
}
