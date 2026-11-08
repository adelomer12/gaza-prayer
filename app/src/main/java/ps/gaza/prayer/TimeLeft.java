package ps.gaza.prayer;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * "Time left" popup, opened by tapping a prayer row, the North America Fajr box
 * (main screen) or a prayer cell of the widget (through PopupActivity).
 *
 * Event kinds: 0..5 are the six times of PrayerCalc, then North America Fajr and the first adhan.
 */
final class TimeLeft {

    static final int KIND_FAJR_NA = 6, KIND_FIRST_ADHAN = 7;

    private TimeLeft() {}

    static String name(int kind) {
        if (kind == KIND_FAJR_NA) return "فجر أمريكا الشمالية";
        if (kind == KIND_FIRST_ADHAN) return "الأذان الأول";
        return PrayerCalc.NAMES_AR[kind];
    }

    /** Kinds shown together when a row is tapped: Fajr also shows the first adhan. */
    static int[] group(int kind) {
        if (kind == PrayerCalc.FAJR) return new int[]{PrayerCalc.FAJR, KIND_FIRST_ADHAN};
        return new int[]{kind};
    }

    /** The event's time on a given day, with the user's minute offsets applied. */
    static long timeOn(Context c, int kind, Calendar day) {
        if (kind == KIND_FAJR_NA) return Scheduler.fajrNorthAmerica(c, day);
        long[] t = Scheduler.dayTimes(c, day);
        if (kind == KIND_FIRST_ADHAN) return Scheduler.firstAdhan(c, t[PrayerCalc.FAJR]);
        return t[kind];
    }

    /** The next time this event comes after now (today, tomorrow or the day after). */
    static long nextOccurrence(Context c, int kind, long now) {
        Calendar d = Calendar.getInstance(PrayerCalc.TZ);
        d.setTimeInMillis(now);
        for (int i = 0; i < 3; i++) {
            long t = timeOn(c, kind, d);
            if (t > now) return t;
            d.add(Calendar.DAY_OF_MONTH, 1);
        }
        return -1;
    }

    /** hh:mm:ss, with whole days in front when the gap is a day or more. */
    static String span(long ms) {
        long s = Math.max(0, ms / 1000);
        long days = s / 86400;
        s %= 86400;
        String hms = String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
        if (days == 0) return hms;
        return (days == 1 ? "يوم و " : days == 2 ? "يومان و " : days + " أيام و ") + hms;
    }

    private static int dayNumber(long t) {
        Calendar c = Calendar.getInstance(PrayerCalc.TZ);
        c.setTimeInMillis(t);
        return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR);
    }

    /** "اليوم" / "غداً" / "أمس", otherwise the weekday and date. */
    static String dayLabel(long t, long now) {
        Calendar a = Calendar.getInstance(PrayerCalc.TZ);
        a.setTimeInMillis(now);
        Calendar b = (Calendar) a.clone();
        b.add(Calendar.DAY_OF_MONTH, 1);
        Calendar y = (Calendar) a.clone();
        y.add(Calendar.DAY_OF_MONTH, -1);
        int d = dayNumber(t);
        if (d == dayNumber(a.getTimeInMillis())) return "اليوم";
        if (d == dayNumber(b.getTimeInMillis())) return "غداً";
        if (d == dayNumber(y.getTimeInMillis())) return "أمس";
        SimpleDateFormat f = new SimpleDateFormat("EEEE d MMMM", new Locale("ar"));
        f.setTimeZone(PrayerCalc.TZ);
        return f.format(new java.util.Date(t));
    }

    private static final class Entry {
        int kind;
        long at;
        TextView label, big, sub;
    }

    /**
     * Shows the popup for the given kinds on the given day (the day whose times are on screen).
     * A time that has already passed shows how long ago it was and the wait for the next one.
     */
    static Dialog show(final Activity a, final Palette pal, int[] kinds, Calendar day, final Runnable onDismiss) {
        final float density = a.getResources().getDisplayMetrics().density;
        final Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        int pad = Math.round(20 * density);
        box.setPadding(pad, pad, pad, Math.round(16 * density));
        GradientDrawable bg;
        if (pal.glass) {
            bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{pal.cardTop, pal.cardBottom});
            bg.setStroke(Math.round(2 * density), pal.accent);
        } else {
            bg = new GradientDrawable();
            bg.setColor(pal.dark ? pal.card : pal.bg);
            bg.setStroke(Math.round(2 * density), pal.header);
        }
        bg.setCornerRadius(22 * density);
        box.setBackground(bg);

        final List<Entry> entries = new ArrayList<Entry>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < kinds.length; i++) {
            Entry e = new Entry();
            e.kind = kinds[i];
            e.at = timeOn(a, kinds[i], day);
            if (i > 0) {
                View line = new View(a);
                line.setBackgroundColor(pal.glass ? pal.stroke : (pal.inkSoft & 0x40FFFFFF));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Math.max(1, Math.round(density)));
                lp.topMargin = lp.bottomMargin = Math.round(14 * density);
                box.addView(line, lp);
            }
            TextView title = text(a, i == 0 ? 24 : 19, pal.glass ? pal.accent : pal.ink, true);
            title.setText(name(e.kind));
            box.addView(title);
            TextView when = text(a, 15, pal.inkSoft, false);
            when.setText("الموعد: " + MainActivity.fmt(e.at) + "  •  " + dayLabel(e.at, now));
            box.addView(when);
            e.label = text(a, 14, pal.inkSoft, false);
            e.label.setPadding(0, Math.round(10 * density), 0, 0);
            box.addView(e.label);
            e.big = text(a, i == 0 ? 38 : 30, pal.ink, true);
            e.big.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            e.big.setTextDirection(View.TEXT_DIRECTION_RTL);
            box.addView(e.big);
            e.sub = text(a, 14, pal.inkSoft, false);
            box.addView(e.sub);
            entries.add(e);
        }

        TextView close = text(a, 16, pal.buttonInk, true);
        close.setText("إغلاق");
        close.setGravity(Gravity.CENTER);
        int vp = Math.round(10 * density);
        close.setPadding(0, vp, 0, vp);
        GradientDrawable cb = new GradientDrawable();
        cb.setColor(pal.button);
        cb.setCornerRadius(16 * density);
        close.setBackground(cb);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.topMargin = Math.round(18 * density);
        box.addView(close, clp);

        d.setContentView(box);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(Math.round(Math.min(a.getResources().getDisplayMetrics().widthPixels * 0.88f, 420 * density)), -2);
        }

        final Handler h = new Handler(Looper.getMainLooper());
        final Runnable tick = new Runnable() {
            @Override
            public void run() {
                long n = System.currentTimeMillis();
                for (Entry e : entries) {
                    if (e.at > n) {
                        e.label.setText("الوقت المتبقي");
                        e.big.setText(span(e.at - n));
                        e.sub.setVisibility(View.GONE);
                    } else {
                        long next = nextOccurrence(a, e.kind, n);
                        e.label.setText("مضى منذ " + span(n - e.at) + "  —  الموعد القادم بعد");
                        e.big.setText(next > 0 ? span(next - n) : "--:--:--");
                        e.sub.setVisibility(next > 0 ? View.VISIBLE : View.GONE);
                        if (next > 0) e.sub.setText(MainActivity.fmt(next) + "  •  " + dayLabel(next, n));
                    }
                }
                h.postDelayed(this, 1000);
            }
        };
        tick.run();
        d.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface di) {
                h.removeCallbacks(tick);
                if (onDismiss != null) onDismiss.run();
            }
        });
        d.show();
        return d;
    }

    private static TextView text(Context c, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }
}
