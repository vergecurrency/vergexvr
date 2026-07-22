package com.vergepay.wallet.tor;

import androidx.annotation.Nullable;

final class TorConnectionAttemptPlan {
    private static final String[] TRANSPORTS = {null, "obfs4", "snowflake", "meek"};

    private int attemptIndex;

    void reset() {
        attemptIndex = 0;
    }

    @Nullable
    String getCurrentTransport() {
        return TRANSPORTS[attemptIndex];
    }

    @Nullable
    String advance() {
        if (attemptIndex + 1 >= TRANSPORTS.length) {
            return null;
        }
        attemptIndex++;
        return TRANSPORTS[attemptIndex];
    }

    boolean isDirect() {
        return attemptIndex == 0;
    }

    static String pluginNameFor(String transport) {
        return "snowflake".equals(transport) ? "snowflake" : "lyrebird";
    }
}
