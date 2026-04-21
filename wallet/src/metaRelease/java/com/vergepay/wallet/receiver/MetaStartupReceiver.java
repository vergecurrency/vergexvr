package com.vergepay.wallet.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;

import com.vergepay.wallet.Configuration;
import com.vergepay.wallet.ui.WalletActivity;
import com.vergepay.wallet.ui.WalletGlanceActivity;

public class MetaStartupReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }

        Configuration configuration = new Configuration(
                PreferenceManager.getDefaultSharedPreferences(context));
        String startupTarget = configuration.getMetaStartupTarget();

        Class<?> activityClass;
        if (Configuration.PREFS_VALUE_META_STARTUP_GLANCE.equals(startupTarget)) {
            activityClass = WalletGlanceActivity.class;
        } else if (Configuration.PREFS_VALUE_META_STARTUP_WALLET.equals(startupTarget)) {
            activityClass = WalletActivity.class;
        } else {
            return;
        }

        Intent launchIntent = new Intent(context, activityClass);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(launchIntent);
    }
}
