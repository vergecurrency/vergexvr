package com.vergepay.wallet.ui.summary;

import android.content.Context;
import android.content.Intent;

public final class WalletSummaryRefresh {
    public static final String ACTION_SUMMARY_CHANGED =
            "com.vergepay.wallet.intent.action.WALLET_SUMMARY_CHANGED";

    private WalletSummaryRefresh() { }

    public static void refreshAll(Context context) {
        Context appContext = context.getApplicationContext();
        WalletSummaryWidgetProvider.refreshWidgets(appContext);
        Intent intent = new Intent(ACTION_SUMMARY_CHANGED).setPackage(appContext.getPackageName());
        appContext.sendBroadcast(intent);
    }
}
