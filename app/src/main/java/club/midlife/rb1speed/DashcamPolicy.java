package club.midlife.rb1speed;

/** Dashcam defaults and decisions that can be verified without Android. */
public final class DashcamPolicy {
    public static final long CLIP_US = 15 * 60 * 1000000L;
    public static final long FRAGMENT_US = 60 * 1000000L;
    public static final long QUOTA_BYTES = 32L * 1024 * 1024 * 1024;
    public static final long RESERVE_BYTES = 5L * 1024 * 1024 * 1024;
    public static final long PART_BYTES = 32L * 1024 * 1024;
    public static final long BATTERY_BUDGET_MS = 30 * 60 * 1000L;
    private DashcamPolicy() {}
    public static boolean recordingFresh(long lastFrame, long now) {
        return lastFrame > 0 && now >= lastFrame && now - lastFrame < 3000;
    }
    public static boolean uploadAllowed(boolean wifi, boolean charging, int battery, long now, long deadline) {
        return wifi && (charging || (battery > 30 && now < deadline));
    }
    public static boolean cut(long elapsedUs, boolean keyframe, long targetUs) {
        return keyframe && elapsedUs >= targetUs;
    }
    public static int partCount(long size) { return (int)((size + PART_BYTES - 1) / PART_BYTES); }
    public static long powerStopAt(boolean plugged, long now, long previousDeadline) {
        return plugged ? -1 : previousDeadline < 0 ? now + 10000 : previousDeadline;
    }
}
