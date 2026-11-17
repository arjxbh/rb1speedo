package club.midlife.rb1speed;

/** Join two NV21 frames into one planar or semiplanar YUV420 encoder frame. */
final class DiagnosticPixels {
    static byte[] join(byte[] left, byte[] right, int width, int height, boolean planar) {
        if (width <= 0 || height <= 0 || (width & 1) != 0 || (height & 1) != 0 ||
            left.length != width * height * 3 / 2 || right.length != left.length)
            throw new IllegalArgumentException("Expected two equal, even-sized NV21 frames");
        int pixels = width * height;
        byte[] out = new byte[pixels * 3];
        for (int y = 0; y < height; y++) {
            System.arraycopy(left, y * width, out, y * width * 2, width);
            System.arraycopy(right, y * width, out, y * width * 2 + width, width);
        }
        for (int y = 0; y < height / 2; y++) for (int x = 0; x < width; x += 2) {
            int source = pixels + y * width + x;
            for (int side = 0; side < 2; side++) {
                byte[] frame = side == 0 ? left : right;
                int column = side * width / 2 + x / 2;
                if (planar) {
                    out[2 * pixels + y * width + column] = frame[source + 1];
                    out[2 * pixels + pixels / 2 + y * width + column] = frame[source];
                } else {
                    int dest = 2 * pixels + y * width * 2 + column * 2;
                    out[dest] = frame[source + 1]; out[dest + 1] = frame[source];
                }
            }
        }
        return out;
    }
}
