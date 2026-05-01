package com.vergepay.wallet.ui.dialogs;

import android.content.DialogInterface;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.core.hardware.fingerprint.FingerprintManagerCompat;
import androidx.core.os.CancellationSignal;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.vergepay.wallet.R;

public class FingerprintScanDialog extends DialogFragment implements View.OnClickListener {

    private FingerprintManagerCompat fingerprintManager;
    private CancellationSignal signal;
    private Callback callback;
    private Mode mode;

    public static FingerprintScanDialog newInstance(Mode mode) {
        FingerprintScanDialog fingerprintScanDialog = new FingerprintScanDialog();
        fingerprintScanDialog.setMode(mode);

        return fingerprintScanDialog;
    }

    public abstract static class Callback extends FingerprintManagerCompat.AuthenticationCallback {

        public abstract void onPincodeClicked();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        signal = new CancellationSignal();
        FingerprintManagerCompat.AuthenticationCallback tempCallback = new FingerprintManagerCompat.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errMsgId, CharSequence errString) {
                super.onAuthenticationError(errMsgId, errString);

                dismiss();

                if (callback != null) {
                    callback.onAuthenticationError(errMsgId, errString);
                }
            }

            @Override
            public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) {
                super.onAuthenticationHelp(helpMsgId, helpString);
                if (callback != null) {
                    callback.onAuthenticationError(helpMsgId, helpString);
                }
            }

            @Override
            public void onAuthenticationSucceeded(FingerprintManagerCompat.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);

                dismiss();

                if (callback != null) {
                    callback.onAuthenticationSucceeded(result);
                }
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                if (callback != null) {
                    callback.onAuthenticationFailed();
                }
            }
        };

        fingerprintManager = FingerprintManagerCompat.from(getContext());
        fingerprintManager.authenticate(null, 0, signal, tempCallback, null);
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View view = inflater.inflate(R.layout.fragment_scan_fingerprint, container, false);

        TextView titleAdd = view.findViewById(R.id.title_add);
        TextView titleCheck = view.findViewById(R.id.title_check);

        switch (mode) {
            case CHECK:
                titleAdd.setVisibility(View.GONE);
                break;
            case ADD:
                titleCheck.setVisibility(View.GONE);
                break;
        }

        Button pincodeButton = view.findViewById(R.id.pincode_button);

        if (mode == Mode.ADD) {
            pincodeButton.setVisibility(View.GONE);
			setCancelable(true);
        } else {
			setCancelable(true);
            pincodeButton.setOnClickListener(this);
        }
        return view;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        signal.cancel();
    }

    @Override
    public void onClick(View view) {
        if (callback != null) {
            callback.onPincodeClicked();
        }
    }

    public enum Mode {
        CHECK, ADD
    }
}