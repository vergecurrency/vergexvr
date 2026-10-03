package com.vergepay.wallet.ui;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceManager;
import android.text.Spannable;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.content.res.AppCompatResources;

import com.vergepay.wallet.R;

/** Applies the user-selected visual theme without changing wallet behavior. */
public final class ThemeManager {
    public static final String PREFS_KEY_VISUAL_THEME = "visual_theme";
    public static final String THEME_DEFAULT = "default";
    public static final String THEME_ARCADE = "arcade";
    public static final String THEME_TERMINAL = "terminal";

    private ThemeManager() {}

    public static String getTheme(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getString(PREFS_KEY_VISUAL_THEME, THEME_DEFAULT);
    }

    public static boolean isArcade(Context context) {
        return THEME_ARCADE.equals(getTheme(context));
    }

    public static boolean isTerminal(Context context) {
        return THEME_TERMINAL.equals(getTheme(context));
    }

    public static void apply(Activity activity, boolean noTitleBar) {
        if (isArcade(activity)) {
            activity.setTheme(noTitleBar ? R.style.AppThemeArcadeNoTitleBar : R.style.AppThemeArcade);
        } else if (isTerminal(activity)) {
            activity.setTheme(noTitleBar ? R.style.AppThemeTerminalNoTitleBar : R.style.AppThemeTerminal);
        }
    }

    public static void decorate(View view, String theme) {
        if (view == null) return;

        if (view.getId() == R.id.wrapper_root || view.getId() == R.id.contents) {
            view.setBackgroundResource(THEME_TERMINAL.equals(theme)
                    ? R.drawable.terminal_screen_bg : R.drawable.arcade_screen_bg);
        } else if (THEME_TERMINAL.equals(theme) && view.getId() == R.id.wrapper_nav_container) {
            view.setBackgroundResource(R.drawable.terminal_panel_bg);
        }

        if (THEME_TERMINAL.equals(theme)) {
            if (hasBackground(view, R.drawable.wallet_screen_bg)) {
                view.setBackgroundResource(R.drawable.terminal_screen_bg);
            } else if (hasBackground(view, R.drawable.list_card_bg)
                    || hasBackground(view, R.drawable.account_nav_bar_bg)
                    || hasBackground(view, R.drawable.account_nav_overflow_bg)) {
                view.setBackgroundResource(R.drawable.terminal_panel_bg);
            } else if (hasBackground(view, R.drawable.coin_list_item_selected_bg)
                    || hasBackground(view, R.drawable.account_nav_item_selected_bg)
                    || hasBackground(view, R.drawable.drawer_item_selected_bg)) {
                view.setBackgroundResource(R.drawable.terminal_selected_bg);
            }
        }

        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            textView.setTypeface(Typeface.MONOSPACE, textView.getTypeface() != null
                    ? textView.getTypeface().getStyle() : Typeface.NORMAL);

            int color = textView.getCurrentTextColor();
            if (THEME_TERMINAL.equals(theme)) {
                if (textView.getText() instanceof Spannable) {
                    Spannable text = (Spannable) textView.getText();
                    for (ForegroundColorSpan span : text.getSpans(
                            0, text.length(), ForegroundColorSpan.class)) {
                        text.removeSpan(span);
                    }
                }
                boolean secondary = color == Color.rgb(185, 173, 216)
                        || color == Color.rgb(137, 125, 167);
                textView.setTextColor(secondary ? Color.rgb(70, 180, 92) : Color.rgb(51, 255, 102));
            } else {
                if (color == Color.rgb(246, 243, 255)) {
                    textView.setTextColor(Color.rgb(223, 255, 0));
                } else if (color == Color.rgb(185, 173, 216)) {
                    textView.setTextColor(Color.rgb(255, 183, 0));
                } else if (color == Color.rgb(137, 125, 167)) {
                    textView.setTextColor(Color.rgb(105, 240, 174));
                } else if (color == Color.rgb(32, 223, 200)) {
                    textView.setTextColor(Color.rgb(0, 229, 255));
                } else if (color == Color.rgb(122, 61, 240)) {
                    textView.setTextColor(Color.rgb(255, 45, 149));
                }
            }
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                decorate(group.getChildAt(i), theme);
            }
        }
    }

    private static boolean hasBackground(View view, int drawableResource) {
        Drawable current = view.getBackground();
        Drawable expected = AppCompatResources.getDrawable(view.getContext(), drawableResource);
        return current != null && expected != null
                && current.getConstantState() != null
                && current.getConstantState().equals(expected.getConstantState());
    }
}
