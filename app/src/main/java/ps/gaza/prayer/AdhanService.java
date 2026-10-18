package ps.gaza.prayer;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.os.PowerManager;

/**
 * Plays res/raw/adhan.mp3 once on the alarm stream, as a foreground service so it keeps
 * playing with the screen off. The notification has a "إيقاف الأذان" button; the service
 * stops itself when the recording ends, on an error, or when another app takes audio focus
 * (a phone call, for example).
 */
public class AdhanService extends Service {

    static final String ACTION_STOP = "ps.gaza.prayer.STOP_ADHAN";
    private static final int NOTIFICATION_ID = 200;

    private MediaPlayer player;
    private AudioManager audio;
    private AudioFocusRequest focus;

    static void start(Context c, int idx) {
        c.startForegroundService(new Intent(c, AdhanService.class).putExtra(Scheduler.EXTRA_INDEX, idx));
    }

    static void stop(Context c) {
        c.stopService(new Intent(c, AdhanService.class));
    }

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        int idx = intent.getIntExtra(Scheduler.EXTRA_INDEX, -1);
        Scheduler.ensureChannel(this);
        PendingIntent stop = PendingIntent.getService(this, 0,
                new Intent(this, AdhanService.class).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE);
        Notification n = AdhanReceiver.notification(this, idx, Scheduler.CHANNEL_PLAY)
                .setOngoing(true)
                .setDeleteIntent(stop)
                .addAction(new Notification.Action.Builder(null, "إيقاف الأذان", stop).build())
                .build();
        startForeground(NOTIFICATION_ID, n);
        play();
        return START_NOT_STICKY;
    }

    private void play() {
        release();
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        audio = getSystemService(AudioManager.class);
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(new AudioManager.OnAudioFocusChangeListener() {
                    @Override
                    public void onAudioFocusChange(int change) {
                        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                            stopSelf();
                        }
                    }
                })
                .build();
        audio.requestAudioFocus(focus);
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(attrs);
            player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
            AssetFileDescriptor afd = getResources().openRawResourceFd(R.raw.adhan);
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    stopSelf();
                }
            });
            player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    stopSelf();
                    return true;
                }
            });
            player.prepare();
            player.start();
        } catch (Exception e) {
            stopSelf();
        }
    }

    private void release() {
        if (player != null) {
            try {
                player.stop();
            } catch (IllegalStateException ignored) {
            }
            player.release();
            player = null;
        }
        if (audio != null && focus != null) audio.abandonAudioFocusRequest(focus);
    }

    @Override
    public void onDestroy() {
        release();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
}
