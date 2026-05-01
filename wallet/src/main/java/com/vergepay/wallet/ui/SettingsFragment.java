package com.vergepay.wallet.ui;

import android.os.Bundle;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;

import com.vergepay.wallet.R;

/**
 * @author John L. Jegutanis
 */
public class SettingsFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.preferences);

        Preference connectionsPreference = findPreference("connections");
        if (connectionsPreference != null) {
            connectionsPreference.setSummary(R.string.pref_summary_connections);
        }

        PreferenceCategory metaCategory = findPreference("meta_preferences_category");
        if (!getResources().getBoolean(R.bool.wallet_meta_variant)) {
            if (metaCategory != null) {
                getPreferenceScreen().removePreference(metaCategory);
            }
            return;
        }

        ListPreference startupPreference = findPreference("meta_startup_target");
        if (startupPreference != null) {
            startupPreference.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        }
    }
}
