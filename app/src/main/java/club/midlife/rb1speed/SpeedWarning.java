package club.midlife.rb1speed;

/** Warning timing uses fresh live speed or explicit demo speed, never rounded display speed. */
public final class SpeedWarning {
    public static final double START_MPH = 75;
    public static final double STOP_MPH = 73;
    public static final long INTERVAL_MS = 1500;
    private boolean warning;
    private long lastChime = -1;
    private long lastUpdate = -1;

    public boolean update(double mph, boolean usableSpeed, long nowMs) {
        if (!usableSpeed || !Double.isFinite(mph) || mph < 0 || nowMs < 0) { reset(); return false; }
        if (lastUpdate >= 0 && (nowMs < lastUpdate || nowMs - lastUpdate >= SpeedReading.MAX_AGE_MS)) reset();
        lastUpdate = nowMs;
        if (mph < STOP_MPH) { reset(); return false; }
        if (!warning && mph >= START_MPH) warning = true;
        if (!warning) return false;
        if (lastChime < 0 || nowMs < lastChime || nowMs - lastChime >= INTERVAL_MS) {
            lastChime = nowMs;
            return true;
        }
        return false;
    }

    public boolean active() { return warning; }
    public void reset() { warning = false; lastChime = -1; lastUpdate = -1; }
}
