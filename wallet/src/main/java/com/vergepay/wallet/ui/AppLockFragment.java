package com.vergepay.wallet.ui;

import android.app.Activity;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.core.hardware.fingerprint.FingerprintManagerCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import com.vergepay.wallet.R;
import com.vergepay.wallet.WalletApplication;
import com.vergepay.wallet.ui.dialogs.FingerprintScanDialog;
import com.vergepay.wallet.util.Crypto;

public class AppLockFragment extends Fragment {

    private Button fingerPrintButton;
    private Button pincodeButton;
    private Button skipButton;
    private FingerprintManagerCompat fingerprintManager;
    private String pincode;
    private boolean includeFingerprint = false;
	private AppLockListener lockListener;

    public static AppLockFragment newInstance(Bundle args) {
        AppLockFragment lockFragment = new AppLockFragment();
        lockFragment.setArguments(args);
        return lockFragment;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_app_lock, container, false);
        fingerPrintButton = view.findViewById(R.id.fingerprint_button);
        pincodeButton = view.findViewById(R.id.pincode_button);
        skipButton = view.findViewById(R.id.skip_button);

        pincodeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                includeFingerprint = false;
                setupPincode();
            }
        });

        fingerprintManager = FingerprintManagerCompat.from(getContext());

        fingerPrintButton.setEnabled(fingerprintManager.isHardwareDetected());

        fingerPrintButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setupFingerprint();
            }
        });

        skipButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
				lockListener.onAppLockResult(getArguments());

            }
        });

        return view;
    }

    private void setupFingerprint() {
        if (!fingerprintManager.hasEnrolledFingerprints()) {
            Toast.makeText(getContext(), R.string.error_no_fingerprints, Toast.LENGTH_LONG).show();
            return;
        }

        includeFingerprint = true;

        final FingerprintScanDialog fingerprintScanDialog = FingerprintScanDialog.newInstance(FingerprintScanDialog.Mode.ADD);
        fingerprintScanDialog.setCallback(new FingerprintScanDialog.Callback() {

            @Override
            public void onPincodeClicked() {

            }
			
            @Override
            public void onAuthenticationError(int errMsgId, CharSequence errString) {
                super.onAuthenticationError(errMsgId, errString);

                Toast.makeText(getActivity().getApplicationContext(), errString, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) {
                super.onAuthenticationHelp(helpMsgId, helpString);

                Toast.makeText(getActivity().getApplicationContext(), helpMsgId, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAuthenticationSucceeded(FingerprintManagerCompat.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);

                setupPincode();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();

                Toast.makeText(getActivity().getApplicationContext(), R.string.finger_not_recognized, Toast.LENGTH_SHORT).show();
            }
			
			
			
        });

        fingerprintScanDialog.show(getFragmentManager(), null);
    }

    private void setupPincode() {
        final EnterPincodeFragment enterPincodeFragment = new EnterPincodeFragment();
        enterPincodeFragment.setResultCallback(new EnterPincodeFragment.Callback() {
            @Override
            public void onResult(String pincode) {
                AppLockFragment.this.pincode = pincode;

                String hashMD5 = Crypto.hashMD5(pincode);

                ((WalletApplication) getActivity().getApplication()).getConfiguration().setPincodeHash(hashMD5);
                ((WalletApplication) getActivity().getApplication()).getConfiguration().setFingerprintEnabled(includeFingerprint);
				
				lockListener.onAppLockResult(getArguments());
            }
        });

        enterPincodeFragment.show(getFragmentManager(), null);
    }
	
	@Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        try {
            lockListener = (AppLockListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException("Activity must implement " + AppLockListener.class.getCanonicalName());
        }
    }

    public interface AppLockListener {

        void onAppLockResult(Bundle args);
    }
}