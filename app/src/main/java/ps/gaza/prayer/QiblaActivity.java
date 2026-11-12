package ps.gaza.prayer;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Qibla compass, opened from the compass button next to the settings gear.
 *
 * The bearing is the great-circle initial course from Gaza (PrayerCalc.LAT/LON) to the Kaaba,
 * about 153° from true north. The phone's heading comes from the rotation-vector sensor
 * (accelerometer + magnetometer, gyro-fused where available), falling back to the raw
 * accelerometer + magnetometer pair. The magnetometer gives magnetic north, so the local
 * declination (GeomagneticField, a few degrees east in Gaza) is added to get true north.
 * No location permission is needed: the app is for Gaza only.
 */
public class QiblaActivity extends Activity implements SensorEventListener {

    private static final double KAABA_LAT = 21.422487;
    private static final double KAABA_LON = 39.826206;
    /** Within this many degrees the needle counts as pointing at the Qibla. */
    private static final float ALIGNED = 3f;

    private Palette pal;
    private SensorManager sm;
    private Sensor rotation, accel, magnet;
    private final float[] grav = new float[3], geo = new float[3];
    private boolean haveGrav, haveGeo;
    private float declination;
    private float qibla;
    /** Smoothed heading (degrees from true north), NaN until the first reading. */
    private float heading = Float.NaN;
    private boolean wasAligned;

    private CompassView dial;
    private TextView headingText, hint;

    static float qiblaBearing() {
        double p1 = Math.toRadians(PrayerCalc.LAT), p2 = Math.toRadians(KAABA_LAT);
        double dl = Math.toRadians(KAABA_LON - PrayerCalc.LON);
        double y = Math.sin(dl) * Math.cos(p2);
        double x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl);
        return (float) ((Math.toDegrees(Math.atan2(y, x)) + 360) % 360);
    }

    @Override
    protected void onCreate(Bundle b) {
        pal = Palette.of(this);
        setTheme(Palette.themeRes(pal.style));
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        qibla = qiblaBearing();
        declination = new GeomagneticField((float) PrayerCalc.LAT, (float) PrayerCalc.LON, 0,
                System.currentTimeMillis()).getDeclination();

        sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        rotation = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnet = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(pal.bg);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));

        TextView title = label(24, pal.ink, true);
        title.setText("اتجاه القبلة");
        root.addView(title);

        TextView bearing = label(16, pal.inkSoft, false);
        bearing.setText("القبلة من غزة: " + Math.round(qibla) + "° من الشمال");
        bearing.setPadding(0, dp(6), 0, 0);
        root.addView(bearing);

        dial = new CompassView(this);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, 0, 1f);
        dlp.topMargin = dp(16);
        dlp.bottomMargin = dp(16);
        root.addView(dial, dlp);

        headingText = label(20, pal.ink, true);
        root.addView(headingText);

        hint = label(14, pal.inkSoft, false);
        hint.setPadding(0, dp(8), 0, 0);
        root.addView(hint);

        TextView tip = label(13, pal.inkSoft, false);
        tip.setText("ضع الهاتف مستوياً، وأبعده عن المغناطيس والأجهزة المعدنية.");
        tip.setPadding(0, dp(12), 0, 0);
        root.addView(tip);

        setContentView(root);

        if (magnet == null || (rotation == null && accel == null)) {
            headingText.setText("لا توجد بوصلة في هذا الهاتف");
            hint.setText("وجّه وجهك إلى " + Math.round(qibla) + "° من الشمال (جنوب شرق تقريباً).");
        } else {
            headingText.setText("…");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (magnet == null) return;
        int rate = SensorManager.SENSOR_DELAY_UI;
        if (rotation != null) {
            sm.registerListener(this, rotation, rate);
        } else if (accel != null) {
            sm.registerListener(this, accel, rate);
        }
        // Registered either way: its accuracy tells whether the compass needs calibrating.
        sm.registerListener(this, magnet, rate);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sm.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        float[] r = new float[9];
        int type = e.sensor.getType();
        if (type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(r, e.values);
        } else {
            if (type == Sensor.TYPE_ACCELEROMETER) {
                lowPass(e.values, grav);
                haveGrav = true;
            } else if (type == Sensor.TYPE_MAGNETIC_FIELD) {
                lowPass(e.values, geo);
                haveGeo = true;
            }
            // The magnetometer alone only drives the fallback path.
            if (rotation != null || !haveGrav || !haveGeo) return;
            if (!SensorManager.getRotationMatrix(r, null, grav, geo)) return;
        }
        float[] o = new float[3];
        SensorManager.getOrientation(r, o);
        float h = norm((float) Math.toDegrees(o[0]) + declination);
        if (Float.isNaN(heading)) {
            heading = h;
        } else {
            // Smooth along the shortest arc so 359° -> 1° does not swing the long way round.
            heading = norm(heading + 0.2f * diff(h, heading));
        }
        update();
    }

    @Override
    public void onAccuracyChanged(Sensor s, int accuracy) {
        if (s.getType() != Sensor.TYPE_MAGNETIC_FIELD) return;
        boolean low = accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW;
        hint.setText(low ? "البوصلة غير دقيقة: حرّك الهاتف في الهواء على شكل الرقم 8 عدة مرات." : "");
    }

    private void update() {
        float off = diff(qibla, heading);
        boolean aligned = Math.abs(off) <= ALIGNED;
        if (aligned && !wasAligned) dial.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        wasAligned = aligned;
        if (aligned) {
            headingText.setText("أنت باتجاه القبلة ✓");
        } else {
            int deg = Math.round(Math.abs(off));
            headingText.setText(off > 0 ? "استدر يميناً " + deg + "°" : "استدر يساراً " + deg + "°");
        }
        dial.set(heading, qibla, aligned);
    }

    private static void lowPass(float[] in, float[] out) {
        for (int i = 0; i < 3; i++) out[i] += 0.25f * (in[i] - out[i]);
    }

    private static float norm(float d) {
        d %= 360f;
        return d < 0 ? d + 360f : d;
    }

    /** Signed difference a - b in (-180, 180]. */
    private static float diff(float a, float b) {
        float d = norm(a - b);
        return d > 180f ? d - 360f : d;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView label(float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    /**
     * The dial turns with the phone so its north mark points to true north; the Qibla marker is
     * fixed on the dial at the bearing, and the fixed pointer at the top is where the phone faces.
     */
    private class CompassView extends View {
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint letter = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint marker = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private float heading = 0, bearing = 0;
        private boolean aligned;

        CompassView(Context c) {
            super(c);
            ring.setStyle(Paint.Style.FILL);
            tick.setStrokeCap(Paint.Cap.ROUND);
            letter.setTextAlign(Paint.Align.CENTER);
            letter.setTypeface(Typeface.DEFAULT_BOLD);
            setContentDescription("بوصلة القبلة");
        }

        void set(float heading, float bearing, boolean aligned) {
            this.heading = heading;
            this.bearing = bearing;
            this.aligned = aligned;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float r = Math.min(cx, cy) - dp(18);
            if (r <= 0) return;
            int hot = aligned ? 0xFF43A047 : pal.button;

            // Dial face
            ring.setColor(pal.card);
            c.drawCircle(cx, cy, r, ring);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(dp(2));
            ring.setColor(aligned ? hot : (pal.stroke != 0 ? pal.stroke : pal.inkSoft));
            c.drawCircle(cx, cy, r, ring);
            ring.setStyle(Paint.Style.FILL);

            // Fixed pointer at the top: where the phone faces
            marker.setColor(hot);
            path.reset();
            path.moveTo(cx, cy - r - dp(14));
            path.lineTo(cx - dp(10), cy - r + dp(2));
            path.lineTo(cx + dp(10), cy - r + dp(2));
            path.close();
            c.drawPath(path, marker);

            c.save();
            c.rotate(-heading, cx, cy);

            // Ticks every 10°, longer every 30°
            for (int d = 0; d < 360; d += 10) {
                boolean major = d % 30 == 0;
                tick.setColor(major ? pal.ink : pal.inkSoft);
                tick.setStrokeWidth(dp(major ? 2 : 1));
                float len = dp(major ? 14 : 8);
                c.save();
                c.rotate(d, cx, cy);
                c.drawLine(cx, cy - r + dp(6), cx, cy - r + dp(6) + len, tick);
                c.restore();
            }

            // Cardinal letters (north in the accent colour)
            letter.setTextSize(dp(20));
            String[] names = {"ش", "ق", "ج", "غ"};
            for (int i = 0; i < 4; i++) {
                c.save();
                c.rotate(i * 90, cx, cy);
                letter.setColor(i == 0 ? 0xFFE53935 : pal.ink);
                c.drawText(names[i], cx, cy - r + dp(44), letter);
                c.restore();
            }

            // Qibla needle from the centre to the Kaaba mark
            c.save();
            c.rotate(bearing, cx, cy);
            tick.setColor(hot);
            tick.setStrokeWidth(dp(5));
            c.drawLine(cx, cy, cx, cy - r + dp(70), tick);
            // Kaaba: a small dark square with a gold band
            float k = dp(26), ky = cy - r + dp(56);
            marker.setColor(0xFF111111);
            c.drawRect(cx - k / 2, ky - k / 2, cx + k / 2, ky + k / 2, marker);
            marker.setColor(0xFFD4AF37);
            c.drawRect(cx - k / 2, ky - k / 2 + k * 0.22f, cx + k / 2, ky - k / 2 + k * 0.36f, marker);
            c.restore();

            c.restore();

            // Hub
            marker.setColor(hot);
            c.drawCircle(cx, cy, dp(9), marker);
        }
    }
}
