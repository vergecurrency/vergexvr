package com.vergepay.wallet.ui;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;

import com.vergepay.wallet.R;
import com.vergepay.wallet.WalletApplication;

public class ApplockActivity extends AppCompatActivity implements AppLockFragment.AppLockListener {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_applock);

        boolean hasPincodeHash = ((WalletApplication) getApplication()).getConfiguration().hasPincodeHash();

        Button removeAppLock = findViewById(R.id.remove_app_lock);

        if (hasPincodeHash) {
            removeAppLock.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    ((WalletApplication) getApplication()).getConfiguration().removeAppLock();
                    finish();
                }
            });
        } else {
            removeAppLock.setVisibility(View.GONE);
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.placeholder, AppLockFragment.newInstance(null))
                    .commit();
        }
    }

    @Override
    public void onAppLockResult(Bundle args) {
        finish();
    }
}