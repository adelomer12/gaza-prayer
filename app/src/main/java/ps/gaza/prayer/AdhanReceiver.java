package ps.gaza.prayer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AdhanReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        int idx = intent.getIntExtra(Scheduler.EXTRA_INDEX, -1);
        int mode = idx >= 0 && idx <= Scheduler.INDEX_FAJR_NA ? Scheduler.mode(c, idx) : Scheduler.MODE_OFF;
        if (mode == Scheduler.MODE_ADHAN) {
            // Exact alarms may start a foreground service from the background (Android 12+ exemption).
            AdhanService.start(c, idx);
        } else if (mode == Scheduler.MODE_NOTIFY) {
            Scheduler.ensureChannel(c);
            c.getSystemService(NotificationManager.class).notify(100 + idx, notification(c, idx, Scheduler.CHANNEL_ID).build());
        }
        // Arm the following event (alarms never fire early, so this one is already in the past).
        Scheduler.scheduleNext(c);
    }

    static String title(int idx) {
        if (idx == Scheduler.INDEX_FIRST_ADHAN) return "الأذان الأول";
        if (idx == Scheduler.INDEX_FAJR_NA) return "فجر أمريكا الشمالية";
        if (idx < 0) return "تجربة الأذان";
        return "حان الآن موعد صلاة " + PrayerCalc.NAMES_AR[idx];
    }

    static Notification.Builder notification(Context c, int idx, String channel) {
        PendingIntent open = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(c, channel)
                .setSmallIcon(R.drawable.ic_notify)
                .setColor(0xFFFFC107)
                .setContentTitle(title(idx))
                .setContentText("حسب توقيت غزة")
                .setCategory(Notification.CATEGORY_ALARM)
                .setContentIntent(open)
                .setAutoCancel(true);
    }
}
