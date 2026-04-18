package com.vergepay.wallet.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

import com.vergepay.wallet.R;
import com.vergepay.wallet.util.Fonts;

import butterknife.ButterKnife;
import butterknife.OnClick;

public class AboutActivity extends BaseWalletActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_about);
        ButterKnife.bind(this);
        setupWrapperHeader();

        TextView version = findViewById(R.id.about_version);
        if (getWalletApplication().packageInfo() != null) {
            version.setText(getWalletApplication().packageInfo().versionName);
        } else {
            version.setVisibility(View.INVISIBLE);
        }

        Fonts.setTypeface(findViewById(R.id.translation_globe), Fonts.Font.COINOMI_FONT_ICONS);
    }

    @OnClick(R.id.terms_of_service_button)
    void onTermsOfUseClick() {
        final View view = getLayoutInflater().inflate(R.layout.dialog_terms_of_use, null);
        view.findViewById(R.id.terms_disagree).setVisibility(View.GONE);
        ((TextView) view.findViewById(R.id.terms_agree)).setText(R.string.button_ok);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .create();

        view.findViewById(R.id.terms_agree).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }
    }
}
