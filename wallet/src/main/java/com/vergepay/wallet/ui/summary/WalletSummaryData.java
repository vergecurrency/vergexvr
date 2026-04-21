package com.vergepay.wallet.ui.summary;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.vergepay.core.coins.FiatValue;
import com.vergepay.core.coins.Value;
import com.vergepay.core.util.ExchangeRateBase;
import com.vergepay.core.util.GenericUtils;
import com.vergepay.core.wallet.WalletAccount;
import com.vergepay.wallet.Configuration;
import com.vergepay.wallet.R;
import com.vergepay.wallet.WalletApplication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class WalletSummaryData {
    private static final Logger log = LoggerFactory.getLogger(WalletSummaryData.class);

    private static final int AMOUNT_SHORT_PRECISION = 4;
    private static final int AMOUNT_SHIFT = 0;

    private static final String PREF_BALANCE_CACHE = "balance_fragment_cache";
    private static final String PREF_XVG_USD_RATE = "xvg_usd_rate";
    private static final String FALLBACK_XVG_USD_RATE = "0.0038";

    private WalletSummaryData() { }

    @Nullable
    public static WalletAccount getPrimaryAccount(Context context) {
        WalletApplication application = getApplication(context);
        if (application == null || application.getWallet() == null) {
            return null;
        }

        Configuration configuration = application.getConfiguration();
        String lastAccountId = configuration.getLastAccountId();
        WalletAccount lastAccount = lastAccountId != null ? application.getAccount(lastAccountId) : null;
        if (lastAccount != null) {
            return lastAccount;
        }

        List<WalletAccount> accounts = application.getAllAccounts();
        return accounts.isEmpty() ? null : accounts.get(0);
    }

    @Nullable
    public static Value readCachedRate(Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREF_BALANCE_CACHE, Context.MODE_PRIVATE);
        String cachedPrice = prefs.getString(PREF_XVG_USD_RATE, null);
        return parseRate(cachedPrice);
    }

    public static void cacheRate(Context context, String price) {
        context.getApplicationContext()
                .getSharedPreferences(PREF_BALANCE_CACHE, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_XVG_USD_RATE, price)
                .apply();
    }

    @NonNull
    public static Snapshot load(Context context) {
        WalletAccount account = getPrimaryAccount(context);
        if (account == null) {
            return new Snapshot(false, null,
                    context.getString(R.string.wallet_summary_empty_amount),
                    context.getString(R.string.wallet_summary_empty_value));
        }

        String amountText = GenericUtils.formatCoinValue(
                account.getCoinType(),
                account.getBalance().toCoin(),
                AMOUNT_SHORT_PRECISION,
                AMOUNT_SHIFT)
                + " " + account.getCoinType().getSymbol();

        String fiatText = context.getString(R.string.wallet_summary_value_unavailable);
        Value cachedRate = readCachedRate(context);
        if (cachedRate != null) {
            try {
                Value fiatAmount = new ExchangeRateBase(account.getCoinType().oneCoin(), cachedRate)
                        .convert(account.getCoinType(), account.getBalance().toCoin());
                fiatText = "$" + GenericUtils.formatFiatValue(fiatAmount) + " USD";
            } catch (Exception e) {
                log.warn("Could not format summary fiat value: {}", e.getMessage());
            }
        }

        return new Snapshot(true, account.getId(), amountText, fiatText);
    }

    @Nullable
    private static WalletApplication getApplication(Context context) {
        Context appContext = context.getApplicationContext();
        if (appContext instanceof WalletApplication) {
            return (WalletApplication) appContext;
        }
        return null;
    }

    @Nullable
    private static Value parseRate(@Nullable String price) {
        String valueToParse = (price == null || price.length() == 0) ? FALLBACK_XVG_USD_RATE : price;
        try {
            return FiatValue.parse("USD", valueToParse);
        } catch (Exception e) {
            log.warn("Could not read cached summary USD rate: {}", e.getMessage());
            if (!FALLBACK_XVG_USD_RATE.equals(valueToParse)) {
                try {
                    return FiatValue.parse("USD", FALLBACK_XVG_USD_RATE);
                } catch (Exception ignored) {
                    return null;
                }
            }
            return null;
        }
    }

    public static final class Snapshot {
        public final boolean hasAccount;
        @Nullable public final String accountId;
        public final String amountText;
        public final String fiatText;

        private Snapshot(boolean hasAccount, @Nullable String accountId,
                         String amountText, String fiatText) {
            this.hasAccount = hasAccount;
            this.accountId = accountId;
            this.amountText = amountText;
            this.fiatText = fiatText;
        }
    }
}
