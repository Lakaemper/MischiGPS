# MischiGPS

A tiny Android overlay that shows the **GPS altitude in meters**, always on top of other apps.

- Floating, always-on-top display (lower-left corner by default, works in landscape and portrait)
- Drag it anywhere; the position is remembered
- Touch it to reveal a small ✕ above it (hides after ~3 s) to close it
- Altitude only, in whole meters; shows `--- m` until there is a GPS fix
- **Barometer fusion:** GPS altitude (absolute but noisy, ±10–30 m) is combined with the phone's pressure sensor
  (precise for changes, but drifts with the weather). A small Kalman filter (`AltitudeFilter.java`) weights each
  GPS fix by its reported vertical accuracy, so the value is stable, reacts instantly to real climbs/descents and
  ignores GPS outliers. Without a barometer it falls back to plain GPS altitude. GPS altitude is the sea-level
  value on Android 14+, otherwise the raw GPS (ellipsoid) height.
- Optional auto-start whenever the [S3XY](https://play.google.com/store/apps/details?id=com.enhauto.buttons) app comes to the foreground

GPS is requested once per second. Plain Java, no dependencies.

## Build

Requires the Android SDK (platform 35) and JDK 17+.

```bash
echo "sdk.dir=/path/to/Android/Sdk" > local.properties   # not committed
./gradlew assembleDebug
```

The APK ends up in `app/build/outputs/apk/debug/app-debug.apk`.

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Setup on the phone

1. Open **MischiGPS** once. It asks for location and "Display over other apps" permission, then starts the overlay.
2. Choose location access **Allow all the time** (needed so auto-start works from the background).
3. For auto-start with S3XY: **Settings → Accessibility → MischiGPS auto-start (S3XY)** → On.
   The service only sees when S3XY comes to the foreground; it reads no screen content.

Stop the overlay with the ✕ or the **Stop** button in its notification.

Using a different dashboard app instead of S3XY? Change the package name in
`app/src/main/res/xml/autostart_config.xml` and `AutoStartService.java`.

## Permissions

| Permission | Why |
|---|---|
| `ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION` | read GPS altitude, also when auto-started in the background |
| `SYSTEM_ALERT_WINDOW` | draw the always-on-top overlay |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION` | keep the overlay and GPS running |
| `POST_NOTIFICATIONS` | show the foreground-service notification with Stop |
