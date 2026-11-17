package fun.board.mischigps;

/**
 * Fuses GPS altitude (absolute but noisy, typically +-10..30 m) with barometric pressure
 * (precise for changes, but drifts with the weather).
 *
 * The barometer gives a fine-grained altitude curve; a scalar Kalman filter slowly estimates the
 * offset between that curve and the GPS altitude, weighting each GPS fix by its reported vertical
 * accuracy. Result: stable, responsive altitude that is still anchored to GPS.
 *
 * Plain Java (no Android classes) so it can be unit-tested on the desktop.
 */
final class AltitudeFilter {
    /** Process noise of the barometer offset in m^2/s (weather / cabin-pressure drift). */
    private static final double Q = 0.01;
    /** Assumed vertical accuracy when the fix does not report one. */
    private static final double DEFAULT_ACC = 25.0;
    /** Fixes worse than this are ignored once the filter is running. */
    private static final double MAX_ACC = 80.0;
    /** Smoothing of raw pressure samples (0..1, higher = faster). */
    private static final double PRESSURE_ALPHA = 0.2;

    private double pressure = Double.NaN; // smoothed, hPa
    private double offset;                // GPS altitude minus barometric altitude
    private double variance;              // uncertainty of offset, m^2
    private boolean initialised;
    private long lastNs;
    private double lastGps = Double.NaN;  // fallback when there is no barometer

    /** Feed a raw pressure sample in hPa. */
    void onPressure(double hPa) {
        pressure = Double.isNaN(pressure) ? hPa : pressure + PRESSURE_ALPHA * (hPa - pressure);
    }

    /** Feed a GPS altitude (sea level, metres) with its vertical accuracy (NaN or <=0 if unknown). */
    void onGps(double gpsAlt, double verticalAccuracy, long nowNs) {
        double acc = verticalAccuracy > 0 ? verticalAccuracy : DEFAULT_ACC;
        lastGps = gpsAlt;
        if (Double.isNaN(pressure)) return; // no barometer: fall back to raw GPS
        double baroAlt = baroAltitude(pressure);
        double r = acc * acc;
        if (!initialised) {
            offset = gpsAlt - baroAlt;
            variance = r;
            initialised = true;
        } else {
            if (acc > MAX_ACC) { lastNs = nowNs; return; }
            double dt = Math.max(0, (nowNs - lastNs) / 1e9);
            variance += Q * dt;
            double k = variance / (variance + r);
            offset += k * (gpsAlt - baroAlt - offset);
            variance *= (1 - k);
        }
        lastNs = nowNs;
    }

    /** Current best altitude in metres, or NaN if there is no GPS fix yet. */
    double altitude() {
        if (initialised && !Double.isNaN(pressure)) return baroAltitude(pressure) + offset;
        return lastGps;
    }

    boolean usingBarometer() { return initialised; }

    /** International standard atmosphere, relative to 1013.25 hPa. */
    static double baroAltitude(double hPa) {
        return 44330.77 * (1.0 - Math.pow(hPa / 1013.25, 0.190263));
    }
}
