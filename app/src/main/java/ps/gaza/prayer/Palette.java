package ps.gaza.prayer;

import android.content.Context;
import android.content.res.Configuration;

/**
 * App colours: light (yellow / cream), dark (gold on near-black) and glass
 * (dark glass cards with thin borders and an orange glowing hero card, like a server dashboard).
 */
final class Palette {

    static final String KEY_THEME = "theme"; // 0 follow the system, 1 light, 2 dark, 3 glass
    static final int INK = 0xFF3E2723;

    static final int LIGHT = 0, DARK = 1, GLASS = 2;

    final int style;
    final boolean dark, glass;
    final int bg, card, ink, inkSoft, header, headerInk, headerInkSoft, highlight, highlightInk, button, buttonInk, chip;
    /** Card border (glass only), accent colour and the top/bottom of the card gradient. */
    final int stroke, accent, cardTop, cardBottom;

    private Palette(int style) {
        this.style = style;
        dark = style != LIGHT;
        glass = style == GLASS;
        if (glass) {
            bg = 0xFF0B0E13;
            card = 0xFF161B22;
            ink = 0xFFE6EDF3;
            inkSoft = 0xFF8B949E;
            header = 0xFF161B22;
            headerInk = 0xFFF0F6FC;
            headerInkSoft = 0xFF9DA7B3;
            highlight = 0xFF2A2012;
            highlightInk = 0xFFFFB74D;
            button = 0xFFFFA726;
            buttonInk = 0xFF1B1300;
            chip = 0xFF21262D;
            stroke = 0x2EFFFFFF;
            accent = 0xFFFFA726;
            cardTop = 0xFF1D232C;
            cardBottom = 0xFF13171D;
        } else if (dark) {
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
            stroke = 0;
            accent = 0xFF1B1300;
            cardTop = card;
            cardBottom = card;
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
            stroke = 0;
            accent = INK;
            cardTop = card;
            cardBottom = card;
        }
    }

    static int style(Context c) {
        int mode = Scheduler.prefs(c).getInt(KEY_THEME, 0);
        if (mode == 1) return LIGHT;
        if (mode == 2) return DARK;
        if (mode == 3) return GLASS;
        int night = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES ? DARK : LIGHT;
    }

    static boolean isDark(Context c) {
        return style(c) != LIGHT;
    }

    static Palette of(Context c) {
        return new Palette(style(c));
    }

    static int themeRes(int style) {
        if (style == GLASS) return R.style.AppTheme_Glass;
        return style == DARK ? R.style.AppTheme_Dark : R.style.AppTheme;
    }
}
