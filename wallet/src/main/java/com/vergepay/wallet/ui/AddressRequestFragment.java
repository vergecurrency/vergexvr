package com.vergepay.wallet.ui;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Message;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.vergepay.core.coins.CoinType;
import com.vergepay.core.coins.Value;
import com.vergepay.core.coins.families.BitFamily;
import com.vergepay.core.coins.families.NxtFamily;
import com.vergepay.core.exceptions.UnsupportedCoinTypeException;
import com.vergepay.core.uri.CoinURI;
import com.vergepay.core.util.GenericUtils;
import com.vergepay.core.wallet.AbstractAddress;
import com.vergepay.core.wallet.WalletAccount;
import com.vergepay.wallet.AddressBookProvider;
import com.vergepay.wallet.Constants;
import com.vergepay.wallet.R;
import com.vergepay.wallet.WalletApplication;
import com.vergepay.wallet.ui.dialogs.CreateNewAddressDialog;
import com.vergepay.wallet.ui.widget.AmountEditView;
import com.vergepay.wallet.util.QrUtils;
import com.vergepay.wallet.util.ThrottlingWalletChangeListener;
import com.vergepay.wallet.util.UiUtils;
import com.vergepay.wallet.util.WeakHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static com.vergepay.core.Preconditions.checkNotNull;

/**
 *
 */
public class AddressRequestFragment extends WalletFragment {
    private static final Logger log = LoggerFactory.getLogger(AddressRequestFragment.class);

    private static final int UPDATE_VIEW = 0;

    // Fragment tags
    private static final String NEW_ADDRESS_TAG = "new_address_tag";

    private CoinType type;
    @Nullable private AbstractAddress showAddress;
    private AbstractAddress receiveAddress;
    private Value amount;
    private String label;
    private String accountId;
    private WalletAccount account;
    private String message;

    private TextView addressLabelView;
    private TextView addressView;
    private View addressCopyView;
    private AmountEditView sendCoinAmountView;
    private ScrollView requestScrollView;
    private View requestAmountRow;
    private View previousAddressesLink;
    private ImageView qrView;
    String lastQrContent;
    ContentResolver resolver;

    private final MyHandler handler = new MyHandler(this);
    private final ContentObserver addressBookObserver = new AddressBookObserver(handler);

    private static class MyHandler extends WeakHandler<AddressRequestFragment> {
        public MyHandler(AddressRequestFragment ref) { super(ref); }

        @Override
        protected void weakHandleMessage(AddressRequestFragment ref, Message msg) {
            switch (msg.what) {
                case UPDATE_VIEW:
                    ref.updateView();
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
            handler.sendEmptyMessage(UPDATE_VIEW);
        }
    }

    public static AddressRequestFragment newInstance(Bundle args) {
        AddressRequestFragment fragment = new AddressRequestFragment();
        fragment.setArguments(args);
        return fragment;
    }

    public static AddressRequestFragment newInstance(String accountId) {
        return newInstance(accountId, null);
    }

    public static AddressRequestFragment newInstance(String accountId,
                                                     @Nullable AbstractAddress showAddress) {
        Bundle args = new Bundle();
        args.putString(Constants.ARG_ACCOUNT_ID, accountId);
        if (showAddress != null) {
            args.putSerializable(Constants.ARG_ADDRESS, showAddress);
        }
        return newInstance(args);
    }
    public AddressRequestFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // The onCreateOptionsMenu is handled in com.vergepay.wallet.ui.AccountFragment
        // or in com.vergepay.wallet.ui.PreviousAddressesActivity
        setHasOptionsMenu(true);

        WalletApplication walletApplication = (WalletApplication) getActivity().getApplication();
        Bundle args = getArguments();
        if (args != null) {
            accountId = args.getString(Constants.ARG_ACCOUNT_ID);
            if (args.containsKey(Constants.ARG_ADDRESS)) {
                showAddress = (AbstractAddress) args.getSerializable(Constants.ARG_ADDRESS);
            }
        }
        // TODO
        account = checkNotNull(walletApplication.getAccount(accountId));
        if (account == null) {
            Toast.makeText(getActivity(), R.string.no_such_pocket_error, Toast.LENGTH_LONG).show();
            return;
        }
        type = account.getCoinType();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_request, container, false);
        addressLabelView = view.findViewById(R.id.request_address_label);
        addressView = view.findViewById(R.id.request_address);
        addressCopyView = view.findViewById(R.id.request_address_copy);
        sendCoinAmountView = view.findViewById(R.id.request_coin_amount);
        requestScrollView = view.findViewById(R.id.request_scroll);
        requestAmountRow = view.findViewById(R.id.request_amount_row);
        previousAddressesLink = view.findViewById(R.id.view_previous_addresses);
        qrView = view.findViewById(R.id.qr_code);

        sendCoinAmountView.resetType(type, true);
        sendCoinAmountView.setListener(amountsListener);
        addressView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onAddressClick();
            }
        });
        addressCopyView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onAddressCopyClick();
            }
        });
        previousAddressesLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onPreviousAddressesClick();
            }
        });

        return view;
    }

    private void scrollAmountRowIntoView() {
        if (requestScrollView == null || requestAmountRow == null) return;
        requestScrollView.post(new Runnable() {
            @Override
            public void run() {
                requestScrollView.smoothScrollTo(0, Math.max(0, requestAmountRow.getTop() - 24));
            }
        });
    }

    private void setEditingState(boolean editing) {
        if (editing) {
            scrollAmountRowIntoView();
        }
    }

    public void onAddressClick() {
        copyCurrentAddress();
    }

    public void onAddressCopyClick() {
        copyCurrentAddress();
    }

    public void onPreviousAddressesClick() {
        Intent intent = new Intent(getActivity(), PreviousAddressesActivity.class);
        intent.putExtra(Constants.ARG_ACCOUNT_ID, accountId);
        startActivity(intent);
    }

    @Override
    public void onResume() {
        super.onResume();

        account.addEventListener(walletListener);
        resolver.registerContentObserver(AddressBookProvider.contentUri(
                getActivity().getPackageName(), type), true, addressBookObserver);

        updateView();
    }

    @Override
    public void onPause() {
        resolver.unregisterContentObserver(addressBookObserver);
        account.removeEventListener(walletListener);
        walletListener.removeCallbacks();

        super.onPause();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_share:
                UiUtils.share(getActivity(), getUri());
                return true;
            case R.id.action_copy:
                UiUtils.copy(getActivity(), getUri());
                return true;
            case R.id.action_new_address:
                showNewAddressDialog();
                return true;
            case R.id.action_edit_label:
                EditAddressBookEntryFragment.edit(getFragmentManager(), type, receiveAddress);
                return true;
            default:
                // Not one of ours. Perform default menu processing
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onAttach(final Context  context) {
        super.onAttach(context);
        this.resolver = context.getContentResolver();
    }

    @Override
    public void onDetach() {
        resolver = null;
        super.onDetach();
    }

    private void showNewAddressDialog() {
        if (!isVisible() || !isResumed()) return;
        Dialogs.dismissAllowingStateLoss(getFragmentManager(), NEW_ADDRESS_TAG);
        DialogFragment dialog = CreateNewAddressDialog.getInstance(account);
        dialog.show(getFragmentManager(), NEW_ADDRESS_TAG);
    }

    @Override
    public void updateView() {
        if (isRemoving() || isDetached()) return;
        receiveAddress = null;
        if (showAddress != null) {
            receiveAddress =  showAddress;
        } else {
            receiveAddress = account.getReceiveAddress();
        }

        // Don't show previous addresses link if we are showing a specific address
        if (showAddress == null && account.hasUsedAddresses()) {
            previousAddressesLink.setVisibility(View.VISIBLE);
        } else {
            previousAddressesLink.setVisibility(View.GONE);
        }

        // TODO, add message

        updateLabel();

        updateQrCode(getQrContent());
    }

    private String getUri() {
        if (type instanceof BitFamily) {
            return CoinURI.convertToCoinURI(receiveAddress, amount, label, message);
        } else if (type instanceof NxtFamily){
            return CoinURI.convertToCoinURI(receiveAddress, amount, label, message,
                    account.getPublicKeySerialized());
        } else {
            throw new UnsupportedCoinTypeException(type);
        }
    }

    private String getQrContent() {
        return receiveAddress != null ? receiveAddress.toString() : "";
    }

    /**
     * Update qr code if the content is different
     */
    private void updateQrCode(final String qrContent) {
        if (lastQrContent == null || !lastQrContent.equals(qrContent)) {
            QrUtils.setQr(qrView, getResources(), qrContent, R.dimen.receive_qr_code_size);
            lastQrContent = qrContent;
        }
    }

    private void updateLabel() {
        label = resolveLabel(receiveAddress);
        if (label != null) {
            addressLabelView.setText(label);
            addressLabelView.setTypeface(Typeface.DEFAULT);
            addressView.setText(receiveAddress.toString());
            addressView.setVisibility(View.VISIBLE);
            addressCopyView.setVisibility(View.VISIBLE);
        } else {
            addressLabelView.setText(getString(R.string.receive_address_label));
            addressLabelView.setTypeface(Typeface.MONOSPACE);
            addressView.setText(receiveAddress.toString());
            addressView.setVisibility(View.VISIBLE);
            addressCopyView.setVisibility(View.VISIBLE);
        }
    }

    private void copyCurrentAddress() {
        if (receiveAddress != null && getActivity() != null) {
            UiUtils.copy(getActivity(), receiveAddress.toString());
        }
    }

    private final ThrottlingWalletChangeListener walletListener = new ThrottlingWalletChangeListener() {
        @Override
        public void onThrottledWalletChanged() {
            handler.sendEmptyMessage(UPDATE_VIEW);
        }
    };

    private String resolveLabel(@Nonnull final AbstractAddress address) {
        return AddressBookProvider.resolveLabel(getActivity(), address);
    }

    @Override
    public WalletAccount getAccount() {
        return account;
    }

    private final AmountEditView.Listener amountsListener = new AmountEditView.Listener() {
        boolean isValid(Value amount) {
            return amount != null && amount.isPositive();
        }

        void checkAndUpdateAmount() {
            Value amountParsed = sendCoinAmountView.getAmount();
            if (isValid(amountParsed)) {
                amount = amountParsed;
            } else {
                amount = null;
            }
            updateView();
        }

        @Override
        public void changed() {
            checkAndUpdateAmount();
        }

        @Override
        public void focusChanged(final boolean hasFocus) {
            if (hasFocus) {
                setEditingState(true);
                scrollAmountRowIntoView();
            } else {
                checkAndUpdateAmount();
                setEditingState(false);
            }
        }
    };
}
