package com.vergepay.wallet.ui;

import android.animation.ValueAnimator;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Shader;
import android.os.Bundle;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.vergepay.wallet.R;

public class ConnectionsActivity extends BaseWalletActivity
        implements ConnectionsFragment.ConnectionStatusHost {
    private static final long STATUS_GRADIENT_DURATION_MS = 2600L;

    private TextView serverLabel;
    private ImageView connectedDot;
    private ImageView disconnectedDot;
    private ValueAnimator serverLabelAnimator;
    private LinearGradient serverLabelGradient;
    private final Matrix serverLabelGradientMatrix = new Matrix();

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
        connectedDot.setVisibility(connected ? ImageView.VISIBLE : ImageView.INVISIBLE);
        disconnectedDot.setVisibility(connected ? ImageView.GONE : ImageView.VISIBLE);
        if (connected) {
            stopServerLabelAnimation(true);
            serverLabel.setTextColor(ContextCompat.getColor(this, R.color.fg_ok));
        } else {
            serverLabel.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            startServerLabelAnimation();
        }
    }

    @Override
    protected void onPause() {
        stopServerLabelAnimation(true);
        super.onPause();
    }

    private void startServerLabelAnimation() {
        if (serverLabel == null) {
            return;
        }

        stopServerLabelAnimation(false);
        serverLabel.post(new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || serverLabel == null) {
                    return;
                }

                final float textWidth = Math.max(serverLabel.getWidth(),
                        serverLabel.getPaint().measureText(serverLabel.getText().toString()));
                if (textWidth <= 0f) {
                    return;
                }

                int[] colors = new int[] {
                        ContextCompat.getColor(ConnectionsActivity.this, R.color.progress_bar_color_2),
                        ContextCompat.getColor(ConnectionsActivity.this, R.color.text_primary),
                        ContextCompat.getColor(ConnectionsActivity.this, R.color.progress_bar_color_3),
                        ContextCompat.getColor(ConnectionsActivity.this, R.color.progress_bar_color_4),
                        ContextCompat.getColor(ConnectionsActivity.this, R.color.progress_bar_color_2)
                };
                float[] positions = new float[] {0f, 0.28f, 0.55f, 0.8f, 1f};
                serverLabelGradient = new LinearGradient(
                        -textWidth, 0f, 0f, 0f, colors, positions, Shader.TileMode.CLAMP);
                serverLabel.getPaint().setShader(serverLabelGradient);

                ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
                animator.setDuration(STATUS_GRADIENT_DURATION_MS);
                animator.setRepeatCount(ValueAnimator.INFINITE);
                animator.setInterpolator(new LinearInterpolator());
                animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        if (serverLabelGradient == null || serverLabel == null) {
                            return;
                        }
                        float progress = (Float) animation.getAnimatedValue();
                        serverLabelGradientMatrix.setTranslate(textWidth * 2f * progress, 0f);
                        serverLabelGradient.setLocalMatrix(serverLabelGradientMatrix);
                        serverLabel.invalidate();
                    }
                });
                serverLabelAnimator = animator;
                animator.start();
            }
        });
    }

    private void stopServerLabelAnimation(boolean clearShader) {
        if (serverLabelAnimator != null) {
            serverLabelAnimator.cancel();
            serverLabelAnimator = null;
        }
        if (clearShader && serverLabel != null) {
            serverLabel.getPaint().setShader(null);
            serverLabel.invalidate();
        }
        serverLabelGradient = null;
    }
}
