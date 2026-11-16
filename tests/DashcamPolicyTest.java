import club.midlife.rb1speed.DashcamPolicy;
public class DashcamPolicyTest {
    private static void check(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
    public static void main(String[] args) {
        check(!DashcamPolicy.recordingFresh(0, 1000), "Starting a service does not mean video is recording");
        check(DashcamPolicy.recordingFresh(1000, 3999), "Recent written frames show recording");
        check(!DashcamPolicy.recordingFresh(1000, 4000), "Stalled video stops showing REC after three seconds");
        check(!DashcamPolicy.recordingFresh(1000, 999), "Invalid frame clocks cannot show recording");
        check(!DashcamPolicy.cut(DashcamPolicy.CLIP_US, false, DashcamPolicy.CLIP_US), "Never cut between dependent frames");
        check(!DashcamPolicy.cut(DashcamPolicy.CLIP_US - 1, true, DashcamPolicy.CLIP_US), "Wait for full segment");
        check(DashcamPolicy.cut(DashcamPolicy.CLIP_US, true, DashcamPolicy.CLIP_US), "Cut at next sync frame");
        check(!DashcamPolicy.uploadAllowed(false, true, 100, 0, 1), "No cellular upload even while powered");
        check(DashcamPolicy.uploadAllowed(true, false, 31, 0, 1), "Upload after ignition off");
        check(!DashcamPolicy.uploadAllowed(true, false, 30, 0, 1), "Stop at battery floor");
        check(!DashcamPolicy.uploadAllowed(true, false, 90, 1, 1), "Stop at persisted battery deadline");
        check(DashcamPolicy.uploadAllowed(true, true, 10, 100, 1), "Charging bypasses battery-session budget");
        check(DashcamPolicy.partCount(DashcamPolicy.PART_BYTES) == 1, "Exact part size");
        check(DashcamPolicy.partCount(DashcamPolicy.PART_BYTES + 1) == 2, "Small final part");
        long stopAt = DashcamPolicy.powerStopAt(false, 1000, -1);
        check(stopAt == 11000, "Ten-second disconnect debounce");
        check(DashcamPolicy.powerStopAt(false, 9000, stopAt) == stopAt, "Battery broadcasts cannot extend recording after parking");
        check(DashcamPolicy.powerStopAt(true, 10000, stopAt) == -1, "Quick power return cancels shutdown");
        check(DashcamPolicy.powerStopAt(false, 12000, -1) == 22000, "A later disconnect gets a fresh debounce");
        System.out.println("Dashcam policy: all checks passed");
    }
}
