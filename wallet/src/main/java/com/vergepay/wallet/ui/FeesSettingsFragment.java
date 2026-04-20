package com.vergepay.wallet.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import com.vergepay.core.coins.Value;
import com.vergepay.wallet.Configuration;
import com.vergepay.wallet.R;
import com.vergepay.wallet.WalletApplication;
import com.vergepay.wallet.ui.adaptors.FeesListAdapter;
import com.vergepay.wallet.ui.dialogs.EditFeeDialog;

/**
 * Fragment that restores a wallet
 */
public class FeesSettingsFragment extends Fragment implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String EDIT_FEE_DIALOG = "edit_fee_dialog";

    private ListView coinList;
    private Configuration config;
    private Context context;
    private FeesListAdapter adapter;

    public FeesSettingsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        adapter = new FeesListAdapter(context, config);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_fees_settings_list, container, false);
        coinList = view.findViewById(R.id.coins_list);
        coinList.setAdapter(adapter);
        coinList.setOnItemClickListener((parent, itemView, position, id) -> editFee(position));

        return view;
    }

    private void editFee(int currentSelection) {
        Value fee = (Value) coinList.getItemAtPosition(currentSelection);
        // Create the fragment and show it as a dialog.
        DialogFragment editFeeDialog = EditFeeDialog.newInstance(fee.type);
        editFeeDialog.show(getFragmentManager(), EDIT_FEE_DIALOG);
    }

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        this.context = context;
        WalletApplication application = (WalletApplication) context.getApplicationContext();
        config = application.getConfiguration();
        config.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        config.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (Configuration.PREFS_KEY_FEES.equals(key)) {
            adapter.update();
        }
    }
}
