package com.vergepay.wallet.ui;

import android.os.Bundle;

import com.vergepay.wallet.R;

public class FeesSettingsActivity extends BaseWalletActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fragment_wrapper);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, new FeesSettingsFragment())
                    .commit();
        }
        setupWrapperHeader();
    }
}
