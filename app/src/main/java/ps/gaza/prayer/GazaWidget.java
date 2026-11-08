package ps.gaza.prayer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.util.TypedValue;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Home-screen widget: next prayer with a live countdown (a Chronometer counts down
 * on its own, so no per-second updates), a live TextClock, North America Fajr, and today's six times.
 *
 * The widget redraws itself at the next event (any prayer, sunrise, first adhan or
 * local midnight) with a non-wakeup alarm, so it costs no battery while the screen is off;
 * updatePeriodMillis (30 min) is only a safety net.
 */
public class GazaWidget extends AppWidgetProvider {

    static final String ACTION_TICK = "ps.gaza.prayer.WIDGET_TICK";

    // Appearance, set from the widget section of SettingsActivity
    static final String KEY_BG_COLOR = "w_bg_color";
    static final String KEY_HEADER_COLOR = "w_header_color";
    static final String KEY_TEXT_COLOR = "w_text_color";
    static final String KEY_TEXT_LIGHT = "w_text_light"; // 1.8 dark/light switch, the default until a text colour is picked
    static final String KEY_TRANSPARENCY = "w_transparency"; // 0 = opaque .. 100 = invisible
    static final String KEY_SIZE = "w_size"; // 0 small, 1 medium, 2 large
    static final String KEY_HEADER_TRANSPARENCY = "w_header_transparency"; // header, NA box and highlight
    static final String KEY_GLASS = "w_glass"; // glass style: orange glowing border, orange highlight
    static final int ACCENT = 0xFFFFA726;
    // Colours set by the "glass" style button (they can still be changed one by one afterwards)
    static final int GLASS_BG = 0xFF0D1117, GLASS_HEADER = 0xFF161B22, GLASS_TEXT = 0xFFE6EDF3, GLASS_TRANSPARENCY = 15;
    static final int BG_DEFAULT = 0xFFFFF8E1;
    static final int HEADER_DEFAULT = 0xFFFFC107;
    static final int SIZE_DEFAULT = 1;
    private static final float[] SCALE = {0.85f, 1f, 1.15f};
    static final int INK = 0xFF3E2723;

    static int textColor(android.content.SharedPreferences p) {
        return p.getInt(KEY_TEXT_COLOR, p.getBoolean(KEY_TEXT_LIGHT, false) ? 0xFFFFFFFF : INK);
    }

    static double luminance(int c) {
        return (0.299 * ((c >> 16) & 255) + 0.587 * ((c >> 8) & 255) + 0.114 * (c & 255)) / 255.0;
    }

    /** Dark ink on light colours, white on dark ones. */
    static int contrast(int c) {
        return luminance(c) < 0.55 ? 0xFFFFFFFF : INK;
    }

    private static int blend(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * t + ((b >> 16) & 255) * (1 - t));
        int g = Math.round(((a >> 8) & 255) * t + ((b >> 8) & 255) * (1 - t));
        int bl = Math.round((a & 255) * t + (b & 255) * (1 - t));
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static final int[] NAME = {R.id.w_name0, R.id.w_name1, R.id.w_name2, R.id.w_name3, R.id.w_name4, R.id.w_name5};
    private static final int[] HI = {R.id.w_hi0, R.id.w_hi1, R.id.w_hi2, R.id.w_hi3, R.id.w_hi4, R.id.w_hi5};
    private static final int[] TIME = {R.id.w_time0, R.id.w_time1, R.id.w_time2, R.id.w_time3, R.id.w_time4, R.id.w_time5};
    private static final int[] CELL = {R.id.w_cell0, R.id.w_cell1, R.id.w_cell2, R.id.w_cell3, R.id.w_cell4, R.id.w_cell5};

    /** Opens the "time left" popup for one event (request codes 10..16 keep the intents apart). */
    private static PendingIntent popupIntent(Context c, int kind) {
        Intent i = new Intent(c, PopupActivity.class)
                .putExtra(PopupActivity.EXTRA_KIND, kind)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return PendingIntent.getActivity(c, 10 + kind, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        updateAll(c);
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        if (ACTION_TICK.equals(intent.getAction())) {
            updateAll(c);
        } else {
            super.onReceive(c, intent);
        }
    }

    @Override
    public void onDisabled(Context c) {
        c.getSystemService(AlarmManager.class).cancel(tickIntent(c));
    }

    private static PendingIntent tickIntent(Context c) {
        Intent i = new Intent(c, GazaWidget.class).setAction(ACTION_TICK);
        return PendingIntent.getBroadcast(c, 2, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, GazaWidget.class));
        if (ids == null || ids.length == 0) return;

        long now = System.currentTimeMillis();
        PrayerCalc.applyTimeMode(c);
        Calendar today = Calendar.getInstance(PrayerCalc.TZ);
        today.setTimeInMillis(now);
        long[] all = Scheduler.todayAndTomorrow(c, today);
        // After Isha the times shown are tomorrow's (dates stay on today until midnight).
        int shift = Scheduler.listShift(all, now);
        Calendar listDay = (Calendar) today.clone();
        listDay.add(Calendar.DAY_OF_MONTH, shift / 6);
        long firstAdhan = Scheduler.firstAdhan(c, all[shift + PrayerCalc.FAJR]);
        int idx = Scheduler.nextIndex(all, now, false);

        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
        android.content.SharedPreferences p = Scheduler.prefs(c);

        // Background colour and transparency: tint the white rounded shape, then fade it.
        int bg = p.getInt(KEY_BG_COLOR, BG_DEFAULT) | 0xFF000000;
        int transparency = p.getInt(KEY_TRANSPARENCY, 0);
        v.setInt(R.id.w_bg, "setColorFilter", bg);
        v.setInt(R.id.w_bg, "setImageAlpha", Math.round(255 * (100 - transparency) / 100f));
        int header = p.getInt(KEY_HEADER_COLOR, HEADER_DEFAULT) | 0xFF000000;
        boolean glass = p.getBoolean(KEY_GLASS, false);
        // Header, North America box and highlight share their own transparency.
        float headerOpacity = (100 - p.getInt(KEY_HEADER_TRANSPARENCY, 0)) / 100f;
        int headerAlpha = Math.round(255 * headerOpacity);
        v.setInt(R.id.w_header_bg, "setColorFilter", header);
        v.setInt(R.id.w_header_bg, "setImageAlpha", headerAlpha);
        v.setViewVisibility(R.id.w_header_glow, glass ? android.view.View.VISIBLE : android.view.View.GONE);
        // Over a faded header the text sits on the widget background, so take the contrast from the blend.
        int headerInk = contrast(blend(header, bg, headerOpacity));
        v.setTextColor(R.id.w_next_name, glass ? ACCENT : headerInk);
        v.setTextColor(R.id.w_countdown, headerInk);
        int box = glass ? 0xFF21262D : 0xFFFFF8E1;
        v.setInt(R.id.w_box_bg, "setColorFilter", box);
        v.setInt(R.id.w_box_bg, "setImageAlpha", headerAlpha);
        int boxInk = contrast(blend(box, blend(header, bg, headerOpacity), headerOpacity));
        v.setTextColor(R.id.w_fajr_na_label, boxInk);
        v.setTextColor(R.id.w_fajr_na, boxInk);
        // Next-prayer highlight: the header colour (orange in glass) at 55 % over the background
        int hiColor = glass ? ACCENT : header;
        int hiAlpha = Math.round(140 * headerOpacity);
        int hiInk = contrast(blend(hiColor, bg, hiAlpha / 255f));
        int ink = textColor(p) | 0xFF000000;
        float k = SCALE[Math.max(0, Math.min(2, p.getInt(KEY_SIZE, SIZE_DEFAULT)))];
        size(v, R.id.w_next_name, 17 * k);
        size(v, R.id.w_countdown, 32 * k);
        size(v, R.id.w_fajr_na_label, 12 * k);
        size(v, R.id.w_fajr_na, 19 * k);
        size(v, R.id.w_date, 13 * k);
        v.setTextColor(R.id.w_date, ink);
        size(v, R.id.w_clock, 20 * k);
        v.setTextColor(R.id.w_clock, ink);
        v.setString(R.id.w_clock, "setTimeZone", PrayerCalc.TZ.getID());

        // Header: next event and countdown
        if (idx >= 0) {
            int which = idx % 6;
            v.setTextViewText(R.id.w_next_name,
                    which == PrayerCalc.SUNRISE ? "الشروق بعد" : "صلاة " + PrayerCalc.NAMES_AR[which] + " بعد");
            long base = SystemClock.elapsedRealtime() + (all[idx] - now);
            v.setChronometer(R.id.w_countdown, base, null, true);
            v.setChronometerCountDown(R.id.w_countdown, true);
        }
        v.setTextViewText(R.id.w_fajr_na, MainActivity.fmt(Scheduler.fajrNorthAmerica(c, listDay)));

        SimpleDateFormat g = new SimpleDateFormat("EEEE d MMMM", new Locale("ar"));
        g.setTimeZone(PrayerCalc.TZ);
        v.setTextViewText(R.id.w_date, g.format(today.getTime()) + "  •  " + PrayerCalc.hijriText(today, Scheduler.hijriOffset(c))
                + "  •  الأذان الأول " + MainActivity.fmt(firstAdhan));

        // Today's times, next one highlighted (after Isha the next is tomorrow, nothing highlighted)
        for (int i = 0; i < 6; i++) {
            v.setTextViewText(TIME[i], MainActivity.fmt(all[shift + i]));
            boolean hi = idx == shift + i;
            v.setViewVisibility(HI[i], hi ? android.view.View.VISIBLE : android.view.View.GONE);
            if (hi) {
                v.setInt(HI[i], "setColorFilter", hiColor);
                v.setInt(HI[i], "setImageAlpha", hiAlpha);
            }
            int col = hi ? hiInk : ink;
            v.setTextColor(NAME[i], col);
            v.setTextColor(TIME[i], col);
            size(v, NAME[i], 15 * k);
            size(v, TIME[i], 18 * k);
        }

        PendingIntent open = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.w_root, open);
        // A tap on a prayer or on the North America box shows how long is left.
        for (int i = 0; i < 6; i++) v.setOnClickPendingIntent(CELL[i], popupIntent(c, i));
        v.setOnClickPendingIntent(R.id.w_na_box, popupIntent(c, TimeLeft.KIND_FAJR_NA));
        m.updateAppWidget(ids, v);

        scheduleTick(c, now, all, firstAdhan, today);
    }

    private static void size(RemoteViews v, int id, float sp) {
        v.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, sp);
    }

    /** Next redraw: the earliest of the next prayer/sunrise, the first adhan, or local midnight. */
    private static void scheduleTick(Context c, long now, long[] all, long firstAdhan, Calendar today) {
        Calendar midnight = (Calendar) today.clone();
        midnight.add(Calendar.DAY_OF_MONTH, 1);
        midnight.set(Calendar.HOUR_OF_DAY, 0);
        midnight.set(Calendar.MINUTE, 0);
        midnight.set(Calendar.SECOND, 0);
        midnight.set(Calendar.MILLISECOND, 0);
        long when = midnight.getTimeInMillis();
        for (long t : all) if (t > now && t < when) when = t;
        if (firstAdhan > now && firstAdhan < when) when = firstAdhan;
        when += 1000; // land just after the event so nextIndex() moves on

        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = tickIntent(c);
        // RTC (non-wakeup): if the screen is off the redraw waits until the device wakes.
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExact(AlarmManager.RTC, when, pi);
        } else {
            am.set(AlarmManager.RTC, when, pi);
        }
    }
}
