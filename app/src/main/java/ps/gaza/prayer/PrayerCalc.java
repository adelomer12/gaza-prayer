package ps.gaza.prayer;

import android.icu.util.IslamicCalendar;
import android.icu.util.ULocale;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Prayer times for Gaza, computed exactly like Home Assistant's
 * Islamic Prayer Times integration (library prayer-times-calculator-offline
 * 1.0.3, which wraps PrayTimes.org v2.3):
 *
 *   - times computed in UTC, one iteration, high-latitude rule "middle of the night"
 *   - each time rounded to the nearest minute, then shown in Asia/Gaza time
 *   - Egyptian General Authority of Survey: Fajr 19.5 deg, Isha 17.5 deg
 *   - Asr Shafi (shadow factor 1), sunrise/sunset 0.833 deg
 *   - Maghrib = sunset + 3 min: the margin in the official Gaza tables, built into
 *     the HA setup the same way (Egyptian + 3 min was within 1 min of 629/630 table times)
 *
 * North America (ISNA) Fajr uses the same code with a 15 deg angle.
 * Per-prayer minute offsets are applied on top (see Scheduler), like the HA dashboard.
 */
public final class PrayerCalc {

    public static final double LAT = 31.5017;
    public static final double LON = 34.4668;
    /**
     * Display time zone. Normally Asia/Gaza from the phone's tz database (summer/winter time
     * automatic); the user can pin winter (UTC+2) or summer (UTC+3) time in case Palestine moves
     * the change date and the phone has not been updated yet. Refreshed by applyTimeMode().
     */
    public static TimeZone TZ = TimeZone.getTimeZone("Asia/Gaza");
    static final String KEY_TIME_MODE = "time_mode"; // 0 automatic, 1 winter UTC+2, 2 summer UTC+3
    private static final String[] TZ_IDS = {"Asia/Gaza", "GMT+02:00", "GMT+03:00"};

    static void applyTimeMode(android.content.Context c) {
        int m = Scheduler.prefs(c).getInt(KEY_TIME_MODE, 0);
        TZ = TimeZone.getTimeZone(TZ_IDS[m < 0 || m > 2 ? 0 : m]);
    }

    public static final double FAJR_ANGLE_EGYPT = 19.5;
    public static final double ISHA_ANGLE_EGYPT = 17.5;
    public static final double FAJR_ANGLE_NORTH_AMERICA = 15.0;
    public static final int MAGHRIB_MARGIN_MIN = 3;

    public static final int FAJR = 0, SUNRISE = 1, DHUHR = 2, ASR = 3, MAGHRIB = 4, ISHA = 5;
    public static final String[] NAMES_AR = {"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"};

    private PrayerCalc() {}

    /**
     * The six official times of a Gaza calendar day as epoch millis, before user offsets.
     * Source order, as in Home Assistant: the official Gaza table (GazaTable, UTC+2;
     * summer time comes from Asia/Gaza automatically because we produce absolute instants),
     * then the Egyptian calculation for days the table lacks (29 Feb).
     */
    public static long[] officialTimesFor(int year, int month1, int day) {
        short[] row = GazaTable.lookup(month1, day);
        if (row == null) return timesFor(year, month1, day, FAJR_ANGLE_EGYPT);
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(year, month1 - 1, day, 0, 0, 0);
        long base = utc.getTimeInMillis() - 2 * 3600000L; // local midnight in UTC+2
        long[] out = new long[6];
        for (int i = 0; i < 6; i++) out[i] = base + row[i] * 60000L;
        return out;
    }

    public static boolean inTable(int month1, int day) {
        return GazaTable.lookup(month1, day) != null;
    }

    /** Calculated times (HA's Islamic Prayer Times + 3 min Maghrib margin), before user offsets. */
    public static long[] timesFor(int year, int month1, int day, double fajrAngle) {
        double[] h = computeUtcHours(year, month1, day, fajrAngle, ISHA_ANGLE_EGYPT);
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(year, month1 - 1, day, 0, 0, 0);
        long base = utc.getTimeInMillis();
        long[] out = new long[6];
        for (int i = 0; i < 6; i++) {
            // Same rounding as the HA library: add 30 s, then drop the seconds.
            long ms = base + (long) Math.floor(h[i] * 3600000.0) + 30000L - 1L;
            out[i] = ms - Math.floorMod(ms, 60000L);
        }
        out[MAGHRIB] += MAGHRIB_MARGIN_MIN * 60000L;
        return out;
    }

    // ---- PrayTimes.org v2.3, as in prayer_times_calculator_offline/praytimes.py ----

    private static double[] computeUtcHours(int year, int month, int day, double fajrAngle, double ishaAngle) {
        double jDate = julian(year, month, day) - LON / (15 * 24.0);

        // dayPortion() of the initial guesses
        double tFajr = 5 / 24.0, tSunrise = 6 / 24.0, tDhuhr = 12 / 24.0, tAsr = 13 / 24.0,
                tSunset = 18 / 24.0, tIsha = 18 / 24.0;

        double riseSet = 0.833; // elevation 0
        double fajr = sunAngleTime(jDate, fajrAngle, tFajr, true);
        double sunrise = sunAngleTime(jDate, riseSet, tSunrise, true);
        double dhuhr = midDay(jDate, tDhuhr);
        double asr = asrTime(jDate, 1, tAsr);
        double sunset = sunAngleTime(jDate, riseSet, tSunset, false);
        double isha = sunAngleTime(jDate, ishaAngle, tIsha, false);

        // adjustTimes() with timezone 0
        double tzAdjust = -LON / 15.0;
        fajr += tzAdjust; sunrise += tzAdjust; dhuhr += tzAdjust; asr += tzAdjust;
        sunset += tzAdjust; isha += tzAdjust;

        // high latitudes: middle of the night (never triggers at 31.5 N, kept for parity)
        double night = timeDiff(sunset, sunrise);
        fajr = adjustHLTime(fajr, sunrise, night, true);
        isha = adjustHLTime(isha, sunset, night, false);

        double maghrib = sunset; // Egypt: maghrib "0 min"
        return new double[]{fajr, sunrise, dhuhr, asr, maghrib, isha};
    }

    private static double julian(int year, int month, int day) {
        if (month <= 2) {
            year -= 1;
            month += 12;
        }
        double a = Math.floor(year / 100.0);
        double b = 2 - a + Math.floor(a / 4);
        return Math.floor(365.25 * (year + 4716)) + Math.floor(30.6001 * (month + 1)) + day + b - 1524.5;
    }

    /** {declination, equation of time} */
    private static double[] sunPosition(double jd) {
        double d = jd - 2451545.0;
        double g = fixAngle(357.529 + 0.98560028 * d);
        double q = fixAngle(280.459 + 0.98564736 * d);
        double l = fixAngle(q + 1.915 * sin(g) + 0.020 * sin(2 * g));
        double e = 23.439 - 0.00000036 * d;
        double ra = Math.toDegrees(Math.atan2(cos(e) * sin(l), cos(l))) / 15.0;
        double eqt = q / 15.0 - fixHour(ra);
        double decl = Math.toDegrees(Math.asin(sin(e) * sin(l)));
        return new double[]{decl, eqt};
    }

    private static double midDay(double jDate, double time) {
        double eqt = sunPosition(jDate + time)[1];
        return fixHour(12 - eqt);
    }

    private static double sunAngleTime(double jDate, double angle, double time, boolean ccw) {
        double decl = sunPosition(jDate + time)[0];
        double noon = midDay(jDate, time);
        double c = (-sin(angle) - sin(decl) * sin(LAT)) / (cos(decl) * cos(LAT));
        c = Math.min(1, Math.max(-1, c));
        double t = 1 / 15.0 * Math.toDegrees(Math.acos(c));
        return noon + (ccw ? -t : t);
    }

    private static double asrTime(double jDate, double factor, double time) {
        double decl = sunPosition(jDate + time)[0];
        double angle = -Math.toDegrees(Math.atan(1.0 / (factor + Math.tan(Math.toRadians(Math.abs(LAT - decl))))));
        return sunAngleTime(jDate, angle, time, false);
    }

    private static double adjustHLTime(double time, double base, double night, boolean ccw) {
        double portion = night / 2.0;
        double diff = ccw ? timeDiff(time, base) : timeDiff(base, time);
        if (Double.isNaN(time) || diff > portion) time = base + (ccw ? -portion : portion);
        return time;
    }

    private static double timeDiff(double t1, double t2) { return fixHour(t2 - t1); }
    private static double sin(double d) { return Math.sin(Math.toRadians(d)); }
    private static double cos(double d) { return Math.cos(Math.toRadians(d)); }
    private static double fixAngle(double a) { return fix(a, 360.0); }
    private static double fixHour(double a) { return fix(a, 24.0); }
    private static double fix(double a, double mode) {
        if (Double.isNaN(a)) return a;
        a = a - mode * Math.floor(a / mode);
        return a < 0 ? a + mode : a;
    }

    // ---- Hijri date (Umm al-Qura) for display ----

    /** Hijri date with a user correction in whole days (like the HA "تصحيح التاريخ الهجري" helper). */
    static IslamicCalendar hijri(Calendar day, int offsetDays) {
        IslamicCalendar ic = new IslamicCalendar(android.icu.util.TimeZone.getTimeZone(TZ.getID()), ULocale.ENGLISH);
        ic.setCalculationType(IslamicCalendar.CalculationType.ISLAMIC_UMALQURA);
        ic.setTimeInMillis(day.getTimeInMillis());
        ic.add(IslamicCalendar.DAY_OF_MONTH, offsetDays);
        return ic;
    }

    public static boolean isRamadan(long when, int offsetDays) {
        Calendar c = Calendar.getInstance(TZ);
        c.setTimeInMillis(when);
        return hijri(c, offsetDays).get(IslamicCalendar.MONTH) == IslamicCalendar.RAMADAN;
    }

    private static final String[] HIJRI_MONTHS = {
            "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
            "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"};

    static String hijriText(Calendar day, int offsetDays) {
        IslamicCalendar ic = hijri(day, offsetDays);
        return ic.get(IslamicCalendar.DAY_OF_MONTH) + " " + HIJRI_MONTHS[ic.get(IslamicCalendar.MONTH)]
                + " " + ic.get(IslamicCalendar.YEAR) + " هـ";
    }
}
