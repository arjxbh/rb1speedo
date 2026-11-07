package club.midlife.rb1speed;

/** Pure speed validation, independent of Android and wall-clock changes. */
public final class SpeedReading {
    public static final double MPH_PER_MPS = 2.2369362920544;
    public static final long MAX_AGE_MS = 4000;
    private double mph;
    private long fixMs = -1;
    public boolean accept(double mps, boolean hasSpeed, float horizontalAccuracy,
                          boolean hasSpeedAccuracy, float speedAccuracy, long sampleMs, long nowMs) {
        if (!hasSpeed || !Double.isFinite(mps) || mps < 0 ||
            !Float.isFinite(horizontalAccuracy) || horizontalAccuracy < 0 || horizontalAccuracy > 35 ||
            (hasSpeedAccuracy && (!Float.isFinite(speedAccuracy) || speedAccuracy < 0 || speedAccuracy > 3)) ||
            sampleMs > nowMs || nowMs - sampleMs >= MAX_AGE_MS || sampleMs <= fixMs) return false;
        mph = mps * MPH_PER_MPS;
        fixMs = sampleMs;
        return true;
    }
    public boolean fresh(long nowMs) { return fixMs >= 0 && nowMs >= fixMs && nowMs - fixMs < MAX_AGE_MS; }
    public double mph() { return mph; }
    public void clear() { fixMs = -1; mph = 0; }
}
