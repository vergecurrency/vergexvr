package com.vergepay.wallet.ui;

import android.support.v4.view.ViewCompat;
import android.support.v4.view.WindowInsetsCompat;
import android.view.View;

final class WindowInsetsHelper {
    private WindowInsetsHelper() { }

    static void applyPaddingInsets(final View view, final boolean applyTopInset,
                                   final boolean applyBottomInset) {
        if (view == null) return;

        final int basePaddingLeft = view.getPaddingLeft();
        final int basePaddingTop = view.getPaddingTop();
        final int basePaddingRight = view.getPaddingRight();
        final int basePaddingBottom = view.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(view, new android.support.v4.view.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsetsCompat onApplyWindowInsets(View v, WindowInsetsCompat insets) {
                int topInset = applyTopInset ? insets.getSystemWindowInsetTop() : 0;
                int bottomInset = applyBottomInset ? insets.getSystemWindowInsetBottom() : 0;

                v.setPadding(
                        basePaddingLeft + insets.getSystemWindowInsetLeft(),
                        basePaddingTop + topInset,
                        basePaddingRight + insets.getSystemWindowInsetRight(),
                        basePaddingBottom + bottomInset
                );
                return insets;
            }
        });
        ViewCompat.requestApplyInsets(view);
    }

    static void applyTopInsetAsPadding(final View view, final int extraTopPaddingPx) {
        if (view == null) return;

        final int basePaddingLeft = view.getPaddingLeft();
        final int basePaddingTop = view.getPaddingTop();
        final int basePaddingRight = view.getPaddingRight();
        final int basePaddingBottom = view.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(view, new android.support.v4.view.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsetsCompat onApplyWindowInsets(View v, WindowInsetsCompat insets) {
                v.setPadding(
                        basePaddingLeft,
                        basePaddingTop + insets.getSystemWindowInsetTop() + extraTopPaddingPx,
                        basePaddingRight,
                        basePaddingBottom
                );
                return insets;
            }
        });
        ViewCompat.requestApplyInsets(view);
    }
}
