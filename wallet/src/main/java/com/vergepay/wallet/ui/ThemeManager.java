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
    public static final String THEME_MIDNIGHT_OLED = "midnight_oled";
    public static final String THEME_GAME_BOY = "game_boy";

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
        } else if (THEME_MIDNIGHT_OLED.equals(getTheme(activity))) {
            activity.setTheme(noTitleBar ? R.style.AppThemeMidnightOledNoTitleBar : R.style.AppThemeMidnightOled);
        } else if (THEME_GAME_BOY.equals(getTheme(activity))) {
            activity.setTheme(noTitleBar ? R.style.AppThemeGameBoyNoTitleBar : R.style.AppThemeGameBoy);
        }
    }

    public static void decorate(View view, String theme) {
        if (view == null) return;

        if (view.getId() == R.id.wrapper_root || view.getId() == R.id.contents
                || view.getId() == R.id.account_root || view.getId() == R.id.balance_root) {
            view.setBackgroundResource(screenBackground(theme));
        } else if (view.getId() == R.id.account_nav_container && !THEME_ARCADE.equals(theme)) {
            view.setBackgroundResource(panelBackground(theme));
        }

        if (!THEME_ARCADE.equals(theme)) {
            if (hasBackground(view, R.drawable.wallet_screen_bg)) {
                view.setBackgroundResource(screenBackground(theme));
            } else if (hasBackground(view, R.drawable.list_card_bg)
                    || hasBackground(view, R.drawable.account_nav_bar_bg)
                    || hasBackground(view, R.drawable.account_nav_overflow_bg)) {
                view.setBackgroundResource(panelBackground(theme));
            } else if (hasBackground(view, R.drawable.coin_list_item_selected_bg)
                    || hasBackground(view, R.drawable.account_nav_item_selected_bg)
                    || hasBackground(view, R.drawable.drawer_item_selected_bg)) {
                view.setBackgroundResource(selectedBackground(theme));
            }
        }

        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            Typeface family = THEME_MIDNIGHT_OLED.equals(theme) ? Typeface.DEFAULT : Typeface.MONOSPACE;
            textView.setTypeface(family, textView.getTypeface() != null
                    ? textView.getTypeface().getStyle() : Typeface.NORMAL);

            int color = textView.getCurrentTextColor();
            if (THEME_TERMINAL.equals(theme)) {
                stripInlineColors(textView);
                boolean secondary = color == Color.rgb(185, 173, 216)
                        || color == Color.rgb(137, 125, 167);
                textView.setTextColor(secondary ? Color.rgb(70, 180, 92) : Color.rgb(51, 255, 102));
            } else if (THEME_GAME_BOY.equals(theme)) {
                stripInlineColors(textView);
                boolean secondary = color == Color.rgb(185, 173, 216)
                        || color == Color.rgb(137, 125, 167);
                textView.setTextColor(secondary ? Color.rgb(48, 98, 48) : Color.rgb(15, 56, 15));
            } else if (THEME_MIDNIGHT_OLED.equals(theme)) {
                stripInlineColors(textView);
                boolean secondary = color == Color.rgb(185, 173, 216)
                        || color == Color.rgb(137, 125, 167);
                textView.setTextColor(secondary ? Color.rgb(142, 142, 147) : Color.WHITE);
            } else if (THEME_ARCADE.equals(theme)) {
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

    public static void applyAccountNavStyle(TextView view, boolean selected) {
        String theme = getTheme(view.getContext());
        if (THEME_DEFAULT.equals(theme) || THEME_ARCADE.equals(theme)) return;

        view.setBackgroundResource(selected ? selectedBackground(theme) : panelBackground(theme));
        if (THEME_GAME_BOY.equals(theme)) {
            view.setTextColor(Color.rgb(15, 56, 15));
        } else if (THEME_TERMINAL.equals(theme)) {
            view.setTextColor(Color.rgb(51, 255, 102));
        } else {
            view.setTextColor(selected ? Color.WHITE : Color.rgb(142, 142, 147));
        }
    }

    private static void stripInlineColors(TextView textView) {
        if (textView.getText() instanceof Spannable) {
                    Spannable text = (Spannable) textView.getText();
                    for (ForegroundColorSpan span : text.getSpans(
                            0, text.length(), ForegroundColorSpan.class)) {
                        text.removeSpan(span);
                    }
        }
    }

    private static int screenBackground(String theme) {
        if (THEME_TERMINAL.equals(theme)) return R.drawable.terminal_screen_bg;
        if (THEME_MIDNIGHT_OLED.equals(theme)) return R.drawable.midnight_oled_screen_bg;
        if (THEME_GAME_BOY.equals(theme)) return R.drawable.game_boy_screen_bg;
        return R.drawable.arcade_screen_bg;
    }

    private static int panelBackground(String theme) {
        if (THEME_TERMINAL.equals(theme)) return R.drawable.terminal_panel_bg;
        if (THEME_MIDNIGHT_OLED.equals(theme)) return R.drawable.midnight_oled_panel_bg;
        if (THEME_GAME_BOY.equals(theme)) return R.drawable.game_boy_panel_bg;
        return R.drawable.arcade_dialog_bg;
    }

    private static int selectedBackground(String theme) {
        if (THEME_TERMINAL.equals(theme)) return R.drawable.terminal_selected_bg;
        if (THEME_MIDNIGHT_OLED.equals(theme)) return R.drawable.midnight_oled_selected_bg;
        if (THEME_GAME_BOY.equals(theme)) return R.drawable.game_boy_selected_bg;
        return R.drawable.drawer_item_selected_bg;
    }

    private static boolean hasBackground(View view, int drawableResource) {
        Drawable current = view.getBackground();
        Drawable expected = AppCompatResources.getDrawable(view.getContext(), drawableResource);
        return current != null && expected != null
                && current.getConstantState() != null
                && current.getConstantState().equals(expected.getConstantState());
    }
}
