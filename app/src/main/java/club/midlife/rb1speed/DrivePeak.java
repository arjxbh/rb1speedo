package club.midlife.rb1speed;

/** Peak for the current drive. Unknown GPS never proves that the vehicle has stopped. */
public final class DrivePeak {
    public static final long END_AFTER_MS = 3 * 60 * 1000L;
    private boolean powered, active;
    private double maximum = -1;
    private long parkedSince = -1, previous = -1;
    private int revision;

    public void power(boolean plugged) {
        if (powered == plugged) return;
        powered = plugged;
        if (plugged) parkedSince = -1;
        revision++;
    }
    public void observe(double mph, boolean freshLive, long now) {
        long oldParked = parkedSince;
        if (previous >= 0 && (now < previous || now - previous >= SpeedReading.MAX_AGE_MS)) parkedSince = -1;
        previous = now;
        if (!freshLive || !Double.isFinite(mph) || mph < 0) parkedSince = -1;
        else {
            if (!active && mph > DriveSession.MOVING_MPH) {
                active = true; maximum = mph; revision++;
            }
            if (active) {
                if (mph > maximum) { maximum = mph; revision++; }
                if (!powered && mph <= DriveSession.MOVING_MPH) {
                    if (parkedSince < 0) parkedSince = now;
                    if (now - parkedSince >= END_AFTER_MS) {
                        active = false; maximum = -1; parkedSince = -1; revision++;
                    }
                } else parkedSince = -1;
            }
        }
        if (oldParked != parkedSince) revision++;
    }
    public double maximumMph() { return maximum; }
    public boolean active() { return active; }
    public int revision() { return revision; }
    public void restore(double maximum, boolean active) {
        // Fresh stationary fixes must establish any pending end timer after recreation.
        this.active = active && Double.isFinite(maximum) && maximum >= 0;
        this.maximum = this.active ? maximum : -1;
        parkedSince = -1; previous = -1; revision++;
    }
}
