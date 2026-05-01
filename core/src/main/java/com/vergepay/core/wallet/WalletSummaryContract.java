package com.vergepay.core.wallet;

public final class WalletSummaryContract {
    public static final String WALLET_PACKAGE = "com.vergepay.wallet";
    public static final String WALLET_ACTIVITY_CLASS = "com.vergepay.wallet.ui.WalletActivity";
    public static final String PROVIDER_PERMISSION =
            "com.vergepay.wallet.permission.READ_WALLET_SUMMARY";

    public static final String AUTHORITY = WALLET_PACKAGE + ".wallet_summary";
    public static final String PATH_SNAPSHOT = "snapshot";
    public static final String CONTENT_URI = "content://" + AUTHORITY + "/" + PATH_SNAPSHOT;

    public static final String COLUMN_ROW_ID = "_id";
    public static final String COLUMN_HAS_ACCOUNT = "has_account";
    public static final String COLUMN_ACCOUNT_ID = "account_id";
    public static final String COLUMN_AMOUNT_TEXT = "amount_text";
    public static final String COLUMN_FIAT_TEXT = "fiat_text";
    public static final String COLUMN_STATUS_TEXT = "status_text";
    public static final String COLUMN_IS_CONNECTED = "is_connected";
    public static final String COLUMN_UPDATED_AT = "updated_at";

    private WalletSummaryContract() { }
}
