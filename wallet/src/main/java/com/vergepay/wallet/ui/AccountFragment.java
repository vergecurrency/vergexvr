package com.vergepay.wallet.ui;

import android.content.Context;
import android.os.Message;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.view.ActionMode;
import android.view.DisplayCutout;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.view.ContextThemeWrapper;
import android.view.WindowInsets;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.vergepay.core.uri.CoinURI;
import com.vergepay.core.uri.CoinURIParseException;
import com.vergepay.core.wallet.WalletAccount;
import com.vergepay.wallet.Constants;
import com.vergepay.wallet.R;
import com.vergepay.wallet.WalletApplication;
import com.vergepay.wallet.util.Keyboard;
import com.vergepay.wallet.util.WeakHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import butterknife.BindView;
import butterknife.ButterKnife;

import static com.vergepay.wallet.util.UiUtils.toastGenericError;

/**
 * @author John L. Jegutanis
 */
public class AccountFragment extends Fragment {
    private static final Logger log = LoggerFactory.getLogger(AccountFragment.class);

    private static final String ACCOUNT_CURRENT_SCREEN = "account_current_screen";
    private static final int NUM_OF_SCREENS = 4;
    // Set offscreen page limit to 2 because receive fragment draws a QR code and we don't
    // want to re-render that if we go to the SendFragment and back
    private static final int OFF_SCREEN_LIMIT = 3;

    // Screen ids
    private static final int RECEIVE = 0;
    private static final int BALANCE = 1;
    private static final int SEND = 2;
    private static final int SWAP = 3;

    // Handler ids
    private static final int SEND_TO_URI = 0;

    private int currentScreen;
    @BindView(R.id.pager) ViewPager viewPager;
    @BindView(R.id.nav_receive) TextView receiveNav;
    @BindView(R.id.nav_balance) TextView balanceNav;
    @BindView(R.id.nav_send) TextView sendNav;
    @BindView(R.id.nav_swap) TextView swapNav;
    @BindView(R.id.nav_overflow) ImageButton overflowNav;
    @BindView(R.id.account_nav_container) View accountNavContainer;
    @BindView(R.id.account_root) View accountRoot;
    NavigationDrawerFragment mNavigationDrawerFragment;
    @Nullable private WalletAccount account;
    private Listener listener;
    private WalletApplication application;
    private final MyHandler handler = new MyHandler(this);

    public static AccountFragment getInstance() {
        AccountFragment fragment = new AccountFragment();
        fragment.setArguments(new Bundle());
        return fragment;
    }

    public static AccountFragment getInstance(String accountId) {
        AccountFragment fragment = getInstance();
        fragment.setupArgs(accountId);
        return fragment;
    }

    private void setupArgs(String accountId) {
        getArguments().putString(Constants.ARG_ACCOUNT_ID, accountId);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        // TODO handle null account
        account = application.getAccount(getArguments().getString(Constants.ARG_ACCOUNT_ID));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_account, container, false);
        ButterKnife.bind(this, view);

        mNavigationDrawerFragment = (NavigationDrawerFragment)
                getFragmentManager().findFragmentById(R.id.navigation_drawer);

        receiveNav.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { goToReceive(true); }
        });
        balanceNav.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { goToBalance(true); }
        });
        sendNav.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { goToSend(true); }
        });
        swapNav.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { goToSwap(true); }
        });
        overflowNav.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showOverflowMenu(v); }
        });

        viewPager.setOffscreenPageLimit(OFF_SCREEN_LIMIT);
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                currentScreen = position;
                updateNavigationSelection();
                if (position == BALANCE) Keyboard.hideKeyboard(getActivity());
                if (listener != null) {
                    switch (position) {
                        case RECEIVE:
                            listener.onReceiveSelected();
                            break;
                        case BALANCE:
                            listener.onBalanceSelected();
                            break;
                        case SEND:
                            listener.onSendSelected();
                            break;
                        case SWAP:
                            listener.onSwapSelected();
                            break;
                        default:
                            throw new RuntimeException("Unknown screen item: " + position);
                    }
                }
            }

            @Override public void onPageScrolled(int pos, float posOffset, int posOffsetPixels) { }
            @Override public void onPageScrollStateChanged(int state) { }
        });

        viewPager.setAdapter(
                new AppSectionsPagerAdapter(getActivity(), getChildFragmentManager(), account));

        WindowInsetsHelper.applyPaddingInsets(view, false, false);
        if (isPixelLikeDevice()) {
            accountRoot.post(new Runnable() {
                @Override
                public void run() {
                    applyPixelSafeTopMargin();
                }
            });
        } else {
            WindowInsetsHelper.applyTopInsetAsPadding(accountNavContainer, 0);
        }

        return view;
    }

    private boolean isPixelLikeDevice() {
        String manufacturer = Build.MANUFACTURER != null ? Build.MANUFACTURER.toLowerCase() : "";
        String model = Build.MODEL != null ? Build.MODEL.toLowerCase() : "";
        String fingerprint = Build.FINGERPRINT != null ? Build.FINGERPRINT.toLowerCase() : "";

        return manufacturer.contains("google")
                || model.contains("sdk_gphone")
                || fingerprint.contains("generic");
    }

    private void applyPixelSafeTopMargin() {
        if (accountRoot == null || accountNavContainer == null) return;
        if (!(accountNavContainer.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) return;

        int resolvedTopInset = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            WindowInsets rootInsets = accountRoot.getRootWindowInsets();
            if (rootInsets != null) {
                resolvedTopInset = rootInsets.getSystemWindowInsetTop();

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    DisplayCutout displayCutout = rootInsets.getDisplayCutout();
                    if (displayCutout != null) {
                        resolvedTopInset = Math.max(resolvedTopInset, displayCutout.getSafeInsetTop());
                    }
                }
            }
        }

        ViewGroup.MarginLayoutParams layoutParams =
                (ViewGroup.MarginLayoutParams) accountNavContainer.getLayoutParams();
        layoutParams.topMargin = getResources().getDimensionPixelSize(R.dimen.account_nav_default_top_margin)
                + resolvedTopInset;
        accountNavContainer.setLayoutParams(layoutParams);
        accountNavContainer.requestLayout();
    }

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        try {
            this.listener = (Listener) context;
            this.application = (WalletApplication) context.getApplicationContext();
        } catch (ClassCastException e) {
            throw new ClassCastException(context + " must implement " + Listener.class);
        }
    }

    @Override
    public void onDetach() {
        listener = null;
        super.onDetach();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(ACCOUNT_CURRENT_SCREEN, currentScreen);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            currentScreen = savedInstanceState.getInt(ACCOUNT_CURRENT_SCREEN, BALANCE);
        } else {
            currentScreen = BALANCE;
        }
        updateView();
        super.onViewStateRestored(savedInstanceState);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (mNavigationDrawerFragment != null && !mNavigationDrawerFragment.isDrawerOpen() &&
                isVisible() && account != null) {

            switch (viewPager.getCurrentItem()) {
                case RECEIVE:
                    inflater.inflate(R.menu.request, menu);
                    MenuItem newAddressItem = menu.findItem(R.id.action_new_address);
                    if (newAddressItem != null) {
                        newAddressItem.setVisible(account.canCreateNewAddresses());
                    }
                    break;
                case BALANCE:
                    inflater.inflate(R.menu.balance, menu);
                    // Disable sign/verify for coins that don't support it
                    menu.findItem(R.id.action_sign_verify_message)
                            .setVisible(account.getCoinType().canSignVerifyMessages());
                    break;
                case SEND:
                    inflater.inflate(R.menu.send, menu);
                    break;
                case SWAP:
                    break;
            }
        }
    }

    private void updateView() {
        goToItem(currentScreen, true);
        updateNavigationSelection();
    }

    @Nullable
    public WalletAccount getAccount() {
        return account;
    }

    public void sendToUri(final CoinURI coinUri) {
        if (viewPager != null) {
            viewPager.setCurrentItem(SEND);
            handler.sendMessage(handler.obtainMessage(SEND_TO_URI, coinUri));
        } else {
            // Should not happen
            toastGenericError(getContext());
        }
    }

    private void setSendToUri(CoinURI coinURI) {
        if (viewPager != null) viewPager.setCurrentItem(SEND);
        SendFragment f = getSendFragment();
        if (f != null) {
            try {
                f.updateStateFrom(coinURI);
            } catch (CoinURIParseException e) {
                Toast.makeText(getContext(),
                        getString(R.string.scan_error, e.getMessage()),
                        Toast.LENGTH_LONG).show();
            }
        } else {
            log.warn("Expected fragment to be not null");
            toastGenericError(getContext());
        }
    }

    @Nullable
    private SendFragment getSendFragment() {
        return (SendFragment) getFragment(getChildFragmentManager(), SEND);
    }

    @Nullable
    private static Fragment getFragment(FragmentManager fm, int item) {
        if (fm.getFragments() == null) return null;

        for (Fragment f : fm.getFragments()) {
            switch (item) {
                case RECEIVE:
                    if (f instanceof AddressRequestFragment) return f;
                    break;
                case BALANCE:
                    if (f instanceof BalanceFragment) return f;
                    break;
                case SEND:
                    if (f instanceof SendFragment) return f;
                    break;
                case SWAP:
                    if (f instanceof SwapWidgetFragment) return f;
                    break;
                default:
                    throw new RuntimeException("Cannot get fragment, unknown screen item: " + item);
            }
        }
        return null;
    }

    @SuppressWarnings({ "unchecked"})
    private static <T extends Fragment> T createFragment(WalletAccount account, int item) {
        String accountId = account.getId();
        switch (item) {
            case RECEIVE:
                return (T) AddressRequestFragment.newInstance(accountId);
            case BALANCE:
                return (T) BalanceFragment.newInstance(accountId);
            case SEND:
                return (T) SendFragment.newInstance(accountId);
            case SWAP:
                return (T) SwapWidgetFragment.newInstance();
            default:
                throw new RuntimeException("Cannot create fragment, unknown screen item: " + item);
        }
    }

    public boolean goToReceive(boolean smoothScroll) {
        return goToItem(RECEIVE, smoothScroll);
    }

    public boolean goToBalance(boolean smoothScroll) {
        return goToItem(BALANCE, smoothScroll);
    }

    public boolean goToSend(boolean smoothScroll) {
        return goToItem(SEND, smoothScroll);
    }

    public boolean goToSwap(boolean smoothScroll) {
        return goToItem(SWAP, smoothScroll);
    }

    private boolean goToItem(int item, boolean smoothScroll) {
        currentScreen = item;
        updateNavigationSelection();
        if (viewPager != null && viewPager.getCurrentItem() != item) {
            viewPager.setCurrentItem(item, smoothScroll);
            return true;
        }
        return false;
    }

    private void updateNavigationSelection() {
        if (receiveNav == null || balanceNav == null || sendNav == null || swapNav == null) return;

        setNavSelected(receiveNav, currentScreen == RECEIVE);
        setNavSelected(balanceNav, currentScreen == BALANCE);
        setNavSelected(sendNav, currentScreen == SEND);
        setNavSelected(swapNav, currentScreen == SWAP);
    }

    private void setNavSelected(TextView view, boolean selected) {
        view.setBackgroundResource(selected
                ? R.drawable.account_nav_item_selected_bg
                : R.drawable.account_nav_item_default_bg);
        view.setTextColor(getResources().getColor(selected ? R.color.text_primary : R.color.text_secondary));
    }

    private void showOverflowMenu(View anchor) {
        if (getContext() == null) return;

        ContextThemeWrapper popupContext = new ContextThemeWrapper(getContext(), R.style.RetroPopupMenu);
        PopupMenu popup = new PopupMenu(popupContext, anchor, Gravity.END, 0, R.style.RetroPopupMenu);
        popup.getMenuInflater().inflate(R.menu.global, popup.getMenu());
        switch (currentScreen) {
            case RECEIVE:
                popup.getMenuInflater().inflate(R.menu.request, popup.getMenu());
                break;
            case BALANCE:
                popup.getMenuInflater().inflate(R.menu.balance, popup.getMenu());
                popup.getMenu().removeItem(R.id.action_scan_qr_code);
                break;
            case SEND:
                popup.getMenuInflater().inflate(R.menu.send, popup.getMenu());
                break;
            case SWAP:
                break;
            default:
                break;
        }

        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (listener != null && listener.onAccountMenuItemSelected(item.getItemId())) {
                    return true;
                }
                Fragment activeFragment = getFragment(getChildFragmentManager(), currentScreen);
                return activeFragment != null && activeFragment.onOptionsItemSelected(item);
            }
        });
        popup.show();
    }

    public boolean resetSend() {
        SendFragment f = getSendFragment();
        if (f != null) {
            f.reset();
            return true;
        }
        return false;
    }

    private static class AppSectionsPagerAdapter extends FragmentPagerAdapter {
        private final String receiveTitle;
        private final String sendTitle;
        private final String balanceTitle;
        private final String swapTitle;

        private AddressRequestFragment request;
        private SendFragment send;
        private BalanceFragment balance;
        private SwapWidgetFragment swap;

        private final WalletAccount account;

        public AppSectionsPagerAdapter(Context context, FragmentManager fm, WalletAccount account) {
            super(fm);
            receiveTitle = context.getString(R.string.wallet_title_request);
            sendTitle = context.getString(R.string.wallet_title_send);
            balanceTitle = context.getString(R.string.wallet_title_balance);
            swapTitle = context.getString(R.string.wallet_title_swap);
            this.account = account;
        }

        @Override
        public Fragment getItem(int i) {
            switch (i) {
                case RECEIVE:
                    if (request == null) request = createFragment(account, i);
                    return request;
                case SEND:
                    if (send == null) send = createFragment(account, i);
                    return send;
                case BALANCE:
                    if (balance == null) balance = createFragment(account, i);
                    return balance;
                case SWAP:
                    if (swap == null) swap = createFragment(account, i);
                    return swap;
                default:
                    throw new RuntimeException("Cannot get item, unknown screen item: " + i);
            }
        }


        @Override
        public int getCount() {
            return NUM_OF_SCREENS;
        }

        @Override
        public CharSequence getPageTitle(int i) {
            switch (i) {
                case RECEIVE: return receiveTitle;
                case SEND: return sendTitle;
                case BALANCE: return balanceTitle;
                case SWAP: return swapTitle;
                default: throw new RuntimeException("Cannot get item, unknown screen item: " + i);
            }
        }
    }

    private static class MyHandler extends WeakHandler<AccountFragment> {
        public MyHandler(AccountFragment ref) { super(ref); }

        @Override
        protected void weakHandleMessage(AccountFragment ref, Message msg) {
            if (msg.what == SEND_TO_URI) {
                ref.setSendToUri((CoinURI) msg.obj);
            }
        }
    }

    public interface Listener extends BalanceFragment.Listener, SendFragment.Listener {
        // TODO make an external interface so that SendFragment and AddressRequestFragment can use.
        void registerActionMode(ActionMode actionMode);
        void onReceiveSelected();
        void onBalanceSelected();
        void onSendSelected();
        void onSwapSelected();
        boolean onAccountMenuItemSelected(int itemId);
    }
}
