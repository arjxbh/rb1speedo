package club.midlife.rb1speed;

/** Foreground-session UI timing, independent of Android. */
public final class DriveSession {
    public static final long HIDE_DEMO_AFTER_MS = 10000;
    public static final double MOVING_MPH = 1.0;
    private long movingSince = -1;
    private long previous = -1;
    private boolean hidden;

    public void update(double mph, boolean freshLive, long nowMs) {
        if (previous >= 0 && (nowMs < previous || nowMs - previous >= SpeedReading.MAX_AGE_MS)) movingSince = -1;
        previous = nowMs;
        if (hidden) return;
        if (!freshLive || !Double.isFinite(mph) || mph <= MOVING_MPH) { movingSince = -1; return; }
        if (movingSince < 0) movingSince = nowMs;
        if (nowMs - movingSince >= HIDE_DEMO_AFTER_MS) hidden = true;
    }

    public boolean demoVisible() { return !hidden; }
    public void reset() { movingSince = -1; previous = -1; hidden = false; }
}
