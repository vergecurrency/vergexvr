package com.vergepay.wallet.ui;

import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import androidx.core.content.ContextCompat;
import androidx.loader.app.LoaderManager;
import androidx.loader.app.LoaderManager.LoaderCallbacks;
import androidx.loader.content.AsyncTaskLoader;
import androidx.loader.content.Loader;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Shader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.vergepay.core.coins.CoinType;
import com.vergepay.core.coins.FiatValue;
import com.vergepay.core.coins.Value;
import com.vergepay.core.util.ExchangeRateBase;
import com.vergepay.core.util.GenericUtils;
import com.vergepay.core.wallet.AbstractTransaction;
import com.vergepay.core.wallet.AbstractWallet;
import com.vergepay.core.wallet.WalletAccount;
import com.vergepay.core.wallet.WalletConnectivityStatus;
import com.vergepay.wallet.AddressBookProvider;
import com.vergepay.wallet.Configuration;
import com.vergepay.wallet.Constants;
import com.vergepay.wallet.R;
import com.vergepay.wallet.WalletApplication;
import com.vergepay.wallet.ui.widget.SwipeRefreshLayout;
import com.vergepay.wallet.util.NetworkUtils;
import com.vergepay.wallet.util.ThrottlingWalletChangeListener;
import com.vergepay.wallet.util.WeakHandler;
import com.google.common.collect.Lists;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.utils.Threading;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import android.view.animation.LinearInterpolator;

/**
 * Use the {@link BalanceFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class BalanceFragment extends WalletFragment implements LoaderCallbacks<List<AbstractTransaction>> {
    private static final Logger log = LoggerFactory.getLogger(BalanceFragment.class);

    private static final int WALLET_CHANGED = 0;
    private static final int UPDATE_VIEW = 1;
    private static final int CLEAR_LABEL_CACHE = 2;

    private static final int AMOUNT_FULL_PRECISION = 8;
    private static final int AMOUNT_MEDIUM_PRECISION = 6;
    private static final int AMOUNT_SHORT_PRECISION = 4;
    private static final int AMOUNT_SHIFT = 0;

    private static final int ID_TRANSACTION_LOADER = 0;
    private static final int ID_RATE_LOADER = 1;
    private static final String COINGECKO_XVG_TICKER_URL = "https://api.coingecko.com/api/v3/simple/price?ids=verge&vs_currencies=usd";
    private static final String CRYPTOCOMPARE_XVG_TICKER_URL = "https://min-api.cryptocompare.com/data/price?fsym=XVG&tsyms=USD";
    private static final String PREF_BALANCE_CACHE = "balance_fragment_cache";
    private static final String PREF_XVG_USD_RATE = "xvg_usd_rate";
    private static final String FALLBACK_XVG_USD_RATE = "0.0038";
    private static final long STATUS_GRADIENT_DURATION_MS = 2600L;

    private String accountId;
    private WalletAccount pocket;
    private CoinType type;
    private Coin currentBalance;
    private Value xvgUsdRate;

    private boolean isFullAmount = false;
    private WalletApplication application;
    private Configuration config;
    private final MyHandler handler = new MyHandler(this);
    private final ContentObserver addressBookObserver = new AddressBookObserver(handler);

    private ListView transactionRows;
    private SwipeRefreshLayout swipeContainer;
    private View emptyPocketMessage;
    private View getVergeMessage;
    private TextView accountBalance;
    private TextView accountExchangedBalance;
    private TextView connectionLabel;
    private ImageView connectedDot;
    private ImageView disconnectedDot;
    private TransactionsListAdapter adapter;
    private Listener listener;
    private ContentResolver resolver;
    @Nullable private BroadcastReceiver torStatusReceiver;
    @Nullable private ValueAnimator connectionLabelAnimator;
    @Nullable private LinearGradient connectionLabelGradient;
    private final Matrix connectionLabelGradientMatrix = new Matrix();

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param accountId of the account
     * @return A new instance of fragment InfoFragment.
     */
    public static BalanceFragment newInstance(String accountId) {
        BalanceFragment fragment = new BalanceFragment();
        Bundle args = new Bundle();
        args.putSerializable(Constants.ARG_ACCOUNT_ID, accountId);
        fragment.setArguments(args);
        return fragment;
    }

    public BalanceFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // The onCreateOptionsMenu is handled in com.vergepay.wallet.ui.AccountFragment
        setHasOptionsMenu(true);

        if (getArguments() != null) {
            accountId = getArguments().getString(Constants.ARG_ACCOUNT_ID);
        }
        //TODO
        pocket = application.getAccount(accountId);
        if (pocket == null) {
            Toast.makeText(getActivity(), R.string.no_such_pocket_error, Toast.LENGTH_LONG).show();
            return;
        }
        type = pocket.getCoinType();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_balance, container, false);
        addHeaderAndFooterToList(inflater, container, view);
        transactionRows = view.findViewById(R.id.transaction_rows);
        swipeContainer = view.findViewById(R.id.swipeContainer);
        emptyPocketMessage = view.findViewById(R.id.history_empty);
        getVergeMessage = view.findViewById(R.id.get_verge);
        accountBalance = view.findViewById(R.id.account_balance);
        accountExchangedBalance = view.findViewById(R.id.account_exchanged_balance);
        connectionLabel = view.findViewById(R.id.connection_label);
        connectedDot = view.findViewById(R.id.connected_dot);
        disconnectedDot = view.findViewById(R.id.disconnected_dot);
        transactionRows.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View itemView, int position, long id) {
                BalanceFragment.this.onItemClick(position);
            }
        });
        accountBalance.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onMainAmountClick();
            }
        });
        accountExchangedBalance.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onLocalAmountClick();
            }
        });

        setupSwipeContainer();

        // TODO show empty message
        // Hide empty message if have some transaction history
        if (pocket.getTransactions().size() > 0) {
            emptyPocketMessage.setVisibility(View.GONE);
            getVergeMessage.setVisibility(View.GONE);
        }

        setupAdapter(inflater);
        hydrateCachedRate();
        // Update the amount
        updateBalance(pocket.getBalance());

        return view;
    }

    private void setupAdapter(LayoutInflater inflater) {
        // Init list adapter
        adapter = new TransactionsListAdapter(inflater.getContext(), (AbstractWallet) pocket);
        adapter.setPrecision(AMOUNT_MEDIUM_PRECISION, 0);
        transactionRows.setAdapter(adapter);
    }

    private void setupSwipeContainer() {
        // Setup refresh listener which triggers new data loading
        swipeContainer.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if (listener != null) {
                    listener.onRefresh();
                }
            }
        });
        // Configure the refreshing colors
        swipeContainer.setColorSchemeResources(
                R.color.progress_bar_color_1,
                R.color.progress_bar_color_2,
                R.color.progress_bar_color_3,
                R.color.progress_bar_color_4);
    }

    private void addHeaderAndFooterToList(LayoutInflater inflater, ViewGroup container, View view) {
        ListView list = view.findViewById(R.id.transaction_rows);

        // Initialize header
        View header = inflater.inflate(R.layout.fragment_balance_header, null);
        list.addHeaderView(header, null, true);

        // Set a space in the end of the list
        View listFooter = new View(inflater.getContext());
        listFooter.setMinimumHeight(
                getResources().getDimensionPixelSize(R.dimen.activity_vertical_margin));
        list.addFooterView(listFooter);
    }

    private void setupConnectivityStatus() {
        updateConnectivityStatus();
        handler.sendMessageDelayed(handler.obtainMessage(WALLET_CHANGED), 2000);
    }

    public void onItemClick(int position) {
        if (position >= transactionRows.getHeaderViewsCount()) {
            // Note the usage of getItemAtPosition() instead of adapter's getItem() because
            // the latter does not take into account the header (which has position 0).
            Object obj = transactionRows.getItemAtPosition(position);

            if (obj != null && obj instanceof AbstractTransaction) {
                Intent intent = new Intent(getActivity(), TransactionDetailsActivity.class);
                intent.putExtra(Constants.ARG_ACCOUNT_ID, accountId);
                intent.putExtra(Constants.ARG_TRANSACTION_ID, ((AbstractTransaction) obj).getHashAsString());
                startActivity(intent);
            } else {
                Toast.makeText(getActivity(), getString(R.string.get_tx_info_error), Toast.LENGTH_LONG).show();
            }
        }
    }

    public void onMainAmountClick() {
        isFullAmount = !isFullAmount;
        updateView();
    }

    public void onLocalAmountClick() {
        if (listener != null) listener.onLocalAmountClick();
    }

    @Override
    public void onStart() {
        super.onStart();
        setupConnectivityStatus();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    // TODO use the ListView feature that shows a view on empty list. Check exchange rates fragment
    @Deprecated
    private void checkEmptyPocketMessage() {
        if (emptyPocketMessage.isShown()) {
            if (!pocket.isNew()) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        emptyPocketMessage.setVisibility(View.GONE);
                        getVergeMessage.setVisibility(View.GONE);
                    }
                });
            }
        }
    }

    private void updateBalance() {
        updateBalance(pocket.getBalance());
    }

    private void updateBalance(final Value newBalance) {
        currentBalance = newBalance.toCoin();

        updateView();
    }

    private void updateConnectivityStatus() {
        setConnectivityStatus(pocket.getConnectivityStatus());
    }

    private void setConnectivityStatus(final WalletConnectivityStatus connectivity) {
        if (!application.isConnected()) {
            applyConnectivityStatus(R.string.connection_status_network_waiting, false);
            return;
        }

        String torStatus = application.getTorStatus();
        if (Constants.TOR_STATUS_FAILED.equals(torStatus)) {
            applyConnectivityStatus(R.string.connection_status_tor_failed, false);
            return;
        }
        if (Constants.TOR_STATUS_STOPPED.equals(torStatus)) {
            applyConnectivityStatus(R.string.connection_status_tor_stopped, false);
            return;
        }
        if (!Constants.TOR_STATUS_READY.equals(torStatus)) {
            applyConnectivityStatus(R.string.connection_status_tor_starting, false);
            return;
        }

        switch (connectivity) {
            case CONNECTED:
                applyConnectivityStatus(R.string.connection_status_connected_over_tor, true);
                break;
            case LOADING:
                applyConnectivityStatus(R.string.connection_status_syncing_over_tor, false);
                break;
            case DISCONNECTED:
                if (hasLoadedWalletData()) {
                    applyConnectivityStatus(R.string.connection_status_reconnecting_over_tor, false);
                } else {
                    applyConnectivityStatus(R.string.connection_status_connecting_over_tor, false);
                }
                break;
            default:
                connectedDot.setVisibility(View.INVISIBLE);
                throw new RuntimeException("Unknown connectivity status: " + connectivity);
        }
    }

    private boolean hasLoadedWalletData() {
        return pocket != null && (!pocket.isNew() || (currentBalance != null && currentBalance.signum() > 0));
    }

    private void applyConnectivityStatus(int labelResId, boolean connected) {
        connectionLabel.setVisibility(View.VISIBLE);
        connectionLabel.setText(labelResId);
        connectedDot.setVisibility(connected ? View.VISIBLE : View.INVISIBLE);
        disconnectedDot.setVisibility(connected ? View.GONE : View.VISIBLE);
        startConnectionLabelAnimation();
    }

    private void startConnectionLabelAnimation() {
        if (connectionLabel == null) {
            return;
        }

        stopConnectionLabelAnimation(false);
        connectionLabel.post(new Runnable() {
            @Override
            public void run() {
                if (!isAdded() || connectionLabel == null) {
                    return;
                }

                final float textWidth = Math.max(connectionLabel.getWidth(),
                        connectionLabel.getPaint().measureText(connectionLabel.getText().toString()));
                if (textWidth <= 0f) {
                    return;
                }

                int[] colors = new int[] {
                        ContextCompat.getColor(requireContext(), R.color.progress_bar_color_2),
                        ContextCompat.getColor(requireContext(), R.color.text_primary),
                        ContextCompat.getColor(requireContext(), R.color.progress_bar_color_3),
                        ContextCompat.getColor(requireContext(), R.color.progress_bar_color_4),
                        ContextCompat.getColor(requireContext(), R.color.progress_bar_color_2)
                };
                float[] positions = new float[] {0f, 0.28f, 0.55f, 0.8f, 1f};
                connectionLabelGradient = new LinearGradient(
                        -textWidth, 0f, 0f, 0f, colors, positions, Shader.TileMode.CLAMP);
                connectionLabel.getPaint().setShader(connectionLabelGradient);

                ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
                animator.setDuration(STATUS_GRADIENT_DURATION_MS);
                animator.setRepeatCount(ValueAnimator.INFINITE);
                animator.setInterpolator(new LinearInterpolator());
                animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        if (connectionLabelGradient == null || connectionLabel == null) {
                            return;
                        }
                        float progress = (Float) animation.getAnimatedValue();
                        connectionLabelGradientMatrix.setTranslate(textWidth * 2f * progress, 0f);
                        connectionLabelGradient.setLocalMatrix(connectionLabelGradientMatrix);
                        connectionLabel.invalidate();
                    }
                });
                connectionLabelAnimator = animator;
                animator.start();
            }
        });
    }

    private void stopConnectionLabelAnimation(boolean clearShader) {
        if (connectionLabelAnimator != null) {
            connectionLabelAnimator.cancel();
            connectionLabelAnimator = null;
        }
        if (clearShader && connectionLabel != null) {
            connectionLabel.getPaint().setShader(null);
            connectionLabel.invalidate();
        }
        connectionLabelGradient = null;
    }

    private void registerTorStatusReceiver() {
        if (torStatusReceiver != null) {
            return;
        }

        torStatusReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                handler.sendEmptyMessage(WALLET_CHANGED);
            }
        };

        IntentFilter filter = new IntentFilter(Constants.ACTION_TOR_STATUS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(torStatusReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            requireContext().registerReceiver(torStatusReceiver, filter);
        }
    }

    private void unregisterTorStatusReceiver() {
        if (torStatusReceiver == null) {
            return;
        }

        requireContext().unregisterReceiver(torStatusReceiver);
        torStatusReceiver = null;
    }

    private final ThrottlingWalletChangeListener walletChangeListener = new ThrottlingWalletChangeListener() {

        @Override
        public void onThrottledWalletChanged() {
            if (adapter != null) adapter.notifyDataSetChanged();
            handler.sendMessage(handler.obtainMessage(WALLET_CHANGED));
        }
    };

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        try {
            listener = (Listener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.getClass() + " must implement " + Listener.class);
        }
        resolver = context.getContentResolver();
        application = (WalletApplication) context.getApplicationContext();
        config = application.getConfiguration();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        getLoaderManager().initLoader(ID_TRANSACTION_LOADER, null, this);
        getLoaderManager().initLoader(ID_RATE_LOADER, null, rateLoaderCallbacks);
    }

    @Override
    public void onDetach() {
        getLoaderManager().destroyLoader(ID_TRANSACTION_LOADER);
        getLoaderManager().destroyLoader(ID_RATE_LOADER);
        listener = null;
        resolver = null;
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();

        resolver.registerContentObserver(AddressBookProvider.contentUri(
                getActivity().getPackageName(), type), true, addressBookObserver);

        pocket.addEventListener(walletChangeListener, Threading.SAME_THREAD);
        registerTorStatusReceiver();

        checkEmptyPocketMessage();
        hydrateCachedRate();

        updateConnectivityStatus();
        updateView();
    }

    @Override
    public void onPause() {
        stopConnectionLabelAnimation(true);
        unregisterTorStatusReceiver();
        pocket.removeEventListener(walletChangeListener);
        walletChangeListener.removeCallbacks();

        resolver.unregisterContentObserver(addressBookObserver);

        super.onPause();
    }

    @Override
    public Loader<List<AbstractTransaction>> onCreateLoader(int id, Bundle args) {
        return new AbstractTransactionsLoader(getActivity(), pocket);
    }

    @Override
    public void onLoadFinished(Loader<List<AbstractTransaction>> loader, final List<AbstractTransaction> transactions) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (adapter != null) adapter.replace(transactions);
            }
        });
    }

    @Override
    public void onLoaderReset(Loader<List<AbstractTransaction>> loader) { /* ignore */ }

    @Override
    public WalletAccount getAccount() {
        return pocket;
    }

    private static class AbstractTransactionsLoader extends AsyncTaskLoader<List<AbstractTransaction>> {
        private final WalletAccount account;
        private final ThrottlingWalletChangeListener transactionAddRemoveListener;


        private AbstractTransactionsLoader(final Context context, @Nonnull final WalletAccount account) {
            super(context);

            this.account = account;
            this.transactionAddRemoveListener = new ThrottlingWalletChangeListener() {
                @Override
                public void onThrottledWalletChanged() {
                    try {
                        forceLoad();
                    } catch (final RejectedExecutionException x) {
                        log.info("rejected execution: " + AbstractTransactionsLoader.this);
                    }
                }
            };
        }

        @Override
        protected void onStartLoading() {
            super.onStartLoading();

            account.addEventListener(transactionAddRemoveListener, Threading.SAME_THREAD);
            transactionAddRemoveListener.onWalletChanged(null); // trigger at least one reload

            forceLoad();
        }

        @Override
        protected void onStopLoading() {
            account.removeEventListener(transactionAddRemoveListener);
            transactionAddRemoveListener.removeCallbacks();

            super.onStopLoading();
        }

        @Override
        public List<AbstractTransaction> loadInBackground() {
            final ArrayList filteredAbstractTransactions = Lists.newArrayList(account.getTransactions().values());

            Collections.sort(filteredAbstractTransactions, TRANSACTION_COMPARATOR);

            return filteredAbstractTransactions;
        }

        private static final Comparator<AbstractTransaction> TRANSACTION_COMPARATOR = new Comparator<AbstractTransaction>() {
            @Override
            public int compare(final AbstractTransaction tx1, final AbstractTransaction tx2) {
                final boolean pending1 = tx1.getConfidenceType() == TransactionConfidence.ConfidenceType.PENDING;
                final boolean pending2 = tx2.getConfidenceType() == TransactionConfidence.ConfidenceType.PENDING;

                if (pending1 != pending2)
                    return pending1 ? -1 : 1;

                // TODO use dates once implemented
//                final Date updateTime1 = tx1.getUpdateTime();
//                final long time1 = updateTime1 != null ? updateTime1.getTime() : 0;
//                final Date updateTime2 = tx2.getUpdateTime();
//                final long time2 = updateTime2 != null ? updateTime2.getTime() : 0;

                // If both not pending
                if (!pending1 && !pending2) {
                    final int time1 = tx1.getAppearedAtChainHeight();
                    final int time2 = tx2.getAppearedAtChainHeight();
                    if (time1 != time2)
                        return time1 > time2 ? -1 : 1;
                }

                return Arrays.equals(tx1.getHashBytes(),tx2.getHashBytes()) ? 1 : -1;
            }
        };
    }

    private final LoaderCallbacks<Value> rateLoaderCallbacks = new LoaderManager.LoaderCallbacks<Value>() {
        @Override
        public Loader<Value> onCreateLoader(final int id, final Bundle args) {
            return new BinanceRateLoader(getActivity());
        }

        @Override
        public void onLoadFinished(final Loader<Value> loader, final Value data) {
            if (data != null) {
                xvgUsdRate = data;
                handler.sendEmptyMessage(UPDATE_VIEW);
                if (log.isInfoEnabled()) {
                    try {
                        log.info("Got exchange rate: {}",
                                GenericUtils.formatFiatValue(data));
                    } catch (Exception e) {
                        log.warn(e.getMessage());
                    }
                }
            }
        }

        @Override
        public void onLoaderReset(final Loader<Value> loader) { }
    };

    @Override
    public void updateView() {
        if (isRemoving() || isDetached()) return;

        if (currentBalance != null) {
            String newBalanceStr = GenericUtils.formatCoinValue(type, currentBalance,
                    isFullAmount ? AMOUNT_FULL_PRECISION : AMOUNT_SHORT_PRECISION, AMOUNT_SHIFT);
            accountBalance.setText(newBalanceStr + " " + type.getSymbol());
        }

        if (currentBalance != null && xvgUsdRate != null && getView() != null) {
            try {
                Value fiatAmount = new ExchangeRateBase(type.oneCoin(), xvgUsdRate).convert(type, currentBalance);
                accountExchangedBalance.setText("$" + GenericUtils.formatFiatValue(fiatAmount) + " USD");
                accountExchangedBalance.setVisibility(View.VISIBLE);
            } catch (Exception e) {
                accountExchangedBalance.setText("");
                accountExchangedBalance.setVisibility(View.GONE);
            }
        } else {
            accountExchangedBalance.setText("");
            accountExchangedBalance.setVisibility(View.GONE);
        }

        swipeContainer.setRefreshing(pocket.isLoading());

        if (adapter != null) adapter.clearLabelCache();
    }

    private void clearLabelCache() {
        if (adapter != null) adapter.clearLabelCache();
    }

    private void hydrateCachedRate() {
        Value cachedRate = readCachedRate();
        if (cachedRate != null) {
            xvgUsdRate = cachedRate;
        }
    }

    private Value readCachedRate() {
        SharedPreferences prefs = getActivity().getSharedPreferences(PREF_BALANCE_CACHE, Context.MODE_PRIVATE);
        String cachedPrice = prefs.getString(PREF_XVG_USD_RATE, null);
        if (cachedPrice == null || cachedPrice.length() == 0) {
            cachedPrice = FALLBACK_XVG_USD_RATE;
        }

        try {
            return FiatValue.parse("USD", cachedPrice);
        } catch (Exception e) {
            log.warn("Could not read cached overview USD rate: {}", e.getMessage());
            return null;
        }
    }

    private static class MyHandler extends WeakHandler<BalanceFragment> {
        public MyHandler(BalanceFragment ref) { super(ref); }

        @Override
        protected void weakHandleMessage(BalanceFragment ref, Message msg) {
            switch (msg.what) {
                case WALLET_CHANGED:
                    ref.updateBalance();
                    ref.checkEmptyPocketMessage();
                    ref.updateConnectivityStatus();
                    break;
                case UPDATE_VIEW:
                    ref.updateView();
                    break;
                case CLEAR_LABEL_CACHE:
                    ref.clearLabelCache();
                    break;
            }
        }
    }

    static class AddressBookObserver extends ContentObserver {
        private final MyHandler handler;

        public AddressBookObserver(MyHandler handler) {
            super(handler);
            this.handler = handler;
        }

        @Override
        public void onChange(final boolean selfChange) {
            handler.sendEmptyMessage(CLEAR_LABEL_CACHE);
        }
    }

    private static class BinanceRateLoader extends AsyncTaskLoader<Value> {
        private BinanceRateLoader(final Context context) {
            super(context);
        }

        @Override
        protected void onStartLoading() {
            forceLoad();
        }

        private String parseUsdPrice(JSONObject json) {
            String price = json.optString("price", null);
            if (price != null && price.length() > 0) {
                return price;
            }

            double cryptoCompareUsd = json.optDouble("USD", -1d);
            if (cryptoCompareUsd >= 0d) {
                return String.valueOf(cryptoCompareUsd);
            }

            JSONObject verge = json.optJSONObject("verge");
            if (verge != null) {
                double usd = verge.optDouble("usd", -1d);
                if (usd >= 0d) {
                    return String.valueOf(usd);
                }
            }
            return null;
        }

        private Value getCachedRate() {
            SharedPreferences prefs = getContext().getSharedPreferences(PREF_BALANCE_CACHE, Context.MODE_PRIVATE);
            String cachedPrice = prefs.getString(PREF_XVG_USD_RATE, null);
            if (cachedPrice == null || cachedPrice.length() == 0) {
                cachedPrice = FALLBACK_XVG_USD_RATE;
            }
            try {
                return FiatValue.parse("USD", cachedPrice);
            } catch (Exception e) {
                try {
                    return FiatValue.parse("USD", FALLBACK_XVG_USD_RATE);
                } catch (Exception ignored) {
                    return null;
                }
            }
        }

        private void cacheRate(String price) {
            getContext().getSharedPreferences(PREF_BALANCE_CACHE, Context.MODE_PRIVATE)
                    .edit()
                    .putString(PREF_XVG_USD_RATE, price)
                    .apply();
        }

        @Override
        public Value loadInBackground() {
            String[] urls = new String[] {
                    COINGECKO_XVG_TICKER_URL,
                    CRYPTOCOMPARE_XVG_TICKER_URL
            };
            try {
                OkHttpClient client = NetworkUtils.getHttpClient(getContext().getApplicationContext());
                for (String url : urls) {
                    Request request = NetworkUtils.getBrowserRequestBuilder(url).build();
                    Response response = client.newCall(request).execute();
                    if (!response.isSuccessful()) {
                        log.warn("Overview rate request failed with HTTP {} for {}", response.code(), url);
                        continue;
                    }

                    JSONObject json = new JSONObject(response.body().string());
                    String price = parseUsdPrice(json);
                    if (price != null && price.length() > 0) {
                        cacheRate(price);
                        return FiatValue.parse("USD", price);
                    }
                }
            } catch (Exception e) {
                log.warn("Could not load XVG price: {}", e.getMessage());
            }
            return getCachedRate();
        }
    }

    public interface Listener {
        void onLocalAmountClick();
        void onRefresh();
    }
}
