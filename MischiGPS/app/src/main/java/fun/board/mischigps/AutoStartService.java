package fun.board.mischigps;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;

/** Starts the altitude overlay whenever the S3XY app (com.enhauto.buttons) comes to the foreground. */
public class AutoStartService extends AccessibilityService {
    private static final String S3XY = "com.enhauto.buttons";

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getPackageName() == null || !S3XY.contentEquals(e.getPackageName())) return;
        if (!Settings.canDrawOverlays(this)
                || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        // OverlayService ignores the call if it is already running.
        startForegroundService(new Intent(this, OverlayService.class));
    }

    @Override
    public void onInterrupt() { }
}
