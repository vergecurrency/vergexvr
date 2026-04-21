package com.vergepay.wearablesmeta.wallet;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.vergepay.core.wallet.WalletSummaryContract;
import com.vergepay.wearablesmeta.R;

public final class WalletSummaryRepository {
    @NonNull
    public static Uri contentUri() {
        return Uri.parse(WalletSummaryContract.CONTENT_URI);
    }

    private WalletSummaryRepository() { }

    @NonNull
    public static Snapshot load(@NonNull final Context context) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(contentUri(), null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                final boolean hasAccount = cursor.getInt(cursor.getColumnIndexOrThrow(
                        WalletSummaryContract.COLUMN_HAS_ACCOUNT)) != 0;
                return new Snapshot(
                        hasAccount,
                        cursor.getString(cursor.getColumnIndexOrThrow(
                                WalletSummaryContract.COLUMN_AMOUNT_TEXT)),
                        cursor.getString(cursor.getColumnIndexOrThrow(
                                WalletSummaryContract.COLUMN_FIAT_TEXT)),
                        cursor.getString(cursor.getColumnIndexOrThrow(
                                WalletSummaryContract.COLUMN_STATUS_TEXT)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(
                                WalletSummaryContract.COLUMN_IS_CONNECTED)) != 0,
                        cursor.getLong(cursor.getColumnIndexOrThrow(
                                WalletSummaryContract.COLUMN_UPDATED_AT)),
                        context.getString(hasAccount
                                ? R.string.wearables_hint_phone_wallet
                                : R.string.wearables_hint_open_wallet));
            }
        } catch (final SecurityException e) {
            return unavailable(context, R.string.wearables_wallet_permission_error,
                    R.string.wearables_hint_signing_required);
        } catch (final IllegalArgumentException e) {
            return unavailable(context, R.string.wearables_wallet_missing_status,
                    R.string.wearables_hint_install_wallet);
        } catch (final Exception e) {
            return unavailable(context, R.string.wearables_wallet_unavailable_status,
                    R.string.wearables_hint_open_wallet);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return unavailable(context, R.string.wearables_wallet_unavailable_status,
                R.string.wearables_hint_open_wallet);
    }

    @NonNull
    private static Snapshot unavailable(@NonNull final Context context,
                                        final int statusResId,
                                        final int detailResId) {
        return new Snapshot(
                false,
                context.getString(R.string.wearables_waiting_amount),
                context.getString(R.string.wearables_waiting_value),
                context.getString(statusResId),
                false,
                0L,
                context.getString(detailResId));
    }

    public static final class Snapshot {
        public final boolean hasAccount;
        public final String amountText;
        public final String fiatText;
        public final String statusText;
        public final boolean connected;
        public final long updatedAtMs;
        public final String detailText;

        private Snapshot(final boolean hasAccount, @NonNull final String amountText,
                         @NonNull final String fiatText, @NonNull final String statusText,
                         final boolean connected, final long updatedAtMs,
                         @NonNull final String detailText) {
            this.hasAccount = hasAccount;
            this.amountText = amountText;
            this.fiatText = fiatText;
            this.statusText = statusText;
            this.connected = connected;
            this.updatedAtMs = updatedAtMs;
            this.detailText = detailText;
        }
    }
}
