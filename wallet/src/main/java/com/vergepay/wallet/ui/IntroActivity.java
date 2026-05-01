package com.vergepay.wallet.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import com.vergepay.wallet.R;

public class IntroActivity extends AbstractWalletFragmentActivity
        implements WelcomeFragment.Listener, PasswordConfirmationFragment.Listener,
        SetPasswordFragment.Listener, SelectCoinsFragment.Listener, AppLockFragment.AppLockListener {
    private boolean showWrapperHeader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showWrapperHeader = getWalletApplication().getWallet() != null;

        if (showWrapperHeader) {
            setTitle(R.string.pref_title_restore_wallet);
            setContentView(R.layout.activity_fragment_wrapper);
            setupWrapperHeader();
        } else {
            setContentView(R.layout.activity_fragment_plain);
            WindowInsetsHelper.applyPaddingInsets(findViewById(R.id.container), true, true);
        }

        // If we detected that this device is incompatible
        if (!getWalletApplication().getConfiguration().isDeviceCompatible()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.incompatible_device_warning_title)
                    .setMessage(R.string.incompatible_device_warning_message)
                    .setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .setCancelable(false)
                    .create().show();
        } else {
            if (savedInstanceState == null) {
                getSupportFragmentManager().beginTransaction()
                        .add(R.id.container, new WelcomeFragment())
                        .commit();
            }
        }
    }

    private void setupWrapperHeader() {
        ImageButton backButton = findViewById(R.id.wrapper_back);
        TextView titleView = findViewById(R.id.wrapper_title);

        if (backButton != null) {
            backButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onBackPressed();
                }
            });
        }
        if (titleView != null) {
            titleView.setText(getTitle());
        }

        WindowInsetsHelper.applyPaddingInsets(findViewById(R.id.wrapper_root), false, true);
        WindowInsetsHelper.applyTopInsetAsPadding(findViewById(R.id.wrapper_nav_container), 0);
    }

    private void replaceFragment(Fragment fragment) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

        // Replace whatever is in the fragment_container view with this fragment,
        // and add the transaction to the back stack so the user can navigate back
        transaction.replace(R.id.container, fragment);
        transaction.addToBackStack(null);

        // Commit the transaction
        transaction.commit();
    }

    @Override
    public void onCreateNewWallet() {
        if (getWalletApplication().getWallet() == null) {
            replaceFragment(new SeedFragment());
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.override_wallet_warning_title)
                    .setMessage(R.string.override_new_wallet_warning_message)
                    .setNegativeButton(R.string.button_cancel, null)
                    .setPositiveButton(R.string.button_confirm, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            replaceFragment(new SeedFragment());
                        }
                    })
                    .create().show();
        }
    }

    @Override
    public void onRestoreWallet() {
        if (getWalletApplication().getWallet() == null) {
            replaceFragment(RestoreFragment.newInstance());
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.override_wallet_warning_title)
                    .setMessage(R.string.override_restore_wallet_warning_message)
                    .setNegativeButton(R.string.button_cancel, null)
                    .setPositiveButton(R.string.button_confirm, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            replaceFragment(RestoreFragment.newInstance());
                        }
                    })
                    .create().show();
        }
    }

    @Override
    public void onSeedCreated(String seed) {
        replaceFragment(RestoreFragment.newInstance(seed));
    }

    @Override
    public void onSeedVerified(Bundle args) {
        replaceFragment(SetPasswordFragment.newInstance(args));
    }

    @Override
    public void onPasswordConfirmed(Bundle args) {
        replaceFragment(AppLockFragment.newInstance(args));
    }

    @Override
    public void onPasswordSet(Bundle args) {
        replaceFragment(AppLockFragment.newInstance(args));
    }

    private void selectCoins(Bundle args) {
        String message = getResources().getString(R.string.select_coins);
        replaceFragment(SelectCoinsFragment.newInstance(message, true, args));
    }

    @Override
    public void onCoinSelection(Bundle args) {
        replaceFragment(FinalizeWalletRestorationFragment.newInstance(args));
    }
	
	@Override
    public void onAppLockResult(Bundle args) {
        selectCoins(args);
    }
}
