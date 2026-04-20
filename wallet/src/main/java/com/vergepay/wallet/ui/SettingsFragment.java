package com.vergepay.wallet.ui;

import android.os.Bundle;

import androidx.preference.PreferenceFragmentCompat;

import com.vergepay.wallet.R;

/**
 * @author John L. Jegutanis
 */
public class SettingsFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.preferences);
    }
}
