package com.vergepay.wallet.ui;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;

import com.vergepay.wallet.R;
import com.vergepay.wallet.ui.widget.IntroSmokeView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class WelcomeFragment extends Fragment {
    private static final Logger log = LoggerFactory.getLogger(WelcomeFragment.class);
    private static final long INTRO_ANIMATION_DURATION_MS = 1250L;

    private Listener listener;
    private ImageView introLogo;
    private IntroSmokeView introSmoke;

    public WelcomeFragment() { }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_welcome, container, false);
        introLogo = view.findViewById(R.id.intro_logo);
        introSmoke = view.findViewById(R.id.intro_smoke);

        view.findViewById(R.id.create_wallet).setOnClickListener(getOnCreateListener());
        view.findViewById(R.id.restore_wallet).setOnClickListener(getOnRestoreListener());

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        startIntroAnimation();
    }

    @Override
    public void onStop() {
        if (introSmoke != null) {
            introSmoke.stop();
        }
        if (introLogo != null) {
            introLogo.animate().cancel();
            introLogo.clearAnimation();
        }
        super.onStop();
    }

    private void startIntroAnimation() {
        if (introLogo == null) return;

        if (introSmoke != null) {
            introSmoke.setAlpha(0f);
            introSmoke.start();
            introSmoke.animate()
                    .alpha(0.85f)
                    .setDuration(900L)
                    .start();
        }

        introLogo.setAlpha(0f);
        introLogo.setScaleX(0.42f);
        introLogo.setScaleY(0.42f);
        introLogo.setRotation(-200f);
        introLogo.setTranslationY(64f);
        introLogo.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .rotation(0f)
                .translationY(0f)
                .setInterpolator(new OvershootInterpolator(1.15f))
                .setDuration(INTRO_ANIMATION_DURATION_MS)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        startPulseAnimation();
                    }
                })
                .start();
    }

    private void startPulseAnimation() {
        if (introLogo == null || !isAdded()) return;

        introLogo.animate()
                .scaleX(1.035f)
                .scaleY(1.035f)
                .setDuration(2200L)
                .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (introLogo == null || !isAdded()) return;
                        introLogo.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(2200L)
                                .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                                .withEndAction(new Runnable() {
                                    @Override
                                    public void run() {
                                        startPulseAnimation();
                                    }
                                })
                                .start();
                    }
                })
                .start();
    }

    private View.OnClickListener getOnCreateListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                log.info("Clicked create new wallet");
                if (listener != null) {
                    listener.onCreateNewWallet();
                }
            }
        };
    }

    private View.OnClickListener getOnRestoreListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                log.info("Clicked restore wallet");
                if (listener != null) {
                    listener.onRestoreWallet();
                }
            }
        };
    }

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        try {
            listener = (Listener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context + " must implement " + Listener.class);
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    public interface Listener {
        void onCreateNewWallet();
        void onRestoreWallet();
        void onSeedCreated(String seed);
        void onSeedVerified(Bundle args);
    }
}
