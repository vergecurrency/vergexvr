package com.vergepay.wallet.ui.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.Random;

public class IntroSmokeView extends View {
    private static final int PARTICLE_COUNT = 9;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random(1337L);
    private final SmokeParticle[] particles = new SmokeParticle[PARTICLE_COUNT];
    private final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
    private float progress;

    public IntroSmokeView(Context context) {
        super(context);
        init();
    }

    public IntroSmokeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public IntroSmokeView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            particles[i] = createParticle(i * 0.11f);
        }

        animator.setDuration(3600L);
        animator.setInterpolator(new LinearInterpolator());
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                progress = (Float) animation.getAnimatedValue();
                invalidate();
            }
        });
    }

    private SmokeParticle createParticle(float startOffset) {
        SmokeParticle particle = new SmokeParticle();
        particle.startOffset = startOffset;
        particle.baseX = 0.22f + random.nextFloat() * 0.56f;
        particle.baseRadius = 18f + random.nextFloat() * 28f;
        particle.verticalTravel = 0.38f + random.nextFloat() * 0.22f;
        particle.horizontalDrift = 0.04f + random.nextFloat() * 0.06f;
        particle.alphaScale = 0.16f + random.nextFloat() * 0.18f;
        particle.color = random.nextBoolean() ? Color.parseColor("#66D9FFF7") : Color.parseColor("#557A3DF0");
        return particle;
    }

    public void start() {
        if (!animator.isStarted()) {
            animator.start();
        } else if (animator.isPaused()) {
            animator.resume();
        }
    }

    public void stop() {
        if (animator.isRunning()) {
            animator.cancel();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        final float width = getWidth();
        final float height = getHeight();
        if (width <= 0 || height <= 0) return;

        for (int i = 0; i < particles.length; i++) {
            SmokeParticle particle = particles[i];
            float t = wrappedProgress(progress + particle.startOffset);
            float eased = easeOut(t);
            float alphaEnvelope = (1f - Math.abs((t * 2f) - 1f));

            float cx = width * particle.baseX
                    + (float) Math.sin((t * Math.PI * 2f) + i) * width * particle.horizontalDrift;
            float cy = height * (0.82f - (particle.verticalTravel * eased));
            float radius = particle.baseRadius * (0.72f + (0.85f * eased));
            int alpha = Math.min(255, Math.max(0, (int) (255f * particle.alphaScale * alphaEnvelope)));

            paint.setColor(particle.color);
            paint.setAlpha(alpha);
            canvas.drawCircle(cx, cy, radius, paint);
        }
    }

    private float wrappedProgress(float value) {
        if (value > 1f) return value - 1f;
        return value;
    }

    private float easeOut(float t) {
        float oneMinus = 1f - t;
        return 1f - (oneMinus * oneMinus * oneMinus);
    }

    private static class SmokeParticle {
        float startOffset;
        float baseX;
        float baseRadius;
        float verticalTravel;
        float horizontalDrift;
        float alphaScale;
        int color;
    }
}
