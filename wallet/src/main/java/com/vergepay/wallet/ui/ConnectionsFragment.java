package com.vergepay.wallet.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;

import com.vergepay.core.wallet.WalletAccount;
import com.vergepay.core.wallet.WalletConnectivityStatus;
import com.vergepay.stratumj.ServerAddress;
import com.vergepay.wallet.Configuration;
import com.vergepay.wallet.Constants;
import com.vergepay.wallet.R;
import com.vergepay.wallet.WalletApplication;
import com.vergepay.wallet.service.CoinService;
import com.vergepay.wallet.service.CoinServiceImpl;
import com.vergepay.wallet.ui.summary.WalletSummaryData;
import com.vergepay.wallet.util.ThrottlingWalletChangeListener;

import org.bitcoinj.utils.Threading;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ConnectionsFragment extends Fragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private LinearLayout connectionsListContainer;
    private Button addCustomConnectionButton;
    private WalletAccount statusAccount;
    private BroadcastReceiver torStatusReceiver;
    private final ThrottlingWalletChangeListener connectionStatusListener =
            new ThrottlingWalletChangeListener(250, false, false, false, true) {
                @Override
                public void onThrottledWalletChanged() {
                    syncConnectionList();
                    updateConnectionStatusHeader();
                }
            };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_connections, container, false);
        connectionsListContainer = view.findViewById(R.id.connections_list_container);
        addCustomConnectionButton = view.findViewById(R.id.connections_add_custom_button);
        addCustomConnectionButton.setOnClickListener(v -> showAddCustomConnectionDialog());
        syncConnectionList();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        getConfiguration().registerOnSharedPreferenceChangeListener(this);
        bindStatusAccount();
        registerTorStatusReceiver();
        syncConnectionList();
        updateConnectionStatusHeader();
    }

    @Override
    public void onPause() {
        getConfiguration().unregisterOnSharedPreferenceChangeListener(this);
        unregisterTorStatusReceiver();
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
            reloadConnections();
        }
    }

    private void syncConnectionList() {
        if (connectionsListContainer == null || !isAdded()) {
            return;
        }

        connectionsListContainer.removeAllViews();
        String connectedEndpoint = getConnectedEndpoint();
        boolean activelyConnected = isActivelyConnected();
        String selectedProfile = getConfiguration().getVergeConnectionProfile();
        boolean fallbackConnected = activelyConnected && !TextUtils.isEmpty(connectedEndpoint);

        addConnectionRow(getString(R.string.pref_connection_option_legacy_onion),
                Configuration.PREFS_VALUE_VERGE_CONNECTION_LEGACY_ONION,
                getConnectionTypeSummary(ServerAddress.Protocol.LEGACY_ELECTRUM,
                        ServerAddress.Transport.PLAIN_TCP),
                connectedEndpoint, activelyConnected, selectedProfile, fallbackConnected);
        addConnectionRow(getString(R.string.pref_connection_option_electrum_cloud),
                Configuration.PREFS_VALUE_VERGE_CONNECTION_ELECTRUM_CLOUD,
                getConnectionTypeSummary(ServerAddress.Protocol.ELECTRUMX,
                        ServerAddress.Transport.PLAIN_TCP),
                connectedEndpoint, activelyConnected, selectedProfile, fallbackConnected);
        addConnectionRow(getString(R.string.pref_connection_option_electrum_cloud_ssl),
                Configuration.PREFS_VALUE_VERGE_CONNECTION_ELECTRUM_CLOUD_SSL,
                getConnectionTypeSummary(ServerAddress.Protocol.ELECTRUMX,
                        ServerAddress.Transport.SSL_TLS),
                connectedEndpoint, activelyConnected, selectedProfile, fallbackConnected);
        addConnectionRow(getString(R.string.pref_connection_option_electrumx_cloud),
                Configuration.PREFS_VALUE_VERGE_CONNECTION_ELECTRUMX_CLOUD,
                getConnectionTypeSummary(ServerAddress.Protocol.ELECTRUMX,
                        ServerAddress.Transport.PLAIN_TCP),
                connectedEndpoint, activelyConnected, selectedProfile, fallbackConnected);
        addConnectionRow(getString(R.string.pref_connection_option_electrumx_cloud_ssl),
                Configuration.PREFS_VALUE_VERGE_CONNECTION_ELECTRUMX_CLOUD_SSL,
                getConnectionTypeSummary(ServerAddress.Protocol.ELECTRUMX,
                        ServerAddress.Transport.SSL_TLS),
                connectedEndpoint, activelyConnected, selectedProfile, fallbackConnected);

        List<String> customIds = new ArrayList<>(getConfiguration().getVergeCustomConnectionIds());
        Collections.sort(customIds);
        for (String customId : customIds) {
            String[] customConnection = Configuration.parseCustomVergeConnectionId(customId);
            if (customConnection == null) {
                continue;
            }

            ServerAddress.Transport transport =
                    Configuration.parseCustomVergeConnectionTransport(customConnection[3]);
            if (isBuiltInConnection(customConnection[0], customConnection[1], transport)) {
                continue;
            }

            addConnectionRow(formatEndpoint(customConnection[0], customConnection[1]), customId,
                    getConnectionTypeSummary(
                            Configuration.parseCustomVergeConnectionProtocol(customConnection[2]),
                            transport),
                    connectedEndpoint, activelyConnected, selectedProfile, fallbackConnected);
        }
    }

    private void addConnectionRow(String endpoint, String connectionId, CharSequence connectionType,
                                  String connectedEndpoint, boolean activelyConnected,
                                  String selectedProfile, boolean fallbackConnected) {
        View row = LayoutInflater.from(requireContext())
                .inflate(R.layout.connection_server_row, connectionsListContainer, false);
        TextView endpointView = row.findViewById(R.id.connection_row_endpoint);
        TextView summaryView = row.findViewById(R.id.connection_row_summary);
        TextView stateView = row.findViewById(R.id.connection_row_state);
        Button connectButton = row.findViewById(R.id.connection_row_connect);

        endpointView.setText(endpoint);
        summaryView.setText(connectionType);

        boolean isSelected = connectionId.equals(selectedProfile);
        boolean isConnected = activelyConnected && endpoint.equalsIgnoreCase(connectedEndpoint);

        if (isConnected) {
            stateView.setText(R.string.pref_connection_connected);
            stateView.setTextColor(ContextCompat.getColor(requireContext(), R.color.fg_ok));
            stateView.setVisibility(View.VISIBLE);
            connectButton.setVisibility(View.GONE);
        } else if (isSelected) {
            stateView.setText(R.string.pref_connection_connecting);
            stateView.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_alt));
            stateView.setVisibility(View.VISIBLE);
            connectButton.setVisibility(View.VISIBLE);
            connectButton.setEnabled(false);
            connectButton.setText(R.string.pref_connection_selected);
        } else if (isSelected && fallbackConnected) {
            stateView.setText(R.string.pref_connection_fallback_active);
            stateView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
            stateView.setVisibility(View.VISIBLE);
            connectButton.setVisibility(View.VISIBLE);
            connectButton.setEnabled(true);
            connectButton.setText(R.string.button_connect);
            connectButton.setOnClickListener(v -> selectConnection(connectionId));
        } else {
            stateView.setVisibility(View.GONE);
            connectButton.setVisibility(View.VISIBLE);
            connectButton.setEnabled(true);
            connectButton.setText(R.string.button_connect);
            connectButton.setOnClickListener(v -> selectConnection(connectionId));
        }

        connectionsListContainer.addView(row);
    }

    private void showAddCustomConnectionDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_connection, null, false);
        EditText hostInput = dialogView.findViewById(R.id.connection_host_input);
        EditText portInput = dialogView.findViewById(R.id.connection_port_input);
        RadioGroup protocolGroup = dialogView.findViewById(R.id.connection_protocol_group);
        RadioGroup transportGroup = dialogView.findViewById(R.id.connection_transport_group);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.pref_title_add_custom_connection_dialog)
                .setView(dialogView)
                .setNegativeButton(R.string.button_cancel, null)
                .setPositiveButton(R.string.button_save, null)
                .create();

        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> saveCustomConnection(dialog, hostInput, portInput,
                        protocolGroup, transportGroup)));
        dialog.show();
    }

    private void saveCustomConnection(AlertDialog dialog, EditText hostInput, EditText portInput,
                                      RadioGroup protocolGroup, RadioGroup transportGroup) {
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

        ServerAddress.Transport transport = getSelectedTransport(transportGroup);
        String builtInId = matchBuiltInConnection(host, port, transport);
        if (builtInId != null) {
            getConfiguration().setVergeConnectionProfile(builtInId);
            dialog.dismiss();
            return;
        }

        String customConnectionId = Configuration.buildCustomVergeConnectionId(host, port,
                getSelectedProtocol(protocolGroup), transport);
        getConfiguration().addVergeCustomConnection(customConnectionId);
        getConfiguration().setVergeConnectionProfile(customConnectionId);
        dialog.dismiss();
    }

    private void selectConnection(String connectionId) {
        Configuration configuration = getConfiguration();
        if (connectionId.equals(configuration.getVergeConnectionProfile())) {
            syncConnectionList();
            updateConnectionStatusHeader();
            return;
        }

        configuration.setVergeConnectionProfile(connectionId);
    }

    private String matchBuiltInConnection(String host, int port, ServerAddress.Transport transport) {
        if ("7eagtn6nsmlyjhjv647ejj4j4orgb2cotoc5dl73qpamhvbvioao4zad.onion".equals(host)
                && port == 50001
                && transport == ServerAddress.Transport.PLAIN_TCP) {
            return Configuration.PREFS_VALUE_VERGE_CONNECTION_LEGACY_ONION;
        }
        if ("electrum-verge.cloud".equals(host) && port == 50001
                && transport == ServerAddress.Transport.PLAIN_TCP) {
            return Configuration.PREFS_VALUE_VERGE_CONNECTION_ELECTRUM_CLOUD;
        }
        if ("electrum-verge.cloud".equals(host) && port == 50002
                && transport == ServerAddress.Transport.SSL_TLS) {
            return Configuration.PREFS_VALUE_VERGE_CONNECTION_ELECTRUM_CLOUD_SSL;
        }
        if ("electrumx-verge.cloud".equals(host) && port == 50001
                && transport == ServerAddress.Transport.PLAIN_TCP) {
            return Configuration.PREFS_VALUE_VERGE_CONNECTION_ELECTRUMX_CLOUD;
        }
        if ("electrumx-verge.cloud".equals(host) && port == 50002
                && transport == ServerAddress.Transport.SSL_TLS) {
            return Configuration.PREFS_VALUE_VERGE_CONNECTION_ELECTRUMX_CLOUD_SSL;
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

        WalletApplication application = (WalletApplication) requireActivity().getApplication();
        WalletConnectivityStatus connectivity = getConnectivityStatus();
        String statusText = getConnectionPhaseLabel(application, connectivity);
        boolean connected = connectivity == WalletConnectivityStatus.CONNECTED
                && Constants.TOR_STATUS_READY.equals(application.getTorStatus())
                && application.isConnected();
        String endpoint = getSelectedOrConnectedEndpoint();
        if (TextUtils.isEmpty(endpoint)) {
            endpoint = getString(R.string.connections_status_trying_saved_servers);
        }
        CharSequence headerText = getString(R.string.connections_status_endpoint_state,
                endpoint, statusText);

        if (requireActivity() instanceof ConnectionStatusHost) {
            ((ConnectionStatusHost) requireActivity()).updateConnectionStatus(headerText, connected);
        }
    }

    private boolean isActivelyConnected() {
        WalletAccount primaryAccount = WalletSummaryData.getPrimaryAccount(requireContext());
        return primaryAccount != null
                && primaryAccount.getConnectivityStatus() == WalletConnectivityStatus.CONNECTED;
    }

    private WalletConnectivityStatus getConnectivityStatus() {
        WalletAccount primaryAccount = WalletSummaryData.getPrimaryAccount(requireContext());
        if (primaryAccount == null) {
            return WalletConnectivityStatus.DISCONNECTED;
        }
        return primaryAccount.getConnectivityStatus();
    }

    private String getConnectedEndpoint() {
        WalletAccount primaryAccount = WalletSummaryData.getPrimaryAccount(requireContext());
        if (primaryAccount == null) {
            return null;
        }
        return primaryAccount.getConnectedServerName();
    }

    private boolean isBuiltInConnection(String host, String port, ServerAddress.Transport transport) {
        try {
            return matchBuiltInConnection(host, Integer.parseInt(port), transport) != null;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String formatEndpoint(String host, String port) {
        return host + ":" + port;
    }

    private String getSelectedOrConnectedEndpoint() {
        String connectedEndpoint = getConnectedEndpoint();
        if (!TextUtils.isEmpty(connectedEndpoint) && isActivelyConnected()) {
            return connectedEndpoint;
        }

        String profile = getConfiguration().getVergeConnectionProfile();
        if (Configuration.PREFS_VALUE_VERGE_CONNECTION_LEGACY_ONION.equals(profile)) {
            return getString(R.string.pref_connection_option_legacy_onion);
        }
        if (Configuration.PREFS_VALUE_VERGE_CONNECTION_ELECTRUM_CLOUD.equals(profile)) {
            return getString(R.string.pref_connection_option_electrum_cloud);
        }
        if (Configuration.PREFS_VALUE_VERGE_CONNECTION_ELECTRUM_CLOUD_SSL.equals(profile)) {
            return getString(R.string.pref_connection_option_electrum_cloud_ssl);
        }
        if (Configuration.PREFS_VALUE_VERGE_CONNECTION_ELECTRUMX_CLOUD.equals(profile)) {
            return getString(R.string.pref_connection_option_electrumx_cloud);
        }
        if (Configuration.PREFS_VALUE_VERGE_CONNECTION_ELECTRUMX_CLOUD_SSL.equals(profile)) {
            return getString(R.string.pref_connection_option_electrumx_cloud_ssl);
        }

        String[] customConnection = Configuration.parseCustomVergeConnectionId(profile);
        if (customConnection != null) {
            return formatEndpoint(customConnection[0], customConnection[1]);
        }

        return connectedEndpoint;
    }

    private String getConnectionPhaseLabel(WalletApplication application,
                                           WalletConnectivityStatus connectivity) {
        if (!application.isConnected()) {
            return getString(R.string.connection_status_network_waiting);
        }

        String torStatus = application.getTorStatus();
        if (Constants.TOR_STATUS_FAILED.equals(torStatus)) {
            return getString(R.string.connection_status_tor_failed);
        }
        if (Constants.TOR_STATUS_STOPPED.equals(torStatus)) {
            return getString(R.string.connection_status_tor_stopped);
        }
        if (!Constants.TOR_STATUS_READY.equals(torStatus)) {
            return getString(R.string.connection_status_tor_starting);
        }

        switch (connectivity) {
            case CONNECTED:
                return getString(R.string.pref_connection_connected);
            case LOADING:
                return getString(R.string.connection_status_syncing_over_tor);
            case DISCONNECTED:
            default:
                return getString(R.string.connection_status_connecting_over_tor);
        }
    }

    private ServerAddress.Protocol getSelectedProtocol(RadioGroup protocolGroup) {
        if (protocolGroup.getCheckedRadioButtonId() == R.id.connection_protocol_electrum) {
            return ServerAddress.Protocol.LEGACY_ELECTRUM;
        }
        return ServerAddress.Protocol.ELECTRUMX;
    }

    private ServerAddress.Transport getSelectedTransport(RadioGroup transportGroup) {
        if (transportGroup.getCheckedRadioButtonId() == R.id.connection_transport_tcp) {
            return ServerAddress.Transport.PLAIN_TCP;
        }
        return ServerAddress.Transport.SSL_TLS;
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

    private CharSequence getTransportLabel(ServerAddress.Transport transport) {
        switch (transport) {
            case SSL_TLS:
                return getString(R.string.connection_transport_ssl);
            case PLAIN_TCP:
            default:
                return getString(R.string.connection_transport_tcp);
        }
    }

    private CharSequence getConnectionTypeSummary(ServerAddress.Protocol protocol,
                                                  ServerAddress.Transport transport) {
        return getProtocolLabel(protocol) + " - " + getTransportLabel(transport);
    }

    private void reloadConnections() {
        Intent intent = new Intent(CoinService.ACTION_RELOAD_CONNECTIONS, null,
                requireContext(), CoinServiceImpl.class);
        requireContext().startService(intent);
    }

    private void registerTorStatusReceiver() {
        if (torStatusReceiver != null) {
            return;
        }

        torStatusReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                syncConnectionList();
                updateConnectionStatusHeader();
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

    public interface ConnectionStatusHost {
        void updateConnectionStatus(CharSequence endpoint, boolean connected);
    }
}
