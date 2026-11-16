import club.midlife.rb1speed.PreviewPixels;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class PreviewPixelsTest {
    public static void main(String[] args) {
        byte[] expected = {1,2,3,4,5,6,7,8,21,11,22,12};
        ByteBuffer y = ByteBuffer.wrap(new byte[]{99,1,2,3,4,99,99,5,6,7,8}); y.position(1);
        ByteBuffer u = ByteBuffer.wrap(new byte[]{11,12});
        ByteBuffer v = ByteBuffer.wrap(new byte[]{21,22});
        byte[] planar = PreviewPixels.nv21(4, 2, new ByteBuffer[]{y,u,v}, new int[]{6,2,2}, new int[]{1,1,1});
        if (!Arrays.equals(planar, expected)) throw new AssertionError("Padded planar YUV must become NV21 VU order");
        ByteBuffer interleaved = ByteBuffer.wrap(new byte[]{21,11,22,12});
        ByteBuffer uShared = interleaved.duplicate(); uShared.position(1);
        byte[] shared = PreviewPixels.nv21(4, 2, new ByteBuffer[]{y,uShared,interleaved}, new int[]{6,4,4}, new int[]{1,2,2});
        if (!Arrays.equals(shared, expected)) throw new AssertionError("Shared chroma buffers and pixel stride 2 must work");
        if (y.position() != 1 || uShared.position() != 1) throw new AssertionError("Preserve source buffer positions");
        System.out.println("Preview pixels: all checks passed");
    }
}
