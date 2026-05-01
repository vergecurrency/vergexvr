package com.vergepay.wallet.ui.summary;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.vergepay.core.wallet.WalletSummaryContract;

import java.util.List;

public class WalletSummaryProvider extends ContentProvider {
    @NonNull
    public static Uri contentUri() {
        return Uri.parse(WalletSummaryContract.CONTENT_URI);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(final Uri uri) {
        validate(uri);
        return "vnd.android.cursor.item/vnd." + WalletSummaryContract.AUTHORITY + ".snapshot";
    }

    @Override
    public Cursor query(final Uri uri, final String[] projection, final String selection,
                        final String[] selectionArgs, final String sortOrder) {
        validate(uri);

        MatrixCursor cursor = new MatrixCursor(new String[] {
                WalletSummaryContract.COLUMN_ROW_ID,
                WalletSummaryContract.COLUMN_HAS_ACCOUNT,
                WalletSummaryContract.COLUMN_ACCOUNT_ID,
                WalletSummaryContract.COLUMN_AMOUNT_TEXT,
                WalletSummaryContract.COLUMN_FIAT_TEXT,
                WalletSummaryContract.COLUMN_STATUS_TEXT,
                WalletSummaryContract.COLUMN_IS_CONNECTED,
                WalletSummaryContract.COLUMN_UPDATED_AT
        });

        WalletSummaryData.Snapshot snapshot = WalletSummaryData.load(getContext());
        cursor.addRow(new Object[] {
                0,
                snapshot.hasAccount ? 1 : 0,
                snapshot.accountId,
                snapshot.amountText,
                snapshot.fiatText,
                snapshot.statusText,
                snapshot.connected ? 1 : 0,
                snapshot.updatedAtMs
        });
        cursor.setNotificationUri(getContext().getContentResolver(), contentUri());

        return cursor;
    }

    @Override
    public Uri insert(final Uri uri, final ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(final Uri uri, final String selection, final String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(final Uri uri, final ContentValues values, final String selection,
                      final String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    private static void validate(@NonNull final Uri uri) {
        if (!WalletSummaryContract.AUTHORITY.equals(uri.getAuthority())) {
            throw new IllegalArgumentException(uri.toString());
        }

        final List<String> pathSegments = uri.getPathSegments();
        if (pathSegments.size() != 1
                || !WalletSummaryContract.PATH_SNAPSHOT.equals(pathSegments.get(0))) {
            throw new IllegalArgumentException(uri.toString());
        }
    }
}
