import club.midlife.rb1speed.*;

public class DrivingFeaturesTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    public static void main(String[] args) {
        DriveSession session = new DriveSession();
        check(session.demoVisible(), "Demo initially available");
        for (long t = 0; t <= 20000; t += 500) session.update(1, true, t);
        check(session.demoVisible(), "GPS jitter at 1 mph does not hide demo");
        for (long t = 21000; t < 31000; t += 500) session.update(20, true, t);
        check(session.demoVisible(), "Do not hide before ten seconds");
        session.update(20, true, 31000);
        check(!session.demoVisible(), "Hide at ten seconds");
        session.update(0, false, 32000);
        check(!session.demoVisible(), "Keep hidden for foreground session");
        session.reset();
        check(session.demoVisible(), "Resume restores demo");
        for (long t = 0; t < 9000; t += 500) session.update(10, true, t);
        session.update(0, true, 9000);
        for (long t = 9500; t < 19500; t += 500) session.update(10, true, t);
        check(session.demoVisible(), "Stopping restarts the interval");
        session.update(10, false, 19500);
        for (long t = 20000; t < 30000; t += 500) session.update(10, true, t);
        check(session.demoVisible(), "Unreliable GPS restarts the interval");
        session.reset();
        session.update(10, true, 0); session.update(10, true, 10000);
        check(session.demoVisible(), "A timer gap cannot count as continuous movement");
        for (long t = 10500; t < 30000; t += 500) session.update(80, false, t);
        check(session.demoVisible(), "Demo speed cannot hide its exit control");

        SpeedWarning warning = new SpeedWarning();
        check(!warning.update(74.99, true, 0), "Use unrounded 75 mph threshold");
        check(warning.update(75, true, 100), "Chime at 75 mph");
        check(!warning.update(80, true, 1599), "Do not repeat too early");
        check(warning.update(80, true, 1600), "Repeat after 1.5 seconds");
        check(warning.update(74, true, 3100), "Hysteresis prevents threshold flutter");
        check(!warning.update(72.99, true, 3200) && !warning.active(), "Stop below 73 mph");
        check(!warning.update(74, true, 3300), "Do not start inside hysteresis margin");
        check(warning.update(75, true, 3400), "Rearm after slowing down");
        check(!warning.update(90, false, 3500) && !warning.active(), "Invalid, stale, demo or paused source stops warning");
        check(!warning.update(Double.NaN, true, 3600), "Reject invalid speed");
        check(!warning.update(Double.POSITIVE_INFINITY, true, 3700), "Reject infinite speed");
        check(warning.update(130, true, 3800), "Analog cap does not cap warning");
        warning.reset();
        check(!warning.active() && !warning.update(74, true, 3900), "Session reset clears warning");
        check(warning.update(75, true, 4000), "Start before reception gap");
        check(!warning.update(74, true, 8000), "Reception gap clears latched warning");

        SpeedReading reading = new SpeedReading();
        reading.accept(75 / SpeedReading.MPH_PER_MPS, true, 5, true, .1f, 10000, 10000);
        check(warning.update(reading.mph(), reading.fresh(10000), 10000), "Accepted live GPS triggers warning");
        check(!warning.update(reading.mph(), reading.fresh(14000), 14000) && !warning.active(), "Exactly four seconds expires warning");

        short[] pcm = ChimeSound.samples();
        check(pcm.length == ChimeSound.SAMPLE_RATE * ChimeSound.DURATION_MS / 1000, "Complete chime duration");
        check(pcm[0] == 0 && pcm[pcm.length - 1] == 0, "Fade starts and ends at silence");
        long earlyEnergy = 0, lateEnergy = 0;
        int peak = 0;
        for (int i = 0; i < pcm.length; i++) {
            peak = Math.max(peak, Math.abs((int) pcm[i]));
            if (i < pcm.length / 3) earlyEnergy += (long) pcm[i] * pcm[i];
            if (i >= pcm.length * 2 / 3) lateEnergy += (long) pcm[i] * pcm[i];
        }
        check(peak > 1000 && peak < 30000, "Audible chime with amplitude headroom");
        check(earlyEnergy > lateEnergy * 10, "Bell decays rather than ending abruptly");
        System.out.println("Driving features: all checks passed");
    }
}
