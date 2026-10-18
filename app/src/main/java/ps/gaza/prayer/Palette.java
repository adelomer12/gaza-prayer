package ps.gaza.prayer;

import android.content.Context;
import android.content.res.Configuration;

/** App colours for the light (yellow / cream) and dark (gold on near-black) looks. */
final class Palette {

    static final String KEY_THEME = "theme"; // 0 follow the system, 1 light, 2 dark
    static final int INK = 0xFF3E2723;

    final boolean dark;
    final int bg, card, ink, inkSoft, header, headerInk, headerInkSoft, highlight, highlightInk, button, buttonInk, chip;

    private Palette(boolean dark) {
        this.dark = dark;
        if (dark) {
            bg = 0xFF121212;
            card = 0xFF232323;
            ink = 0xFFF1ECE8;
            inkSoft = 0xFFB3AAA4;
            header = 0xFFC9A227;
            headerInk = 0xFF1B1300;
            headerInkSoft = 0xFF4A3A10;
            highlight = 0xFFB08D2C;
            highlightInk = 0xFF141414;
            button = 0xFFC9A227;
            buttonInk = 0xFF1B1300;
            chip = 0xFF2E2E2E;
        } else {
            bg = 0xFFFFF8E1;
            card = 0xFFFFFFFF;
            ink = INK;
            inkSoft = 0xFF6D4C41;
            header = 0xFFFFC107;
            headerInk = INK;
            headerInkSoft = 0xFF6D4C41;
            highlight = 0xFFFFE082;
            highlightInk = INK;
            button = 0xFFFFC107;
            buttonInk = INK;
            chip = 0xFFF5F5F5;
        }
    }

    static boolean isDark(Context c) {
        int mode = Scheduler.prefs(c).getInt(KEY_THEME, 0);
        if (mode == 1) return false;
        if (mode == 2) return true;
        int night = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES;
    }

    static Palette of(Context c) {
        return new Palette(isDark(c));
    }

    static int themeRes(boolean dark) {
        return dark ? R.style.AppTheme_Dark : R.style.AppTheme;
    }
}
