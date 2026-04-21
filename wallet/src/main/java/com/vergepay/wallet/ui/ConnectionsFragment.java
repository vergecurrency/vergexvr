package com.vergepay.wallet.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;

import com.vergepay.core.wallet.WalletAccount;
import com.vergepay.core.wallet.WalletConnectivityStatus;
import com.vergepay.wallet.Configuration;
import com.vergepay.wallet.R;
import com.vergepay.wallet.WalletApplication;
import com.vergepay.wallet.service.CoinService;
import com.vergepay.wallet.service.CoinServiceImpl;
import com.vergepay.wallet.ui.summary.WalletSummaryData;
import com.vergepay.wallet.util.ThrottlingWalletChangeListener;
import com.vergepay.stratumj.ServerAddress;

import org.bitcoinj.utils.Threading;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import android.graphics.Typeface;

public class ConnectionsFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private Preference savedConnectionsInfoPreference;
    private PreferenceCategory savedConnectionsCategory;
    private WalletAccount statusAccount;
    private final ThrottlingWalletChangeListener connectionStatusListener =
            new ThrottlingWalletChangeListener(250, false, false, false, true) {
                @Override
                public void onThrottledWalletChanged() {
                    syncConnectionList();
                    updateConnectionStatusHeader();
                }
            };

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.preferences_connections);

        savedConnectionsInfoPreference = findPreference("saved_connections_info");
        savedConnectionsCategory = findPreference("saved_connections_category");
        styleConnectionHeaders();
        syncConnectionList();

        Preference addCustomConnectionPreference = findPreference("add_custom_connection");
        if (addCustomConnectionPreference != null) {
            addCustomConnectionPreference.setTitle(R.string.pref_title_add_custom_connection_button);
            addCustomConnectionPreference.setSummary(null);
            addCustomConnectionPreference.setLayoutResource(R.layout.preference_action_button);
            addCustomConnectionPreference.setIconSpaceReserved(false);
            addCustomConnectionPreference.setOnPreferenceClickListener(preference -> {
                showAddCustomConnectionDialog();
                return true;
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceManager().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
        bindStatusAccount();
        syncConnectionList();
        updateConnectionStatusHeader();
    }

    @Override
    public void onPause() {
        getPreferenceManager().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
        unbindStatusAccount();
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (Configuration.PREFS_KEY_VERGE_CUSTOM_CONNECTIONS.equals(key)) {
            syncConnectionList();
            updateConnectionStatusHeader();
            reloadConnections();
            return;
        }

        if (Configuration.PREFS_KEY_VERGE_CONNECTION_PROFILE.equals(key)) {
            syncConnectionList();
            updateConnectionStatusHeader();
        }
    }

    private void syncConnectionList() {
        if (savedConnectionsCategory == null || !isAdded()) {
            return;
        }

        savedConnectionsCategory.removeAll();
        String connectedEndpoint = getConnectedEndpoint();
        boolean activelyConnected = isActivelyConnected();

        addConnectionPreference(getString(R.string.pref_connection_option_legacy_onion),
                getProtocolLabel(ServerAddress.Protocol.LEGACY_ELECTRUM),
                connectedEndpoint, activelyConnected);
        addConnectionPreference(getString(R.string.pref_connection_option_electrum_cloud),
                getProtocolLabel(ServerAddress.Protocol.ELECTRUMX),
                connectedEndpoint, activelyConnected);
        addConnectionPreference(getString(R.string.pref_connection_option_electrumx_cloud),
                getProtocolLabel(ServerAddress.Protocol.ELECTRUMX),
                connectedEndpoint, activelyConnected);

        List<String> customIds = new ArrayList<>(getConfiguration().getVergeCustomConnectionIds());
        Collections.sort(customIds);
        for (String customId : customIds) {
            String[] customConnection = Configuration.parseCustomVergeConnectionId(customId);
            if (customConnection == null) {
                continue;
            }

            String endpoint = formatEndpoint(customConnection[0], customConnection[1]);
            if (isBuiltInEndpoint(endpoint)) {
                continue;
            }

            addConnectionPreference(endpoint,
                    getProtocolLabel(Configuration.parseCustomVergeConnectionProtocol(customConnection[2])),
                    connectedEndpoint, activelyConnected);
        }
    }

    private void addConnectionPreference(CharSequence endpoint, CharSequence protocolLabel,
                                         String connectedEndpoint,
                                         boolean activelyConnected) {
        Preference preference = new Preference(requireContext());
        preference.setTitle(styleServerEndpoint(endpoint));
        preference.setPersistent(false);
        preference.setSelectable(false);
        preference.setIconSpaceReserved(false);

        String endpointText = endpoint.toString();
        if (activelyConnected && endpointText.equalsIgnoreCase(connectedEndpoint)) {
            preference.setSummary(protocolLabel + " - " + getString(R.string.pref_connection_connected));
        } else {
            preference.setSummary(protocolLabel);
        }
        savedConnectionsCategory.addPreference(preference);
    }

    private void showAddCustomConnectionDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_connection, null, false);
        EditText hostInput = dialogView.findViewById(R.id.connection_host_input);
        EditText portInput = dialogView.findViewById(R.id.connection_port_input);
        RadioGroup protocolGroup = dialogView.findViewById(R.id.connection_protocol_group);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.pref_title_add_custom_connection_dialog)
                .setView(dialogView)
                .setNegativeButton(R.string.button_cancel, null)
                .setPositiveButton(R.string.button_save, null)
                .create();

        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> saveCustomConnection(dialog, hostInput, portInput, protocolGroup)));
        dialog.show();
    }

    private void saveCustomConnection(AlertDialog dialog, EditText hostInput, EditText portInput,
                                      RadioGroup protocolGroup) {
        String host = hostInput.getText().toString().trim().toLowerCase(Locale.US);
        String portValue = portInput.getText().toString().trim();

        hostInput.setError(null);
        portInput.setError(null);

        if (TextUtils.isEmpty(host)) {
            hostInput.setError(getString(R.string.connection_error_host_required));
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portValue);
        } catch (NumberFormatException e) {
            portInput.setError(getString(R.string.connection_error_port_invalid));
            return;
        }

        if (port < 1 || port > 65535) {
            portInput.setError(getString(R.string.connection_error_port_invalid));
            return;
        }

        String builtInId = matchBuiltInConnection(host, port);
        if (builtInId != null) {
            dialog.dismiss();
            return;
        }

        String customConnectionId = Configuration.buildCustomVergeConnectionId(host, port,
                getSelectedProtocol(protocolGroup));
        getConfiguration().addVergeCustomConnection(customConnectionId);
        dialog.dismiss();
    }

    private String matchBuiltInConnection(String host, int port) {
        if (port != 50001) {
            return null;
        }
        if ("7eagtn6nsmlyjhjv647ejj4j4orgb2cotoc5dl73qpamhvbvioao4zad.onion".equals(host)) {
            return Configuration.PREFS_VALUE_VERGE_CONNECTION_LEGACY_ONION;
        }
        if ("electrum-verge.cloud".equals(host)) {
            return Configuration.PREFS_VALUE_VERGE_CONNECTION_ELECTRUM_CLOUD;
        }
        if ("electrumx-verge.cloud".equals(host)) {
            return Configuration.PREFS_VALUE_VERGE_CONNECTION_ELECTRUMX_CLOUD;
        }
        return null;
    }

    private Configuration getConfiguration() {
        return ((WalletApplication) requireActivity().getApplication()).getConfiguration();
    }

    private void bindStatusAccount() {
        unbindStatusAccount();

        WalletAccount primaryAccount = WalletSummaryData.getPrimaryAccount(requireContext());
        statusAccount = primaryAccount;
        if (primaryAccount != null) {
            primaryAccount.addEventListener(connectionStatusListener, Threading.SAME_THREAD);
        }
    }

    private void unbindStatusAccount() {
        if (statusAccount != null) {
            statusAccount.removeEventListener(connectionStatusListener);
            statusAccount = null;
        }
        connectionStatusListener.removeCallbacks();
    }

    private void updateConnectionStatusHeader() {
        if (!isAdded()) {
            return;
        }

        CharSequence endpoint = getString(R.string.connections_status_trying_saved_servers);
        boolean connected = isActivelyConnected();
        String connectedServerName = getConnectedEndpoint();
        if (!TextUtils.isEmpty(connectedServerName)) {
            endpoint = connectedServerName;
        }

        if (requireActivity() instanceof ConnectionStatusHost) {
            ((ConnectionStatusHost) requireActivity()).updateConnectionStatus(endpoint, connected);
        }
    }

    private boolean isActivelyConnected() {
        WalletAccount primaryAccount = WalletSummaryData.getPrimaryAccount(requireContext());
        return primaryAccount != null
                && primaryAccount.getConnectivityStatus() == WalletConnectivityStatus.CONNECTED;
    }

    private String getConnectedEndpoint() {
        WalletAccount primaryAccount = WalletSummaryData.getPrimaryAccount(requireContext());
        if (primaryAccount == null) {
            return null;
        }
        return primaryAccount.getConnectedServerName();
    }

    private boolean isBuiltInEndpoint(String endpoint) {
        return endpoint.equalsIgnoreCase(getString(R.string.pref_connection_option_legacy_onion))
                || endpoint.equalsIgnoreCase(getString(R.string.pref_connection_option_electrum_cloud))
                || endpoint.equalsIgnoreCase(getString(R.string.pref_connection_option_electrumx_cloud));
    }

    private String formatEndpoint(String host, String port) {
        return host + ":" + port;
    }

    private ServerAddress.Protocol getSelectedProtocol(RadioGroup protocolGroup) {
        if (protocolGroup.getCheckedRadioButtonId() == R.id.connection_protocol_electrum) {
            return ServerAddress.Protocol.LEGACY_ELECTRUM;
        }
        return ServerAddress.Protocol.ELECTRUMX;
    }

    private CharSequence getProtocolLabel(ServerAddress.Protocol protocol) {
        switch (protocol) {
            case LEGACY_ELECTRUM:
                return getString(R.string.connection_protocol_electrum);
            case ELECTRUMX:
                return getString(R.string.connection_protocol_electrumx);
            case AUTO:
            default:
                return getString(R.string.connection_protocol_auto);
        }
    }

    private void reloadConnections() {
        Intent intent = new Intent(CoinService.ACTION_RELOAD_CONNECTIONS, null,
                requireContext(), CoinServiceImpl.class);
        requireContext().startService(intent);
    }

    private void styleConnectionHeaders() {
        if (savedConnectionsInfoPreference != null) {
            savedConnectionsInfoPreference.setTitle(styleSectionTitle(
                    getString(R.string.pref_title_saved_connections), false));
            savedConnectionsInfoPreference.setIconSpaceReserved(false);
        }
        if (savedConnectionsCategory != null) {
            savedConnectionsCategory.setTitle(styleSectionTitle(
                    getString(R.string.pref_title_verge_connection_profile), true));
            savedConnectionsCategory.setIconSpaceReserved(false);
        }
    }

    private CharSequence styleSectionTitle(String text, boolean emphasize) {
        SpannableString styled = new SpannableString(text);
        styled.setSpan(new ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.accent)),
                0, styled.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        styled.setSpan(new StyleSpan(Typeface.BOLD), 0, styled.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (emphasize) {
            styled.setSpan(new RelativeSizeSpan(1.12f), 0, styled.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return styled;
    }

    private CharSequence styleServerEndpoint(CharSequence endpoint) {
        SpannableString styled = new SpannableString(endpoint);
        styled.setSpan(new StyleSpan(Typeface.BOLD), 0, styled.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return styled;
    }

    public interface ConnectionStatusHost {
        void updateConnectionStatus(CharSequence endpoint, boolean connected);
    }
}
