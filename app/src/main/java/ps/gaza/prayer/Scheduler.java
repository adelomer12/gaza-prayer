package ps.gaza.prayer;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;

import java.util.Calendar;

/** Keeps exactly one alarm armed: the next event (prayer, first adhan or North America Fajr) not switched off. */
public final class Scheduler {

    static final String PREFS = "gaza_prayer";
    static final String KEY_NOTIFY = "notify";
    static final String CHANNEL_ID = "adhan";
    static final String EXTRA_INDEX = "index";

    private Scheduler() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Minute offsets, same idea as the "تعديل الأوقات" helpers in Home Assistant. */
    // Maghrib key renamed in 1.5: the +3 min margin moved into the calculation.
    static final String[] OFFSET_KEYS = {"off_fajr", "off_sunrise", "off_dhuhr", "off_asr", "off_maghrib_v2", "off_isha"};
    static final String OFFSET_FAJR_NA = "off_fajr_na";
    // Defaults match the HA offset helpers on 16 Oct 2026: all 0, North America Fajr -2.
    static final int[] OFFSET_DEFAULTS = {0, 0, 0, 0, 0, 0};
    static final int OFFSET_FAJR_NA_DEFAULT = -2;

    /** First adhan (الأذان الأول): minutes before Fajr, 30 more in Ramadan, as in the Gaza tables. */
    static final String KEY_FIRST_ADHAN = "first_adhan_min";
    static final int FIRST_ADHAN_DEFAULT = 30;
    static final int INDEX_FIRST_ADHAN = 6;
    static final int INDEX_FAJR_NA = 7;

    /** Per-event sound: off, short notification, or the full adhan (AdhanService). */
    static final int MODE_OFF = 0, MODE_NOTIFY = 1, MODE_ADHAN = 2;
    /** Order shown in the adhan settings, earliest of the night first. */
    static final int[] EVENT_ORDER = {INDEX_FIRST_ADHAN, INDEX_FAJR_NA, 0, 1, 2, 3, 4, 5};

    static String modeKey(int idx) {
        return "mode_" + idx;
    }

    static int mode(Context c, int idx) {
        return prefs(c).getInt(modeKey(idx), idx == PrayerCalc.SUNRISE ? MODE_OFF : MODE_ADHAN);
    }

    static String eventName(int idx) {
        if (idx == INDEX_FIRST_ADHAN) return "الأذان الأول";
        if (idx == INDEX_FAJR_NA) return "فجر أمريكا الشمالية";
        return PrayerCalc.NAMES_AR[idx];
    }

    static long firstAdhan(Context c, long fajr) {
        int gap = prefs(c).getInt(KEY_FIRST_ADHAN, FIRST_ADHAN_DEFAULT);
        if (PrayerCalc.isRamadan(fajr, hijriOffset(c))) gap += 30;
        return fajr - gap * 60000L;
    }

    /** Hijri date correction in days (-2..+2 is typical), also moves the Ramadan first-adhan gap. */
    static final String KEY_HIJRI_OFFSET = "hijri_offset_days";

    static int hijriOffset(Context c) {
        return prefs(c).getInt(KEY_HIJRI_OFFSET, 0);
    }

    static int offset(Context c, int i) {
        return prefs(c).getInt(OFFSET_KEYS[i], OFFSET_DEFAULTS[i]);
    }

    static int offsetFajrNa(Context c) {
        return prefs(c).getInt(OFFSET_FAJR_NA, OFFSET_FAJR_NA_DEFAULT);
    }

    static long[] dayTimes(Context c, Calendar day) {
        long[] t = PrayerCalc.officialTimesFor(day.get(Calendar.YEAR), day.get(Calendar.MONTH) + 1,
                day.get(Calendar.DAY_OF_MONTH));
        for (int i = 0; i < 6; i++) t[i] += offset(c, i) * 60000L;
        return t;
    }

    static long fajrNorthAmerica(Context c, Calendar day) {
        return PrayerCalc.timesFor(day.get(Calendar.YEAR), day.get(Calendar.MONTH) + 1,
                day.get(Calendar.DAY_OF_MONTH), PrayerCalc.FAJR_ANGLE_NORTH_AMERICA)[PrayerCalc.FAJR]
                + offsetFajrNa(c) * 60000L;
    }

    static long[] todayAndTomorrow(Context c, Calendar day) {
        long[] t = dayTimes(c, day);
        Calendar next = (Calendar) day.clone();
        next.add(Calendar.DAY_OF_MONTH, 1);
        long[] n = dayTimes(c, next);
        long[] all = new long[12];
        System.arraycopy(t, 0, all, 0, 6);
        System.arraycopy(n, 0, all, 6, 6);
        return all;
    }

    /** Index 0..11 into todayAndTomorrow() of the next event after now; skipSunrise for alarms. */
    static int nextIndex(long[] all, long now, boolean skipSunrise) {
        for (int i = 0; i < all.length; i++) {
            if (skipSunrise && i % 6 == PrayerCalc.SUNRISE) continue;
            if (all[i] > now) return i;
        }
        return -1;
    }

    /** 0 while today's Isha is still ahead, 6 once it has come: the shown list is then tomorrow's. */
    static int listShift(long[] all, long now) {
        return now >= all[PrayerCalc.ISHA] ? 6 : 0;
    }

    /** Silent, high-priority channel for the notification shown while the adhan plays. */
    static final String CHANNEL_PLAY = "adhan_play";

    static void ensureChannel(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL_PLAY) == null) {
            NotificationChannel p = new NotificationChannel(CHANNEL_PLAY, "تشغيل الأذان", NotificationManager.IMPORTANCE_HIGH);
            p.setDescription("إشعار مع زر الإيقاف أثناء تشغيل الأذان");
            p.setSound(null, null);
            p.enableVibration(false);
            nm.createNotificationChannel(p);
        }
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "الأذان", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("تنبيه عند دخول وقت كل صلاة");
        AudioAttributes aa = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        ch.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), aa);
        ch.enableVibration(true);
        nm.createNotificationChannel(ch);
    }

    static void scheduleNext(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        Intent i = new Intent(c, AdhanReceiver.class);
        PendingIntent cancel = PendingIntent.getBroadcast(c, 1, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(cancel);
        if (!prefs(c).getBoolean(KEY_NOTIFY, true)) return;

        PrayerCalc.applyTimeMode(c);
        long now = System.currentTimeMillis();
        Calendar today = Calendar.getInstance(PrayerCalc.TZ);
        long[] all = todayAndTomorrow(c, today);
        long when = Long.MAX_VALUE;
        int which = -1;
        for (int d = 0; d < 2; d++) {
            Calendar day = (Calendar) today.clone();
            day.add(Calendar.DAY_OF_MONTH, d);
            long[] t = new long[8];
            System.arraycopy(all, d * 6, t, 0, 6);
            t[INDEX_FIRST_ADHAN] = firstAdhan(c, all[d * 6 + PrayerCalc.FAJR]);
            t[INDEX_FAJR_NA] = fajrNorthAmerica(c, day);
            for (int e = 0; e < 8; e++) {
                if (t[e] > now && t[e] < when && mode(c, e) != MODE_OFF) {
                    when = t[e];
                    which = e;
                }
            }
        }
        if (which < 0) return;

        i.putExtra(EXTRA_INDEX, which);
        PendingIntent pi = PendingIntent.getBroadcast(c, 1, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        if (exact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        }
    }
}
