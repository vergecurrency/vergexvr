package com.vergepay.wallet.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.vergepay.wallet.Constants;
import com.vergepay.wallet.ui.BalanceFragment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TorStatusReceiver extends BroadcastReceiver {
    private static final Logger log = LoggerFactory.getLogger(TorStatusReceiver.class);

    private final BalanceFragment.Listener listener;

    public TorStatusReceiver(BalanceFragment.Listener listener) {
        this.listener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String status = intent.getStringExtra(Constants.EXTRA_TOR_STATUS);
        log.info("Received embedded Tor status {}", status);

        if (Constants.TOR_STATUS_READY.equals(status) && listener != null) {
            listener.onRefresh();
        }
    }
}
