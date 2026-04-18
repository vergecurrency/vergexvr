package com.vergepay.wallet.ui.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import com.vergepay.core.coins.CoinID;
import com.vergepay.core.coins.CoinType;
import com.vergepay.core.coins.Value;
import com.vergepay.core.coins.ValueType;
import com.vergepay.wallet.Configuration;
import com.vergepay.wallet.Constants;
import com.vergepay.wallet.R;
import com.vergepay.wallet.WalletApplication;
import com.vergepay.wallet.ui.widget.AmountEditView;

import butterknife.BindView;
import butterknife.ButterKnife;

import static com.vergepay.core.Preconditions.checkState;

/**
 * @author John L. Jegutanis
 */
public class EditFeeDialog extends DialogFragment {
    @BindView(R.id.fee_title)
    TextView title;
    @BindView(R.id.fee_description)
    TextView description;
    @BindView(R.id.fee_amount)
    AmountEditView feeAmount;
    @BindView(R.id.fee_cancel)
    Button cancelButton;
    @BindView(R.id.fee_default)
    Button defaultButton;
    @BindView(R.id.fee_ok)
    Button okButton;
    Configuration configuration;
    Resources resources;

    public static EditFeeDialog newInstance(ValueType type) {
        EditFeeDialog dialog = new EditFeeDialog();
        dialog.setArguments(new Bundle());
        dialog.getArguments().putString(Constants.ARG_COIN_ID, type.getId());
        return dialog;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        WalletApplication application = (WalletApplication) activity.getApplication();
        configuration = application.getConfiguration();
        resources = application.getResources();
    }

    public Dialog onCreateDialog(Bundle savedInstanceState) {
        checkState(getArguments().containsKey(Constants.ARG_COIN_ID), "Must provide coin id");
        View view = View.inflate(getActivity(), R.layout.dialog_edit_fee_themed, null);
        ButterKnife.bind(this, view);

        // TODO move to xml
        feeAmount.setSingleLine(true);

        final CoinType type = CoinID.typeFromId(getArguments().getString(Constants.ARG_COIN_ID));
        feeAmount.resetType(type);

        String feePolicy;
        switch (type.getFeePolicy()) {
            case FEE_PER_KB:
                feePolicy = resources.getString(R.string.tx_fees_per_kilobyte);
                break;
            case FLAT_FEE:
                feePolicy = resources.getString(R.string.tx_fees_per_transaction);
                break;
            default:
                throw new RuntimeException("Unknown fee policy " + type.getFeePolicy());
        }
        title.setText(resources.getString(R.string.tx_fees_title, type.getName()));
        description.setText(resources.getString(R.string.tx_fees_description, feePolicy));

        final Value fee = configuration.getFeeValue(type);
        feeAmount.setAmount(fee, false);

        final AlertDialog dialog = new AlertDialog.Builder(getActivity()).setView(view).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        defaultButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                configuration.resetFeeValue(type);
                dismiss();
            }
        });
        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Value newFee = feeAmount.getAmount();
                if (newFee != null && !newFee.equals(fee)) {
                    configuration.setFeeValue(newFee);
                }
                dismiss();
            }
        });

        return dialog;
    }
}
