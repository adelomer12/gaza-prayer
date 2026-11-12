package ps.gaza.prayer;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class MainActivity extends Activity {

    private Palette pal;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView gregorian, hijri, clock, nextName, countdown, fajrNaTime, firstAdhanText, backToday;
    private ProgressBar progress;
    private final TextView[] rowName = new TextView[6];
    private final TextView[] rowTime = new TextView[6];
    private final LinearLayout[] rows = new LinearLayout[6];
    private long[] all;
    private int dayKey = -1;
    /** Days away from the list day, changed with the header arrows or a sideways swipe. */
    private int dayOffset = 0;
    /** Set while the settings screen is open, so coming back from it keeps the shown day. */
    private boolean inSettings = false;
    /** The day whose times are on screen (for the popups). */
    private Calendar shownDay;
    private GestureDetector swipe;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        PrayerCalc.applyTimeMode(this);
        pal = Palette.of(this);
        setTheme(Palette.themeRes(pal.style));
        super.onCreate(b);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        setContentView(buildUi());
        swipe = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || e2 == null) return false;
                float dx = e2.getX() - e1.getX();
                if (Math.abs(dx) < dp(80) || Math.abs(vx) < Math.abs(vy) * 1.5f) return false;
                // RTL: dragging the page to the right brings in the next day, to the left the previous one.
                changeDay(dx > 0 ? 1 : -1);
                return true;
            }
        });

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        Scheduler.ensureChannel(this);
        Scheduler.scheduleNext(this);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (swipe != null && swipe.onTouchEvent(ev)) {
            // Cancel the touch underneath so a swipe does not also count as a tap on a row.
            MotionEvent cancel = MotionEvent.obtain(ev);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            super.dispatchTouchEvent(cancel);
            cancel.recycle();
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The look may have been switched in the settings screen.
        if (Palette.style(this) != pal.style) {
            recreate();
            return;
        }
        PrayerCalc.applyTimeMode(this);
        // Each time the app is opened again it shows today, so a day browsed earlier (or a
        // sideways swipe by mistake) never leaves it on another date without the highlight.
        // Coming back from the settings screen keeps the day that was shown.
        if (!inSettings) dayOffset = 0; // the tick below redraws (dayKey = -1)
        inSettings = false;
        dayKey = -1;
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
        GazaWidget.updateAll(this);
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

    /** A prayer card: flat in light/dark; in glass a gradient with a thin border, orange when it is next. */
    private Drawable cardBg(boolean on) {
        if (!pal.glass) return rounded(on ? pal.highlight : pal.card);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                on ? new int[]{0xFF33260F, 0xFF1C170E} : new int[]{pal.cardTop, pal.cardBottom});
        g.setCornerRadius(dp(18));
        g.setStroke(dp(on ? 2 : 1), on ? pal.accent : pal.stroke);
        return g;
    }

    /** Glass hero card: dark gradient with an orange border and a soft glow around it. */
    private Drawable heroBg() {
        if (!pal.glass) return rounded(pal.header);
        GradientDrawable outer = new GradientDrawable();
        outer.setCornerRadius(dp(24));
        outer.setStroke(dp(3), 0x26FFA726);
        GradientDrawable mid = new GradientDrawable();
        mid.setCornerRadius(dp(22));
        mid.setStroke(dp(2), 0x55FFA726);
        GradientDrawable card = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF20262F, 0xFF12161C});
        card.setCornerRadius(dp(20));
        card.setStroke(dp(2), pal.accent);
        LayerDrawable l = new LayerDrawable(new Drawable[]{outer, mid, card});
        l.setLayerInset(1, dp(2), dp(2), dp(2), dp(2));
        l.setLayerInset(2, dp(4), dp(4), dp(4), dp(4));
        return l;
    }

    private TextView arrow(String glyph, final int step, String desc) {
        TextView a = text(30, pal.glass ? pal.accent : pal.headerInk, true);
        a.setText(glyph);
        a.setGravity(Gravity.CENTER);
        a.setContentDescription(desc);
        a.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeDay(step);
            }
        });
        return a;
    }

    private void changeDay(int step) {
        dayOffset += step;
        dayKey = -1;
        refresh();
    }

    private void popup(int kind) {
        if (shownDay == null) return;
        int[] kinds = kind == TimeLeft.KIND_FAJR_NA ? new int[]{kind} : TimeLeft.group(kind);
        TimeLeft.show(this, pal, kinds, shownDay, null);
    }

    private View buildUi() {
        // Fill the screen: the six prayer rows share the height left under the header.
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(pal.bg);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(12), dp(12), dp(12));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -1));

        // Header card: [previous day] content [next day]. RTL: the first child is on the right.
        LinearLayout headOuter = new LinearLayout(this);
        headOuter.setOrientation(LinearLayout.HORIZONTAL);
        headOuter.setGravity(Gravity.CENTER_VERTICAL);
        headOuter.setBackground(heroBg());
        int edge = pal.glass ? 6 : 2;
        headOuter.setPadding(dp(edge), dp(16 + edge), dp(edge), dp(16 + edge));
        headOuter.addView(arrow("›", -1, "اليوم السابق"), new LinearLayout.LayoutParams(dp(30), -1));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(21, pal.headerInk, true);
        title.setText("مواقيت الصلاة - غزة");
        gregorian = text(15, pal.headerInk, false);
        hijri = text(15, pal.headerInkSoft, false);
        clock = text(20, pal.headerInk, true);
        clock.setTextDirection(View.TEXT_DIRECTION_RTL);
        backToday = text(13, pal.glass ? pal.buttonInk : pal.header, true);
        backToday.setText("↺ العودة إلى اليوم");
        backToday.setPadding(dp(10), dp(3), dp(10), dp(3));
        backToday.setBackground(rounded(pal.glass ? pal.accent : pal.headerInk));
        backToday.setVisibility(View.GONE);
        backToday.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeDay(-dayOffset);
            }
        });
        nextName = text(17, pal.glass ? pal.accent : pal.headerInk, true);
        nextName.setPadding(0, dp(12), 0, 0);
        countdown = text(40, pal.headerInk, true);
        countdown.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        countdown.setTextDirection(View.TEXT_DIRECTION_LTR);
        countdown.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);

        // Header layout (RTL, the first child is on the right):
        //   [title ............................ gear]
        //   [gregorian / hijri / clock ...... NA box]
        //   next prayer name, countdown, progress
        // The title is weighted (width 0) so the fixed-size gear is measured first and keeps its
        // 40 dp at any system font size; a long title wraps instead of pushing the gear out.
        android.widget.ImageView gear = new android.widget.ImageView(this);
        gear.setImageResource(R.drawable.ic_settings);
        gear.setColorFilter(pal.glass ? pal.inkSoft : pal.headerInk);
        gear.setPadding(dp(8), dp(8), dp(8), dp(8));
        gear.setContentDescription("الإعدادات");
        gear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                inSettings = true;
                startActivity(new android.content.Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(dp(40), dp(40));
        gp.setMarginStart(dp(8));
        gp.setMarginEnd(-dp(8)); // icon (not its touch padding) lines up with the NA box edge
        titleRow.addView(gear, gp);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(gregorian);
        titles.addView(hijri);
        titles.addView(clock);
        LinearLayout.LayoutParams btp = new LinearLayout.LayoutParams(-2, -2);
        btp.topMargin = dp(4);
        titles.addView(backToday, btp);
        top.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));

        // North America Fajr (ISNA 15°), beside the dates and clock
        LinearLayout na = new LinearLayout(this);
        na.setOrientation(LinearLayout.VERTICAL);
        na.setGravity(Gravity.CENTER_HORIZONTAL);
        if (pal.glass) {
            GradientDrawable nb = rounded(pal.chip);
            nb.setStroke(dp(1), pal.stroke);
            na.setBackground(nb);
        } else {
            na.setBackground(rounded(pal.dark ? pal.card : pal.bg));
        }
        na.setPadding(dp(12), dp(8), dp(12), dp(8));
        TextView naLabel = text(12, pal.inkSoft, true);
        naLabel.setGravity(Gravity.CENTER);
        naLabel.setText("فجر أمريكا\nالشمالية");
        fajrNaTime = text(20, pal.ink, true);
        fajrNaTime.setTextDirection(View.TEXT_DIRECTION_LTR);
        TextView naAngle = text(11, pal.inkSoft, false);
        naAngle.setText("ISNA 15°");
        naAngle.setTextDirection(View.TEXT_DIRECTION_LTR);
        na.addView(naLabel);
        na.addView(fajrNaTime);
        na.addView(naAngle);
        na.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                popup(TimeLeft.KIND_FAJR_NA);
            }
        });
        LinearLayout.LayoutParams nap = new LinearLayout.LayoutParams(-2, -2);
        nap.setMarginStart(dp(10));
        top.addView(na, nap);

        head.addView(titleRow, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = dp(2);
        head.addView(top, tp);
        head.addView(nextName);
        head.addView(countdown, new LinearLayout.LayoutParams(-1, -2));

        // Time elapsed between the previous event and the next one, like a load bar.
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setProgressTintList(ColorStateList.valueOf(pal.glass ? pal.accent : pal.headerInk));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(pal.glass ? 0xFF2D333B : pal.headerInkSoft));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, dp(6));
        pp.topMargin = dp(6);
        head.addView(progress, pp);

        headOuter.addView(head, new LinearLayout.LayoutParams(0, -2, 1f));
        headOuter.addView(arrow("‹", 1, "اليوم التالي"), new LinearLayout.LayoutParams(dp(30), -1));
        root.addView(headOuter);

        // One card per prayer, stretched to fill the rest of the screen; a tap shows the time left.
        for (int i = 0; i < 6; i++) {
            final int kind = i;
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(dp(20), dp(6), dp(20), dp(6));
            r.setMinimumHeight(dp(60));
            rowName[i] = text(22, pal.ink, true);
            rowName[i].setText(PrayerCalc.NAMES_AR[i]);
            rowTime[i] = text(22, pal.ink, true);
            rowTime[i].setTextDirection(View.TEXT_DIRECTION_LTR);
            if (i == PrayerCalc.FAJR) {
                LinearLayout col = new LinearLayout(this);
                col.setOrientation(LinearLayout.VERTICAL);
                col.addView(rowName[i]);
                firstAdhanText = text(14, pal.inkSoft, false);
                col.addView(firstAdhanText);
                r.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
            } else {
                r.addView(rowName[i], new LinearLayout.LayoutParams(0, -2, 1f));
            }
            r.addView(rowTime[i]);
            r.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    popup(kind);
                }
            });
            rows[i] = r;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1f);
            lp.topMargin = dp(10);
            root.addView(r, lp);
        }

        return scroll;
    }

    static String fmt(long t) {
        SimpleDateFormat f = new SimpleDateFormat("h:mm", Locale.US);
        f.setTimeZone(PrayerCalc.TZ);
        Calendar c = Calendar.getInstance(PrayerCalc.TZ);
        c.setTimeInMillis(t);
        return f.format(c.getTime()) + (c.get(Calendar.AM_PM) == Calendar.AM ? " ص" : " م");
    }

    /** Current time with seconds, in the app's time zone (follows the summer/winter setting). */
    static String clockText(long t) {
        SimpleDateFormat f = new SimpleDateFormat("h:mm:ss", Locale.US);
        f.setTimeZone(PrayerCalc.TZ);
        Calendar c = Calendar.getInstance(PrayerCalc.TZ);
        c.setTimeInMillis(t);
        return f.format(c.getTime()) + (c.get(Calendar.AM_PM) == Calendar.AM ? " ص" : " م");
    }

    private void refresh() {
        long now = System.currentTimeMillis();
        clock.setText(clockText(now));
        Calendar today = Calendar.getInstance(PrayerCalc.TZ);
        today.setTimeInMillis(now);
        if (all == null || dayKey == -1) all = Scheduler.todayAndTomorrow(this, today);
        // After Isha the list moves on to tomorrow's times (dates stay on today until midnight).
        int shift = Scheduler.listShift(all, now);
        int key = ((today.get(Calendar.YEAR) % 100 * 1000 + today.get(Calendar.DAY_OF_YEAR)) * 10 + shift) * 1000
                + (dayOffset + 500);
        if (key != dayKey) {
            dayKey = key;
            all = Scheduler.todayAndTomorrow(this, today);
            shift = Scheduler.listShift(all, now);
            Calendar listDay = (Calendar) today.clone();
            listDay.add(Calendar.DAY_OF_MONTH, shift / 6);
            long[] shown;
            Calendar dateDay;
            if (dayOffset == 0) {
                shown = new long[6];
                System.arraycopy(all, shift, shown, 0, 6);
                shownDay = listDay;
                dateDay = today;
            } else {
                shownDay = (Calendar) listDay.clone();
                shownDay.add(Calendar.DAY_OF_MONTH, dayOffset);
                shown = Scheduler.dayTimes(this, shownDay);
                dateDay = shownDay;
            }
            SimpleDateFormat g = new SimpleDateFormat("EEEE d MMMM yyyy", new Locale("ar"));
            g.setTimeZone(PrayerCalc.TZ);
            gregorian.setText(g.format(dateDay.getTime()));
            hijri.setText(PrayerCalc.hijriText(dateDay, Scheduler.hijriOffset(this)));
            for (int i = 0; i < 6; i++) rowTime[i].setText(fmt(shown[i]));
            fajrNaTime.setText(fmt(Scheduler.fajrNorthAmerica(this, shownDay)));
            firstAdhanText.setText("الأذان الأول " + fmt(Scheduler.firstAdhan(this, shown[PrayerCalc.FAJR])));
            backToday.setVisibility(dayOffset == 0 ? View.GONE : View.VISIBLE);
        }
        int idx = Scheduler.nextIndex(all, now, false);
        for (int i = 0; i < 6; i++) {
            // Another day is on screen: nothing is "next" there.
            boolean on = dayOffset == 0 && idx == shift + i;
            rows[i].setBackground(cardBg(on));
            int col = on ? pal.highlightInk : pal.ink;
            rowName[i].setTextColor(col);
            rowTime[i].setTextColor(col);
            if (i == PrayerCalc.FAJR) firstAdhanText.setTextColor(on ? pal.highlightInk : pal.inkSoft);
        }
        if (idx < 0) return;
        nextName.setText((idx % 6 == PrayerCalc.SUNRISE ? "الشروق بعد" : "صلاة " + PrayerCalc.NAMES_AR[idx % 6] + " بعد"));
        long left = (all[idx] - now) / 1000;
        countdown.setText(String.format(Locale.US, "%02d:%02d:%02d", left / 3600, (left / 60) % 60, left % 60));

        // Previous event: the one before in the list, or yesterday's Isha before today's Fajr.
        long prev;
        if (idx > 0) {
            prev = all[idx - 1];
        } else {
            Calendar y = (Calendar) today.clone();
            y.add(Calendar.DAY_OF_MONTH, -1);
            prev = Scheduler.dayTimes(this, y)[PrayerCalc.ISHA];
        }
        long span = all[idx] - prev;
        progress.setProgress(span <= 0 ? 0 : (int) Math.max(0, Math.min(1000, (now - prev) * 1000 / span)));
    }
}
