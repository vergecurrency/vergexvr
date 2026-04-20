package com.vergepay.wallet.ui;

import android.os.Build;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.util.Log;
import android.view.DisplayCutout;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;

final class WindowInsetsHelper {
    private static final String TAG = "WindowInsetsHelper";

    private WindowInsetsHelper() { }

    private static int resolveTopInset(View view, WindowInsetsCompat insets) {
        int topInset = insets.getSystemWindowInsetTop();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowInsets rootInsets = view.getRootWindowInsets();
            if (rootInsets != null) {
                DisplayCutout displayCutout = rootInsets.getDisplayCutout();
                if (displayCutout != null) {
                    topInset = Math.max(topInset, displayCutout.getSafeInsetTop());
                }
            }
        }

        return topInset;
    }

    static void applyPaddingInsets(final View view, final boolean applyTopInset,
                                   final boolean applyBottomInset) {
        if (view == null) return;

        final int basePaddingLeft = view.getPaddingLeft();
        final int basePaddingTop = view.getPaddingTop();
        final int basePaddingRight = view.getPaddingRight();
        final int basePaddingBottom = view.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(view, new androidx.core.view.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsetsCompat onApplyWindowInsets(View v, WindowInsetsCompat insets) {
                int topInset = applyTopInset ? resolveTopInset(v, insets) : 0;
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

        ViewCompat.setOnApplyWindowInsetsListener(view, new androidx.core.view.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsetsCompat onApplyWindowInsets(View v, WindowInsetsCompat insets) {
                v.setPadding(
                        basePaddingLeft,
                        basePaddingTop + resolveTopInset(v, insets) + extraTopPaddingPx,
                        basePaddingRight,
                        basePaddingBottom
                );
                return insets;
            }
        });
        ViewCompat.requestApplyInsets(view);
    }

    static void applySystemTopInsetAsPadding(final View view, final int extraTopPaddingPx) {
        if (view == null) return;

        final int basePaddingLeft = view.getPaddingLeft();
        final int basePaddingTop = view.getPaddingTop();
        final int basePaddingRight = view.getPaddingRight();
        final int basePaddingBottom = view.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(view, new androidx.core.view.OnApplyWindowInsetsListener() {
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

    static void applyTopInsetAsMargin(final View view, final int extraTopMarginPx) {
        if (view == null) return;
        if (!(view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) return;

        final ViewGroup.MarginLayoutParams layoutParams =
                (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        final int baseTopMargin = layoutParams.topMargin;

        ViewCompat.setOnApplyWindowInsetsListener(view, new androidx.core.view.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsetsCompat onApplyWindowInsets(View v, WindowInsetsCompat insets) {
                ViewGroup.MarginLayoutParams currentParams =
                        (ViewGroup.MarginLayoutParams) v.getLayoutParams();
                int systemTopInset = insets.getSystemWindowInsetTop();
                int resolvedTopInset = resolveTopInset(v, insets);
                int safeCutoutTopInset = 0;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    WindowInsets rootInsets = v.getRootWindowInsets();
                    if (rootInsets != null) {
                        DisplayCutout displayCutout = rootInsets.getDisplayCutout();
                        if (displayCutout != null) {
                            safeCutoutTopInset = displayCutout.getSafeInsetTop();
                        }
                    }
                }

                currentParams.topMargin = baseTopMargin + resolvedTopInset + extraTopMarginPx;
                v.setLayoutParams(currentParams);
                Log.i(TAG, "applyTopInsetAsMargin viewId=" + v.getId()
                        + " baseTopMargin=" + baseTopMargin
                        + " systemTopInset=" + systemTopInset
                        + " safeCutoutTopInset=" + safeCutoutTopInset
                        + " resolvedTopInset=" + resolvedTopInset
                        + " finalTopMargin=" + currentParams.topMargin);
                return insets;
            }
        });
        ViewCompat.requestApplyInsets(view);
    }
}
