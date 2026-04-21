package com.vergepay.wallet.ui;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.vergepay.wallet.R;

public class ConnectionsActivity extends BaseWalletActivity
        implements ConnectionsFragment.ConnectionStatusHost {
    private TextView serverLabel;
    private ImageView connectedDot;
    private ImageView disconnectedDot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connections);
        serverLabel = findViewById(R.id.connections_server_label);
        connectedDot = findViewById(R.id.connections_connected_dot);
        disconnectedDot = findViewById(R.id.connections_disconnected_dot);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, new ConnectionsFragment())
                    .commit();
        }
        setupWrapperHeader();
    }

    @Override
    public void updateConnectionStatus(CharSequence endpoint, boolean connected) {
        if (serverLabel == null || connectedDot == null || disconnectedDot == null) {
            return;
        }

        serverLabel.setText(endpoint);
        serverLabel.setTextColor(ContextCompat.getColor(this,
                connected ? R.color.fg_ok : R.color.text_primary));
        connectedDot.setVisibility(connected ? ImageView.VISIBLE : ImageView.INVISIBLE);
        disconnectedDot.setVisibility(connected ? ImageView.GONE : ImageView.VISIBLE);
    }
}
