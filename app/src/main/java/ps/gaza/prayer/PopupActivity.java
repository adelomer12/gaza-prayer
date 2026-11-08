package ps.gaza.prayer;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

import java.util.Calendar;

/**
 * Transparent host for the "time left" popup when a widget cell is tapped:
 * a widget cannot show a dialog itself. Closes as soon as the popup does.
 */
public class PopupActivity extends Activity {

    static final String EXTRA_KIND = "kind";
    private android.app.Dialog dialog;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        PrayerCalc.applyTimeMode(this);
        int kind = getIntent().getIntExtra(EXTRA_KIND, PrayerCalc.FAJR);

        // Same day the widget shows: today, or tomorrow once today's Isha has passed.
        long now = System.currentTimeMillis();
        Calendar today = Calendar.getInstance(PrayerCalc.TZ);
        today.setTimeInMillis(now);
        long[] all = Scheduler.todayAndTomorrow(this, today);
        Calendar day = (Calendar) today.clone();
        day.add(Calendar.DAY_OF_MONTH, Scheduler.listShift(all, now) / 6);

        dialog = TimeLeft.show(this, Palette.of(this), TimeLeft.group(kind), day, new Runnable() {
            @Override
            public void run() {
                finish();
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
        super.onDestroy();
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        finish();
    }
}
