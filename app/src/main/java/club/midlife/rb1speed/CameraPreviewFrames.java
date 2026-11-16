package club.midlife.rb1speed;

import android.graphics.*;
import android.media.*;
import android.os.*;
import android.util.Size;
import android.view.Surface;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/** Low-resolution aiming frames, decoded only while a visible setup screen subscribes. */
final class CameraPreviewFrames {
    interface Listener { void frame(Bitmap bitmap); }
    private final ImageReader reader;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean pending = new AtomicBoolean();
    private volatile Listener listener;
    private long last;
    CameraPreviewFrames(Size size, final int orientation, Handler cameraHandler) {
        reader = ImageReader.newInstance(size.getWidth(), size.getHeight(), ImageFormat.YUV_420_888, 3);
        reader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            public void onImageAvailable(ImageReader source) {
                try (Image image = source.acquireLatestImage()) {
                    final Listener target = listener;
                    long now = SystemClock.elapsedRealtime();
                    if (image == null || target == null || now - last < 250 || pending.get()) return;
                    last = now;
                    Image.Plane[] p = image.getPlanes();
                    byte[] nv21 = PreviewPixels.nv21(image.getWidth(), image.getHeight(),
                        new ByteBuffer[]{p[0].getBuffer(), p[1].getBuffer(), p[2].getBuffer()},
                        new int[]{p[0].getRowStride(), p[1].getRowStride(), p[2].getRowStride()},
                        new int[]{p[0].getPixelStride(), p[1].getPixelStride(), p[2].getPixelStride()});
                    ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
                    // Center crop a 4:3 fallback stream to the recorder's 16:9 framing.
                    int width = image.getWidth(), height = image.getHeight();
                    int cropHeight = Math.min(height, width * 9 / 16) & ~1;
                    int cropWidth = Math.min(width, height * 16 / 9) & ~1;
                    Rect crop = new Rect(((width - cropWidth) / 2) & ~1, ((height - cropHeight) / 2) & ~1, 0, 0);
                    crop.right = crop.left + cropWidth; crop.bottom = crop.top + cropHeight;
                    new YuvImage(nv21, ImageFormat.NV21, width, height, null).compressToJpeg(crop, 70, jpeg);
                    byte[] encoded = jpeg.toByteArray();
                    Bitmap raw = BitmapFactory.decodeByteArray(encoded, 0, encoded.length);
                    if (raw == null) return;
                    Matrix rotation = new Matrix(); rotation.postRotate(orientation);
                    final Bitmap bitmap = Bitmap.createBitmap(raw, 0, 0, raw.getWidth(), raw.getHeight(), rotation, true);
                    if (raw != bitmap) raw.recycle();
                    pending.set(true);
                    main.post(new Runnable() { public void run() {
                        try { if (listener == target) target.frame(bitmap); else bitmap.recycle(); }
                        finally { pending.set(false); }
                    }});
                } catch (RuntimeException ignored) { /* A preview frame must never stop video recording. */ }
            }
        }, cameraHandler);
    }
    static Size choose(Size[] sizes) {
        Size best = null;
        if (sizes == null) return null;
        for (int pass = 0; pass < 2 && best == null; pass++) for (Size size : sizes) {
            int w = size.getWidth(), h = size.getHeight();
            if (w < 320 || w > 1280 || h > 720 || (w & 1) != 0 || (h & 1) != 0 || (pass == 0 && w * 9 != h * 16)) continue;
            if (best == null || (long) w * h < (long) best.getWidth() * best.getHeight()) best = size;
        }
        return best;
    }
    Surface surface() { return reader.getSurface(); }
    void listen(Listener value) { listener = value; }
    void close() { listener = null; reader.close(); }
}
