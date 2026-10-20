package ps.gaza.prayer;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class MainActivity extends Activity {

    private Palette pal;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView gregorian, hijri, clock, nextName, countdown, fajrNaTime, firstAdhanText;
    private final TextView[] rowName = new TextView[6];
    private final TextView[] rowTime = new TextView[6];
    private final LinearLayout[] rows = new LinearLayout[6];
    private long[] all;
    private int dayKey = -1;

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
        setTheme(Palette.themeRes(pal.dark));
        super.onCreate(b);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        setContentView(buildUi());

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        Scheduler.ensureChannel(this);
        Scheduler.scheduleNext(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The look may have been switched in the settings screen.
        if (Palette.isDark(this) != pal.dark) {
            recreate();
            return;
        }
        PrayerCalc.applyTimeMode(this);
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

    private View buildUi() {
        // Fill the screen: the six prayer rows share the height left under the header.
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(pal.bg);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(12), dp(12), dp(12));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -1));

        // Header card
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setGravity(Gravity.CENTER_HORIZONTAL);
        head.setBackground(rounded(pal.header));
        head.setPadding(dp(16), dp(20), dp(16), dp(20));
        TextView title = text(22, pal.headerInk, true);
        title.setText("مواقيت الصلاة - غزة");
        gregorian = text(15, pal.headerInk, false);
        hijri = text(15, pal.headerInkSoft, false);
        clock = text(20, pal.headerInk, true);
        clock.setTextDirection(View.TEXT_DIRECTION_RTL);
        nextName = text(17, pal.headerInk, true);
        nextName.setPadding(0, dp(14), 0, 0);
        countdown = text(40, pal.headerInk, true);
        countdown.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        countdown.setTextDirection(View.TEXT_DIRECTION_LTR);
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        // Title with the settings gear (offsets, notifications and widget appearance live there)
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(title);
        android.widget.ImageView gear = new android.widget.ImageView(this);
        gear.setImageResource(R.drawable.ic_settings);
        gear.setColorFilter(pal.headerInk);
        gear.setPadding(dp(8), dp(8), dp(8), dp(8));
        gear.setContentDescription("الإعدادات");
        gear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new android.content.Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        titleRow.addView(gear, new LinearLayout.LayoutParams(dp(40), dp(40)));
        titles.addView(titleRow);
        titles.addView(gregorian);
        titles.addView(hijri);
        titles.addView(clock);
        top.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));

        // North America Fajr (ISNA 15°), shown for reference next to the title
        LinearLayout na = new LinearLayout(this);
        na.setOrientation(LinearLayout.VERTICAL);
        na.setGravity(Gravity.CENTER_HORIZONTAL);
        na.setBackground(rounded(pal.dark ? pal.card : pal.bg));
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
        LinearLayout.LayoutParams nap = new LinearLayout.LayoutParams(-2, -2);
        nap.setMarginStart(dp(12));
        top.addView(na, nap);

        head.setGravity(Gravity.START);
        head.addView(top, new LinearLayout.LayoutParams(-1, -2));
        head.addView(nextName);
        head.addView(countdown);
        root.addView(head);

        // One card per prayer, stretched to fill the rest of the screen
        for (int i = 0; i < 6; i++) {
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
        int key = (today.get(Calendar.YEAR) * 1000 + today.get(Calendar.DAY_OF_YEAR)) * 10 + shift;
        if (key != dayKey) {
            dayKey = key;
            all = Scheduler.todayAndTomorrow(this, today);
            shift = Scheduler.listShift(all, now);
            Calendar listDay = (Calendar) today.clone();
            listDay.add(Calendar.DAY_OF_MONTH, shift / 6);
            SimpleDateFormat g = new SimpleDateFormat("EEEE d MMMM yyyy", new Locale("ar"));
            g.setTimeZone(PrayerCalc.TZ);
            gregorian.setText(g.format(today.getTime()));
            hijri.setText(PrayerCalc.hijriText(today, Scheduler.hijriOffset(this)));
            for (int i = 0; i < 6; i++) rowTime[i].setText(fmt(all[shift + i]));
            fajrNaTime.setText(fmt(Scheduler.fajrNorthAmerica(this, listDay)));
            firstAdhanText.setText("الأذان الأول " + fmt(Scheduler.firstAdhan(this, all[shift + PrayerCalc.FAJR])));
        }
        int idx = Scheduler.nextIndex(all, now, false);
        for (int i = 0; i < 6; i++) {
            boolean on = idx == shift + i;
            rows[i].setBackground(rounded(on ? pal.highlight : pal.card));
            int col = on ? pal.highlightInk : pal.ink;
            rowName[i].setTextColor(col);
            rowTime[i].setTextColor(col);
            if (i == PrayerCalc.FAJR) firstAdhanText.setTextColor(on ? pal.highlightInk : pal.inkSoft);
        }
        if (idx < 0) return;
        nextName.setText((idx % 6 == PrayerCalc.SUNRISE ? "الشروق بعد" : "صلاة " + PrayerCalc.NAMES_AR[idx % 6] + " بعد"));
        long left = (all[idx] - now) / 1000;
        countdown.setText(String.format(Locale.US, "%02d:%02d:%02d", left / 3600, (left / 60) % 60, left % 60));
    }
}
