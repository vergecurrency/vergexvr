package com.vergepay.wallet.ui.summary;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;

import com.vergepay.wallet.R;
import com.vergepay.wallet.ui.WalletActivity;

public class WalletSummaryWidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        updateWidgets(context, appWidgetManager, appWidgetIds);
    }

    static void refreshWidgets(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName componentName = new ComponentName(context, WalletSummaryWidgetProvider.class);
        int[] widgetIds = manager.getAppWidgetIds(componentName);
        if (widgetIds == null || widgetIds.length == 0) {
            return;
        }

        updateWidgets(context, manager, widgetIds);
    }

    private static void updateWidgets(Context context, AppWidgetManager manager, int[] widgetIds) {
        WalletSummaryData.Snapshot snapshot = WalletSummaryData.load(context);
        PendingIntent launchWalletIntent = createLaunchWalletIntent(context);

        for (int widgetId : widgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.wallet_summary_widget);
            views.setTextViewText(R.id.wallet_summary_amount, snapshot.amountText);
            views.setTextViewText(R.id.wallet_summary_value, snapshot.fiatText);
            views.setOnClickPendingIntent(R.id.wallet_summary_root, launchWalletIntent);
            manager.updateAppWidget(widgetId, views);
        }
    }

    private static PendingIntent createLaunchWalletIntent(Context context) {
        Intent intent = new Intent(context, WalletActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getActivity(context, 0, intent, flags);
    }
}
