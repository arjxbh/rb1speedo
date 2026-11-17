package club.midlife.rb1speed;

/** Camera ordering independent of Android; compare horizontal field of view at 16:9. */
public final class CameraLens {
    private CameraLens() {}
    public static double fieldOfView(double width, double height, double focal, double zoom) {
        if (!Double.isFinite(width) || !Double.isFinite(height) || !Double.isFinite(focal) || !Double.isFinite(zoom) ||
            width <= 0 || height <= 0 || focal <= 0 || zoom <= 0) return Double.NaN;
        double visibleWidth = Math.min(width, height * 16 / 9);
        return Math.toDegrees(2 * Math.atan(visibleWidth / (2 * focal * zoom)));
    }
    public static int compare(double first, double second) {
        boolean a = Double.isFinite(first), b = Double.isFinite(second);
        if (a != b) return a ? -1 : 1;
        return a ? -Double.compare(first, second) : 0;
    }
    public static int choose(String[] keys, double[] fields, String preference, java.util.Set<String> rejected) {
        for (int i = 0; i < keys.length; i++) if (keys[i].equals(preference) && !rejected.contains(keys[i])) return i;
        int best = -1;
        for (int i = 0; i < keys.length; i++) {
            if (rejected.contains(keys[i])) continue;
            if (best < 0 || compare(fields[i], fields[best]) < 0 ||
                (compare(fields[i], fields[best]) == 0 && keys[i].compareTo(keys[best]) < 0)) best = i;
        }
        return best;
    }
}
