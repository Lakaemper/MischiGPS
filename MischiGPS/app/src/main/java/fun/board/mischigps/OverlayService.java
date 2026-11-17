package fun.board.mischigps;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.IBinder;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.Locale;

/** Always-on-top overlay showing GPS altitude in metres. Default: lower-left, draggable. */
public class OverlayService extends Service implements LocationListener {
    private static final String CH = "overlay";
    private static final String ACTION_STOP = "stop";

    private WindowManager wm;
    private TextView tv;
    private TextView close;
    private WindowManager.LayoutParams closeLp;
    private boolean closeShown;
    private int cs, gap;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable hideClose = () -> setCloseVisible(false);
    private WindowManager.LayoutParams lp;
    private LocationManager lm;
    private SharedPreferences prefs;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (tv != null) return START_STICKY;

        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CH, "Altitude overlay", NotificationManager.IMPORTANCE_LOW));
        PendingIntent stop = PendingIntent.getService(this, 0,
                new Intent(this, OverlayService.class).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CH)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("MischiGPS altitude overlay")
                .addAction(new Notification.Action.Builder(null, "Stop", stop).build())
                .build();
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);

        prefs = getSharedPreferences("pos", MODE_PRIVATE);
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        float d = getResources().getDisplayMetrics().density;

        tv = new TextView(this);
        tv.setText("--- m");
        tv.setTextColor(Color.WHITE);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        tv.setPadding((int) (14 * d), (int) (8 * d), (int) (14 * d), (int) (8 * d));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xAA000000);
        bg.setCornerRadius(12 * d);
        tv.setBackground(bg);

        // Close ornament: its own small window above the display, shown only briefly after a touch.
        cs = (int) (40 * d);
        gap = (int) (6 * d);
        close = new TextView(this);
        close.setText("✕");
        close.setTextColor(Color.WHITE);
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        close.setGravity(Gravity.CENTER);
        GradientDrawable cbg = new GradientDrawable();
        cbg.setShape(GradientDrawable.OVAL);
        cbg.setColor(0xDDCC2222);
        close.setBackground(cbg);
        close.setOnClickListener(v -> stopSelf());
        closeLp = new WindowManager.LayoutParams(cs, cs,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        closeLp.gravity = Gravity.BOTTOM | Gravity.START;

        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        // Offsets are measured from the lower-left corner, so rotation keeps it there.
        lp.gravity = Gravity.BOTTOM | Gravity.START;
        lp.x = prefs.getInt("x", (int) (16 * d));
        lp.y = prefs.getInt("y", (int) (16 * d));

        tv.setOnTouchListener(new View.OnTouchListener() {
            float dx, dy; int sx, sy;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        handler.removeCallbacks(hideClose);
                        showClose();
                        dx = e.getRawX(); dy = e.getRawY(); sx = lp.x; sy = lp.y; return true;
                    case MotionEvent.ACTION_MOVE:
                        lp.x = Math.max(0, sx + (int) (e.getRawX() - dx));
                        lp.y = Math.max(0, sy - (int) (e.getRawY() - dy)); // y grows upward from bottom
                        wm.updateViewLayout(tv, lp);
                        if (closeShown) placeClose();
                        return true;
                    case MotionEvent.ACTION_UP:
                        prefs.edit().putInt("x", lp.x).putInt("y", lp.y).apply();
                        handler.postDelayed(hideClose, 3000);
                        return true;
                }
                return false;
            }
        });
        wm.addView(tv, lp);

        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);
        } catch (SecurityException ex) {
            tv.setText("no location perm");
        }
        return START_STICKY;
    }

    private void placeClose() {
        closeLp.x = lp.x + tv.getWidth() - cs;   // right-aligned with the display
        closeLp.y = lp.y + tv.getHeight() + gap; // just above it
        wm.updateViewLayout(close, closeLp);
    }

    private void showClose() {
        if (closeShown || close == null) { return; }
        closeLp.x = lp.x + tv.getWidth() - cs;
        closeLp.y = lp.y + tv.getHeight() + gap;
        wm.addView(close, closeLp);
        closeShown = true;
    }

    private void setCloseVisible(boolean on) {
        if (!on && closeShown && close != null) {
            try { wm.removeView(close); } catch (Exception ignored) {}
            closeShown = false;
        }
    }

    @Override
    public void onLocationChanged(Location l) {
        if (!l.hasAltitude()) { tv.setText("--- m"); return; }
        double alt = l.getAltitude(); // ellipsoid height
        if (Build.VERSION.SDK_INT >= 34 && l.hasMslAltitude()) alt = l.getMslAltitudeMeters(); // sea level
        tv.setText(String.format(Locale.US, "%.0f m", alt));
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(hideClose);
        if (lm != null) lm.removeUpdates(this);
        setCloseVisible(false);
        if (tv != null) { try { wm.removeView(tv); } catch (Exception ignored) {} tv = null; }
        super.onDestroy();
    }
}
