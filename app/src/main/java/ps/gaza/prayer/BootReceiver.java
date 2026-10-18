package ps.gaza.prayer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Alarms do not survive reboot or clock changes; re-arm them and redraw the widget. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        Scheduler.scheduleNext(c);
        GazaWidget.updateAll(c);
    }
}
