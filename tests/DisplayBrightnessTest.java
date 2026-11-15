import club.midlife.rb1speed.DisplayBrightness;
import java.time.Instant;

public class DisplayBrightnessTest {
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static boolean night(String time, double lat, double lon) {
        return DisplayBrightness.isNight(Instant.parse(time).toEpochMilli(), lat, lon);
    }
    public static void main(String[] args) {
        check(DisplayBrightness.level(Float.NaN, true) == .08f, "Night fallback");
        check(DisplayBrightness.level(Float.NaN, false) == -1f, "Day uses system brightness");
        check(DisplayBrightness.level(0, false) == .06f, "Dark cabin overrides daylight");
        check(DisplayBrightness.level(10000, true) == 1f, "Bright light overrides clock");
        float previous = 0;
        for (int lux = 0; lux <= 20000; lux += 10) {
            float value = DisplayBrightness.level(lux, false);
            check(value >= previous && value <= 1, "Bounded monotonic brightness"); previous = value;
        }
        check(!night("2026-11-15T17:00:00Z", 40.7, -74), "New York noon");
        check(night("2026-11-15T05:00:00Z", 40.7, -74), "New York midnight");
        check(!night("2026-06-21T00:00:00Z", 89, 0), "Polar summer");
        check(night("2026-12-21T12:00:00Z", 89, 0), "Polar winter");
        check(!night("2026-03-20T03:00:00Z", 35.7, 139.7), "Tokyo noon east longitude");
        System.out.println("Display brightness: all checks passed");
    }
}
