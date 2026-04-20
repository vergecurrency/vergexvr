package com.vergepay.wallet.ui.dialogs;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import android.view.View;
import android.view.Window;

import com.vergepay.wallet.R;

/**
 * @author John L. Jegutanis
 */
public class TermsOfUseDialog extends DialogFragment {
    private Listener listener;

    public static TermsOfUseDialog newInstance() {
        return new TermsOfUseDialog();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (activity instanceof Listener) {
            listener = (Listener) activity;
        }
    }

    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final View view = getActivity().getLayoutInflater().inflate(R.layout.dialog_terms_of_use, null);
        final AlertDialog dialog = new AlertDialog.Builder(getActivity())
                .setView(view)
                .create();

        final View disagreeButton = view.findViewById(R.id.terms_disagree);
        final View agreeButton = view.findViewById(R.id.terms_agree);
        if (listener != null) {
            disagreeButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onTermsDisagree();
                    dismissAllowingStateLoss();
                }
            });
            agreeButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onTermsAgree();
                    dismissAllowingStateLoss();
                }
            });
        } else {
            disagreeButton.setVisibility(View.GONE);
            agreeButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismissAllowingStateLoss();
                }
            });
        }

        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawableResource(android.R.color.transparent);
            }
        }
    }

    public interface Listener {
        void onTermsAgree();
        void onTermsDisagree();
    }
}
