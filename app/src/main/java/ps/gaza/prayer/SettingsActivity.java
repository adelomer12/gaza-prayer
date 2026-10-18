package ps.gaza.prayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Settings, opened from the gear on the main screen: a menu of three sections
 * (adhan and notifications, time offsets, look and widget), each shown by the same activity.
 */
public class SettingsActivity extends Activity {

    private static final int YELLOW_DARK = 0xFFFFA000;
    private Palette pal;

    private static final int[] BG_COLORS = {0xFFFFF8E1, 0xFFFFFFFF, 0xFFFFE082, 0xFF757575, 0xFF3E2723, 0xFF000000};
    private static final int[] HEADER_COLORS = {0xFFFFC107, 0xFFFFA000, 0xFF4CAF50, 0xFF1E88E5, 0xFF8E24AA, 0xFF3E2723};
    private static final int[] TEXT_COLORS = {0xFF3E2723, 0xFF000000, 0xFFFFFFFF, 0xFFFFC107};

    private SharedPreferences prefs;
    /** Every colour row, repainted after any change (picking a background also sets the text colour). */
    private final List<ColorRow> colorRows = new ArrayList<ColorRow>();
    /** Segmented button rows, repainted after any change. */
    private final List<Seg> segs = new ArrayList<Seg>();

    static final String EXTRA_SECTION = "section";
    private static final int MENU = 0, ADHAN = 1, TIMES = 2, LOOK = 3, INFO = 4;
    private int section;

    @Override
    protected void onCreate(Bundle b) {
        pal = Palette.of(this);
        setTheme(Palette.themeRes(pal.dark));
        super.onCreate(b);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        prefs = Scheduler.prefs(this);
        section = getIntent().getIntExtra(EXTRA_SECTION, MENU);
        setContentView(buildUi());
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView text(float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable rounded(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(18));
        return g;
    }

    private LinearLayout card(LinearLayout root, String title) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(rounded(pal.card));
        c.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(16);
        root.addView(c, lp);
        if (title != null) {
            TextView h = text(17, pal.ink, true);
            h.setText(title);
            c.addView(h);
        }
        return c;
    }

    private TextView label(String s) {
        TextView t = text(15, pal.ink, false);
        t.setText(s);
        t.setPadding(0, dp(14), 0, dp(6));
        return t;
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(pal.bg);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(root);

        String[] titles = {"الإعدادات", "الأذان والتنبيهات", "تعديل الأوقات", "المظهر والويدجت", "معلومات"};
        root.addView(titleBar(titles[section]));
        if (section == ADHAN) buildAdhan(root);
        else if (section == TIMES) buildTimes(root);
        else if (section == LOOK) buildLook(root);
        else if (section == INFO) buildInfo(root);
        else buildMenu(root);
        repaint();
        return scroll;
    }

    private View titleBar(String t) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(rounded(pal.header));
        bar.setPadding(dp(8), dp(10), dp(16), dp(10));
        TextView back = text(24, pal.headerInk, true);
        back.setText("→"); // RTL: back points right
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        bar.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView title = text(22, pal.headerInk, true);
        title.setText(t);
        bar.addView(title);
        return bar;
    }

    // ---- Menu ----

    private void buildMenu(LinearLayout root) {
        menuItem(root, ADHAN, "الأذان والتنبيهات", "صوت الأذان أو تنبيه أو إيقاف لكل صلاة");
        menuItem(root, TIMES, "تعديل الأوقات", "دقائق لكل صلاة، الأذان الأول، والتاريخ الهجري");
        menuItem(root, LOOK, "المظهر والويدجت", "الوضع الداكن، ألوان الويدجت وحجم الخط");
        menuItem(root, INFO, "معلومات", "عن التطبيق والتواصل مع المطوّر");
    }

    private void menuItem(LinearLayout root, final int target, String title, String sub) {
        LinearLayout c = card(root, null);
        c.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t = text(18, pal.ink, true);
        t.setText(title);
        TextView s = text(14, pal.inkSoft, false);
        s.setText(sub);
        col.addView(t);
        col.addView(s);
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView arrow = text(22, pal.inkSoft, true);
        arrow.setText("‹"); // RTL: forward points left
        row.addView(arrow);
        c.addView(row);
        c.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new android.content.Intent(SettingsActivity.this, SettingsActivity.class)
                        .putExtra(EXTRA_SECTION, target));
            }
        });
    }

    // ---- About ----

    private void buildInfo(LinearLayout root) {
        LinearLayout app = card(root, null);
        app.setGravity(Gravity.CENTER_HORIZONTAL);
        android.widget.ImageView icon = new android.widget.ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher);
        app.addView(icon, new LinearLayout.LayoutParams(dp(88), dp(88)));
        TextView name = text(20, pal.ink, true);
        name.setText(getString(R.string.app_name));
        name.setGravity(Gravity.CENTER);
        app.addView(name);
        TextView ver = text(14, pal.inkSoft, false);
        String v = "";
        try {
            v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        ver.setText("الإصدار " + v);
        ver.setGravity(Gravity.CENTER);
        app.addView(ver);

        LinearLayout dev = card(root, "المطوّر");
        dev.addView(infoRow("الاسم", "عادل حجاج", null));
        dev.addView(infoRow("الجوال", "0599706260",
                new android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:0599706260"))));
        dev.addView(infoRow("GitHub", "github.com/adelomer12",
                new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/adelomer12"))));
    }

    /** Label over value; a tappable row opens the dialer or browser (no permission needed). */
    private View infoRow(String label, String value, final android.content.Intent action) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(0, dp(12), 0, dp(4));
        TextView l = text(13, pal.inkSoft, false);
        l.setText(label);
        TextView val = text(17, action == null ? pal.ink : (pal.dark ? pal.header : 0xFF1565C0), true);
        val.setText(value);
        val.setTextDirection(View.TEXT_DIRECTION_LTR);
        val.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        r.addView(l);
        r.addView(val);
        if (action != null) {
            r.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        startActivity(action);
                    } catch (android.content.ActivityNotFoundException ignored) {
                    }
                }
            });
        }
        return r;
    }

    // ---- Adhan and notifications ----

    private void buildAdhan(LinearLayout root) {
        LinearLayout n = card(root, null);
        Switch notify = new Switch(this);
        notify.setText("تفعيل الأذان والتنبيهات");
        notify.setTextSize(16);
        notify.setTextColor(pal.ink);
        notify.setPadding(0, dp(6), 0, dp(6));
        notify.setChecked(prefs.getBoolean(Scheduler.KEY_NOTIFY, true));
        int[][] st = {{android.R.attr.state_checked}, {}};
        notify.setThumbTintList(new ColorStateList(st, new int[]{pal.button, 0xFFBDBDBD}));
        notify.setTrackTintList(new ColorStateList(st, new int[]{0xFFFFD54F, 0xFFE0E0E0}));
        notify.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton v, boolean on) {
                prefs.edit().putBoolean(Scheduler.KEY_NOTIFY, on).apply();
                Scheduler.scheduleNext(SettingsActivity.this);
            }
        });
        n.addView(notify);

        LinearLayout list = card(root, "لكل صلاة");
        TextView hint = text(13, pal.inkSoft, false);
        hint.setText("أذان: صوت الأذان كاملاً، ويوقفه زر «إيقاف الأذان» في الإشعار.\nتنبيه: صوت إشعار قصير فقط.");
        hint.setPadding(0, dp(4), 0, dp(4));
        list.addView(hint);
        for (int idx : Scheduler.EVENT_ORDER) {
            final int e = idx;
            TextView name = text(16, pal.ink, true);
            name.setText(Scheduler.eventName(e));
            name.setPadding(0, dp(14), 0, dp(6));
            list.addView(name);
            list.addView(segmented(new String[]{"إيقاف", "تنبيه", "أذان"}, new Getter() {
                @Override
                public int get() {
                    return Scheduler.mode(SettingsActivity.this, e);
                }
            }, new Choice() {
                @Override
                public void pick(int i) {
                    prefs.edit().putInt(Scheduler.modeKey(e), i).apply();
                    Scheduler.scheduleNext(SettingsActivity.this);
                    repaint();
                }
            }));
        }

        LinearLayout test = card(root, "تجربة الصوت");
        TextView play = text(16, pal.buttonInk, true);
        play.setText("تشغيل الأذان");
        play.setGravity(Gravity.CENTER);
        play.setPadding(0, dp(10), 0, dp(10));
        play.setBackground(rounded(pal.button));
        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AdhanService.start(SettingsActivity.this, -1);
            }
        });
        TextView stop = text(16, pal.ink, true);
        stop.setText("إيقاف");
        stop.setGravity(Gravity.CENTER);
        stop.setPadding(0, dp(10), 0, dp(10));
        stop.setBackground(rounded(pal.chip));
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AdhanService.stop(SettingsActivity.this);
            }
        });
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.addView(play, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, -2, 1f);
        sp.setMarginStart(dp(8));
        btns.addView(stop, sp);
        test.addView(btns);
        TextView vol = text(13, pal.inkSoft, false);
        vol.setText("يُشغَّل الأذان على صوت المنبّه في الهاتف.");
        vol.setPadding(0, dp(8), 0, 0);
        test.addView(vol);
    }

    // ---- Time offsets ----

    private void buildTimes(LinearLayout root) {
        LinearLayout tz = card(root, "التوقيت الصيفي والشتوي");
        tz.addView(segmented(new String[]{"تلقائي", "شتوي +2", "صيفي +3"}, new Getter() {
            @Override
            public int get() {
                return prefs.getInt(PrayerCalc.KEY_TIME_MODE, 0);
            }
        }, new Choice() {
            @Override
            public void pick(int i) {
                prefs.edit().putInt(PrayerCalc.KEY_TIME_MODE, i).apply();
                Scheduler.scheduleNext(SettingsActivity.this);
                GazaWidget.updateAll(SettingsActivity.this);
                repaint();
            }
        }));
        TextView tzHint = text(13, pal.inkSoft, false);
        tzHint.setText("تلقائي: حسب توقيت فلسطين المسجّل في الجوال.\nاختر شتوي أو صيفي فقط إذا تغيّر الموعد الرسمي ولم يتحدث الجوال، ثم أرجعه إلى تلقائي بعد التحديث.");
        tzHint.setPadding(0, dp(8), 0, 0);
        tz.addView(tzHint);

        // Minute offsets, mirroring the HA "تعديل الأوقات (دقائق)" helpers
        LinearLayout offs = card(root, "تعديل الأوقات (دقائق)");
        offs.addView(offsetRow("الفجر", Scheduler.OFFSET_KEYS[0], Scheduler.OFFSET_DEFAULTS[0]));
        offs.addView(offsetRow("فجر أمريكا الشمالية", Scheduler.OFFSET_FAJR_NA, Scheduler.OFFSET_FAJR_NA_DEFAULT));
        offs.addView(offsetRow("الأذان الأول (قبل الفجر)", Scheduler.KEY_FIRST_ADHAN, Scheduler.FIRST_ADHAN_DEFAULT));
        for (int i = 1; i < 6; i++) {
            offs.addView(offsetRow(PrayerCalc.NAMES_AR[i], Scheduler.OFFSET_KEYS[i], Scheduler.OFFSET_DEFAULTS[i]));
        }
        offs.addView(offsetRow("تصحيح التاريخ الهجري (أيام)", Scheduler.KEY_HIJRI_OFFSET, 0));
    }

    // ---- Look and widget ----

    private void buildLook(LinearLayout root) {
        // Look: follow the phone, or force light / dark
        LinearLayout look = card(root, "المظهر");
        look.addView(segmented(new String[]{"تلقائي", "فاتح", "داكن"}, new Getter() {
            @Override
            public int get() {
                return prefs.getInt(Palette.KEY_THEME, 0);
            }
        }, new Choice() {
            @Override
            public void pick(int i) {
                prefs.edit().putInt(Palette.KEY_THEME, i).apply();
                if (Palette.isDark(SettingsActivity.this) != pal.dark) {
                    recreate();
                } else {
                    repaint();
                }
            }
        }));
        TextView lookHint = text(13, pal.inkSoft, false);
        lookHint.setText("تلقائي: يتبع الوضع الداكن في إعدادات الهاتف.");
        lookHint.setPadding(0, dp(8), 0, 0);
        look.addView(lookHint);

        LinearLayout w = card(root, "الويدجت");
        w.addView(label("لون الخلفية"));
        w.addView(new ColorRow(GazaWidget.KEY_BG_COLOR, GazaWidget.BG_DEFAULT, BG_COLORS, true).view);
        w.addView(label("لون الشريط العلوي"));
        w.addView(new ColorRow(GazaWidget.KEY_HEADER_COLOR, GazaWidget.HEADER_DEFAULT, HEADER_COLORS, false).view);
        w.addView(label("لون الخط"));
        w.addView(new ColorRow(GazaWidget.KEY_TEXT_COLOR, -1, TEXT_COLORS, false).view);

        final TextView tl = label("");
        w.addView(tl);
        SeekBar seek = new SeekBar(this);
        seek.setMax(100);
        seek.setProgress(prefs.getInt(GazaWidget.KEY_TRANSPARENCY, 0));
        seek.setProgressTintList(ColorStateList.valueOf(pal.button));
        seek.setThumbTintList(ColorStateList.valueOf(YELLOW_DARK));
        tl.setText("شفافية الخلفية: " + seek.getProgress() + "%");
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int v, boolean user) {
                tl.setText("شفافية الخلفية: " + v + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {}

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                prefs.edit().putInt(GazaWidget.KEY_TRANSPARENCY, s.getProgress()).apply();
                GazaWidget.updateAll(SettingsActivity.this);
            }
        });
        w.addView(seek);

        w.addView(label("حجم الخط"));
        w.addView(segmented(new String[]{"صغير", "متوسط", "كبير"}, new Getter() {
            @Override
            public int get() {
                return prefs.getInt(GazaWidget.KEY_SIZE, GazaWidget.SIZE_DEFAULT);
            }
        }, new Choice() {
            @Override
            public void pick(int i) {
                prefs.edit().putInt(GazaWidget.KEY_SIZE, i).apply();
                repaint();
            }
        }));

        TextView reset = text(15, pal.inkSoft, true);
        reset.setText("استعادة ألوان الويدجت الافتراضية");
        reset.setGravity(Gravity.CENTER);
        reset.setPadding(0, dp(18), 0, dp(4));
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.edit().remove(GazaWidget.KEY_BG_COLOR).remove(GazaWidget.KEY_HEADER_COLOR)
                        .remove(GazaWidget.KEY_TEXT_COLOR).remove(GazaWidget.KEY_TEXT_LIGHT)
                        .remove(GazaWidget.KEY_TRANSPARENCY).remove(GazaWidget.KEY_SIZE).apply();
                recreate();
                GazaWidget.updateAll(SettingsActivity.this);
            }
        });
        w.addView(reset);
    }

    private interface Choice {
        void pick(int i);
    }

    private interface Getter {
        int get();
    }

    private static final class Seg {
        final TextView[] buttons;
        final Getter current;

        Seg(TextView[] buttons, Getter current) {
            this.buttons = buttons;
            this.current = current;
        }
    }

    /** A row of equal buttons; the selected one is painted by repaint(). */
    private View segmented(String[] names, Getter current, final Choice c) {
        TextView[] out = new TextView[names.length];
        segs.add(new Seg(out, current));
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            TextView btn = text(15, pal.ink, true);
            btn.setText(names[i]);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(0, dp(8), 0, dp(8));
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    c.pick(idx);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i > 0) lp.setMarginStart(dp(8));
            r.addView(btn, lp);
            out[i] = btn;
        }
        return r;
    }

    private View offsetRow(String label, final String key, int def) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(6), 0, dp(6));
        TextView name = text(16, pal.ink, false);
        name.setText(label);
        r.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        final TextView val = text(17, pal.ink, true);
        val.setGravity(Gravity.CENTER);
        val.setTextDirection(View.TEXT_DIRECTION_LTR);
        val.setMinWidth(dp(48));
        final int[] cur = {prefs.getInt(key, def)};
        val.setText(String.format(Locale.US, "%+d", cur[0]));
        for (final int step : new int[]{1, -1}) {
            TextView b = text(20, pal.buttonInk, true);
            b.setText(step > 0 ? "+" : "−");
            b.setGravity(Gravity.CENTER);
            b.setBackground(rounded(pal.button));
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cur[0] += step;
                    prefs.edit().putInt(key, cur[0]).apply();
                    val.setText(String.format(Locale.US, "%+d", cur[0]));
                    Scheduler.scheduleNext(SettingsActivity.this);
                    GazaWidget.updateAll(SettingsActivity.this);
                }
            });
            r.addView(b, new LinearLayout.LayoutParams(dp(40), dp(40)));
            if (step > 0) r.addView(val);
        }
        return r;
    }

    private void repaint() {
        for (ColorRow r : colorRows) r.paint();
        for (Seg g : segs) {
            int sel = g.current.get();
            for (int i = 0; i < g.buttons.length; i++) {
                g.buttons[i].setBackground(rounded(i == sel ? pal.button : pal.chip));
                g.buttons[i].setTextColor(i == sel ? pal.buttonInk : pal.ink);
            }
        }
        if (section == LOOK) GazaWidget.updateAll(this);
    }

    /** A row of preset swatches plus a last "custom" swatch that opens the colour picker. */
    private final class ColorRow {
        final String key;
        final int def;
        final int[] presets;
        final boolean setsText;
        final View[] swatches;
        final TextView custom;
        final View view;

        ColorRow(String key, int def, int[] presets, boolean setsText) {
            this.key = key;
            this.def = def;
            this.presets = presets;
            this.setsText = setsText;
            swatches = new View[presets.length];
            LinearLayout row = new LinearLayout(SettingsActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(4), 0, dp(4));
            for (int i = 0; i < presets.length; i++) {
                final int color = presets[i];
                View s = new View(SettingsActivity.this);
                s.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        pick(color);
                    }
                });
                row.addView(s, swatchParams());
                swatches[i] = s;
            }
            custom = text(20, pal.ink, true);
            custom.setText("+");
            custom.setGravity(Gravity.CENTER);
            custom.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showPicker(current(), new Picked() {
                        @Override
                        public void color(int c) {
                            pick(c);
                        }
                    });
                }
            });
            row.addView(custom, swatchParams());
            HorizontalScrollView h = new HorizontalScrollView(SettingsActivity.this);
            h.setHorizontalScrollBarEnabled(false);
            h.addView(row);
            view = h;
            colorRows.add(this);
        }

        private LinearLayout.LayoutParams swatchParams() {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
            lp.setMarginEnd(dp(10));
            return lp;
        }

        int current() {
            return key.equals(GazaWidget.KEY_TEXT_COLOR) ? GazaWidget.textColor(prefs) : prefs.getInt(key, def);
        }

        void pick(int color) {
            SharedPreferences.Editor e = prefs.edit().putInt(key, color | 0xFF000000);
            // A new background also picks a readable text colour; it can be changed afterwards.
            if (setsText) e.putInt(GazaWidget.KEY_TEXT_COLOR, GazaWidget.contrast(color));
            e.apply();
            repaint();
        }

        void paint() {
            int cur = current() | 0xFF000000;
            boolean preset = false;
            for (int i = 0; i < presets.length; i++) {
                boolean on = presets[i] == cur;
                preset |= on;
                swatches[i].setBackground(circle(presets[i], on));
            }
            // The custom swatch shows the chosen colour when it is not one of the presets.
            if (preset) {
                custom.setBackground(circle(pal.chip, false));
                custom.setTextColor(pal.ink);
            } else {
                custom.setBackground(circle(cur, true));
                custom.setTextColor(GazaWidget.contrast(cur));
            }
        }
    }

    private GradientDrawable circle(int color, boolean selected) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        g.setStroke(dp(selected ? 4 : 1), selected ? YELLOW_DARK : 0xFFBDBDBD);
        return g;
    }

    private interface Picked {
        void color(int c);
    }

    /** HSV colour picker: preview, hue/saturation/brightness sliders and a hex field. */
    private void showPicker(int start, final Picked done) {
        final float[] hsv = new float[3];
        Color.colorToHSV(start, hsv);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(12), dp(20), dp(4));

        final View preview = new View(this);
        box.addView(preview, new LinearLayout.LayoutParams(-1, dp(56)));
        final EditText hex = new EditText(this);
        hex.setTextDirection(View.TEXT_DIRECTION_LTR);
        hex.setGravity(Gravity.CENTER);
        hex.setSingleLine(true);
        hex.setFilters(new InputFilter[]{new InputFilter.LengthFilter(7)});
        box.addView(hex);

        final SeekBar[] bars = new SeekBar[3];
        final boolean[] fromHex = {false};
        final Runnable update = new Runnable() {
            @Override
            public void run() {
                int c = Color.HSVToColor(hsv);
                preview.setBackground(rounded(c));
                if (!fromHex[0]) hex.setText(String.format(Locale.US, "#%06X", c & 0xFFFFFF));
            }
        };
        String[] names = {"اللون", "التشبع", "السطوع"};
        int[] max = {360, 100, 100};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView l = text(14, pal.ink, false);
            l.setText(names[i]);
            l.setPadding(0, dp(10), 0, 0);
            box.addView(l);
            SeekBar s = new SeekBar(this);
            s.setMax(max[i]);
            s.setProgress(Math.round(i == 0 ? hsv[0] : hsv[i] * 100));
            s.setProgressTintList(ColorStateList.valueOf(pal.button));
            s.setThumbTintList(ColorStateList.valueOf(YELLOW_DARK));
            s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar sb, int v, boolean user) {
                    if (!user) return;
                    hsv[idx] = idx == 0 ? v : v / 100f;
                    update.run();
                }

                @Override
                public void onStartTrackingTouch(SeekBar sb) {}

                @Override
                public void onStopTrackingTouch(SeekBar sb) {}
            });
            bars[i] = s;
            box.addView(s);
        }
        hex.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable e) {
                String t = e.toString().trim();
                if (!t.matches("#?[0-9a-fA-F]{6}") || !hex.hasFocus()) return;
                int c = 0xFF000000 | Integer.parseInt(t.replace("#", ""), 16);
                Color.colorToHSV(c, hsv);
                bars[0].setProgress(Math.round(hsv[0]));
                bars[1].setProgress(Math.round(hsv[1] * 100));
                bars[2].setProgress(Math.round(hsv[2] * 100));
                fromHex[0] = true;
                update.run();
                fromHex[0] = false;
            }
        });
        update.run();

        new AlertDialog.Builder(this)
                .setTitle("لون مخصص")
                .setView(box)
                .setPositiveButton("تم", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        done.color(Color.HSVToColor(hsv));
                    }
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }
}
