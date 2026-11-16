package club.midlife.rb1speed;

import java.nio.ByteBuffer;

/** Camera YUV planes may have padding and shared, interleaved chroma buffers. */
public final class PreviewPixels {
    public static byte[] nv21(int width, int height, ByteBuffer[] planes, int[] rows, int[] pixels) {
        if (width <= 0 || height <= 0 || (width & 1) != 0 || (height & 1) != 0) throw new IllegalArgumentException("Even preview dimensions required");
        byte[] result = new byte[width * height * 3 / 2];
        for (int p = 0; p < 3; p++) {
            ByteBuffer source = planes[p].duplicate(); int base = source.position();
            int w = p == 0 ? width : width / 2, h = p == 0 ? height : height / 2;
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int dest = p == 0 ? y * width + x : width * height + y * width + x * 2 + (p == 1 ? 1 : 0);
                result[dest] = source.get(base + y * rows[p] + x * pixels[p]);
            }
        }
        return result;
    }
}
