import club.midlife.rb1speed.CameraLens;
import java.util.*;
public class CameraLensTest {
    private static void check(boolean value, String why) { if (!value) throw new AssertionError(why); }
    public static void main(String[] args) {
        double ultra = CameraLens.fieldOfView(4.8, 3.6, 2, 1), main = CameraLens.fieldOfView(6.4, 4.8, 4.5, 1);
        double tele = CameraLens.fieldOfView(4, 3, 7, 1);
        check(ultra > main && main > tele, "Widest field wins regardless of camera ID");
        check(CameraLens.fieldOfView(1, 1, 2, 1) < main, "Focal length alone cannot compare different sensor sizes");
        check(CameraLens.fieldOfView(4, 2, 2, 1) < CameraLens.fieldOfView(4, 3, 2, 1), "Use actual 16:9 sensor crop");
        check(CameraLens.fieldOfView(6.4, 4.8, 4.5, .5) > main, "Logical zoom-out expands field");
        check(Double.isNaN(CameraLens.fieldOfView(0, 3, 2, 1)), "Missing sensor metadata is unknown");
        check(Double.isNaN(CameraLens.fieldOfView(4, 3, Double.NaN, 1)), "Reject invalid focal metadata");
        String[] keys = {"tele", "unknown", "main", "ultra"}; double[] fields = {tele, Double.NaN, main, ultra};
        Set<String> rejected = new HashSet<String>();
        check(CameraLens.choose(keys, fields, "auto", rejected) == 3, "Auto chooses widest");
        check(CameraLens.choose(keys, fields, "main", rejected) == 2, "Manual choice overrides widest");
        check(CameraLens.choose(keys, fields, "removed", rejected) == 3, "Missing saved lens falls back to widest");
        rejected.add("ultra"); check(CameraLens.choose(keys, fields, "auto", rejected) == 2, "Failed widest falls back to next widest");
        rejected.add("main"); rejected.add("tele"); check(CameraLens.choose(keys, fields, "auto", rejected) == 1, "Unknown metadata is a final usable fallback");
        rejected.add("unknown"); check(CameraLens.choose(keys, fields, "auto", rejected) == -1, "Stop after exhausting cameras");
        check(CameraLens.choose(new String[]{"b","a"}, new double[]{Double.NaN,Double.NaN}, "auto", new HashSet<String>()) == 1, "Missing metadata fallback is deterministic");
        System.out.println("Camera lenses: all checks passed");
    }
}
