package club.midlife.rb1speed;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.*;
import android.os.*;
import android.util.Range;
import android.util.Size;
import android.view.*;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.io.File;

/** One continuous hardware encoder, with MP4 fragments cut only at sync frames. */
final class CameraRecorder {
    interface Listener { void stopped(String reason); }
    private final Context context;
    private final ClipStore store;
    private final Listener listener;
    private final HandlerThread thread = new HandlerThread("dashcam-camera");
    private Handler handler;
    private CameraDevice camera;
    private CameraCaptureSession session;
    private MediaCodec encoder;
    private Surface surface;
    private MediaMuxer mux;
    private MediaFormat format;
    private int track, orientation;
    private String clip, fragment, reason = "Stopped";
    private long origin = -1, groupStart, fragmentStart, lastSample, wall, stopDeadline;
    private boolean closing, finished, started, opening;
    private Range<Integer> frameRange;
    private boolean continuousFocus;
    CameraRecorder(Context context, Listener listener) { this.context = context; this.listener = listener; store = ClipStore.get(context); }
    void start() {
        thread.start(); handler = new Handler(thread.getLooper());
        handler.post(new Runnable() { public void run() { open(); }});
    }
    void stop(final String reason) {
        if (handler != null) handler.post(new Runnable() { public void run() { requestStop(reason); }});
    }
    private void open() {
        try {
            store.recover(); ClipAssembler.enqueue(context, false);
            android.content.Intent battery = context.registerReceiver(null, new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
            if (battery != null && battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) >= 450) throw new IllegalStateException("Battery temperature too high");
            if (Build.VERSION.SDK_INT >= 29 && ((PowerManager)context.getSystemService(Context.POWER_SERVICE)).getCurrentThermalStatus() >= PowerManager.THERMAL_STATUS_SEVERE)
                throw new IllegalStateException("Device thermal protection active");
            if (!store.makeSpace()) throw new IllegalStateException("Storage quota or free-space reserve reached");
            CameraManager manager = (CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
            String selected = null; CameraCharacteristics chosen = null;
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics c = manager.getCameraCharacteristics(id);
                if (c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) { selected = id; chosen = c; break; }
            }
            if (selected == null) throw new IllegalStateException("Rear camera unavailable");
            StreamConfigurationMap map = chosen.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            boolean supported = false;
            for (Size size : map.getOutputSizes(MediaCodec.class)) if (size.getWidth() == 1920 && size.getHeight() == 1080) supported = true;
            if (!supported) throw new IllegalStateException("Rear camera does not expose 1080p recording");
            Range<Integer>[] ranges = chosen.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
            if (ranges != null) for (Range<Integer> range : ranges) {
                if (range.contains(30) && (frameRange == null || range.getLower() > frameRange.getLower() ||
                    (range.getLower().equals(frameRange.getLower()) && range.getUpper() < frameRange.getUpper()))) frameRange = range;
            }
            if (frameRange == null) throw new IllegalStateException("30 fps camera mode unavailable");
            int[] focusModes = chosen.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
            if (focusModes != null) for (int mode : focusModes) if (mode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO) continuousFocus = true;
            int rotation = ((WindowManager)context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
            orientation = (chosen.get(CameraCharacteristics.SENSOR_ORIENTATION) - rotation * 90 + 360) % 360;
            MediaFormat config = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1920, 1080);
            config.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            config.setInteger(MediaFormat.KEY_BIT_RATE, 8000000); config.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
            config.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            String codecName = encoder.getName();
            if ((Build.VERSION.SDK_INT >= 29 && !encoder.getCodecInfo().isHardwareAccelerated()) || codecName.startsWith("OMX.google.") || codecName.startsWith("c2.android."))
                throw new IllegalStateException("Hardware video encoder unavailable");
            encoder.configure(config, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            surface = encoder.createInputSurface(); encoder.start(); started = true; wall = System.currentTimeMillis();
            opening = true;
            manager.openCamera(selected, new CameraDevice.StateCallback() {
                public void onOpened(CameraDevice device) {
                    opening = false;
                    if (finished || closing) { device.close(); if (finished) thread.quitSafely(); return; }
                    camera = device;
                    try {
                        camera.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {
                            public void onConfigured(CameraCaptureSession capture) {
                                if (finished || closing) { capture.close(); return; }
                                session = capture;
                                try {
                                    CaptureRequest.Builder request = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                                    request.addTarget(surface);
                                    request.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                                    request.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, frameRange);
                                    if (continuousFocus) request.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
                                    session.setRepeatingRequest(request.build(), null, handler);
                                } catch (Exception e) { requestStop("Camera capture failed: " + e.getClass().getSimpleName()); }
                            }
                            public void onConfigureFailed(CameraCaptureSession capture) { requestStop("Camera configuration failed"); }
                        }, handler);
                    } catch (Exception e) { requestStop("Camera session failed: " + e.getClass().getSimpleName()); }
                }
                public void onDisconnected(CameraDevice device) { opening = false; device.close(); if (finished) thread.quitSafely(); requestStop("Camera disconnected"); }
                public void onError(CameraDevice device, int error) { opening = false; device.close(); if (finished) thread.quitSafely(); requestStop("Camera error " + error); }
            }, handler);
            handler.post(drain);
        } catch (Exception e) { opening = false; reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); finish(); }
    }
    private void requestStop(String why) {
        if (finished || closing) return;
        reason = why; closing = true; stopDeadline = SystemClock.elapsedRealtime() + 4000;
        if (session != null) { session.close(); session = null; }
        if (camera != null) { camera.close(); camera = null; }
        if (encoder != null && started) try { encoder.signalEndOfInputStream(); } catch (RuntimeException ignored) {}
        if (!started) finish();
    }
    private final Runnable drain = new Runnable() { public void run() {
        if (finished) return;
        try {
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            for (int i = 0; i < 12; i++) {
                int index = encoder.dequeueOutputBuffer(info, 0);
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { format = encoder.getOutputFormat(); continue; }
                if (index < 0) break;
                try {
                    if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) sample(encoder.getOutputBuffer(index), info);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) { finish(); return; }
                } finally { if (!finished) encoder.releaseOutputBuffer(index, false); }
            }
            if (closing && SystemClock.elapsedRealtime() >= stopDeadline) { finish(); return; }
            handler.postDelayed(this, 10);
        } catch (Exception e) { reason = "Recording failed: " + e.getClass().getSimpleName(); finish(); }
    }};
    private void sample(ByteBuffer data, MediaCodec.BufferInfo info) throws Exception {
        if (closing && mux == null) return;
        boolean keyframe = (info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0;
        if (origin < 0) { if (!keyframe) return; origin = info.presentationTimeUs; wall = System.currentTimeMillis();
            DashcamService.report(context, "Recording rear camera · 1080p / H.264");
            context.getSharedPreferences("dashcam", 0).edit().putLong("recordingStarted", wall).apply(); }
        long pts = info.presentationTimeUs;
        if (mux == null) {
            clip = store.beginClip(wall + (pts - origin) / 1000); groupStart = pts; openFragment(pts);
        } else if (DashcamPolicy.cut(pts - fragmentStart, keyframe, DashcamPolicy.FRAGMENT_US) ||
            DashcamPolicy.cut(pts - groupStart, keyframe, DashcamPolicy.CLIP_US)) {
            closeFragment(pts);
            if (DashcamPolicy.cut(pts - groupStart, true, DashcamPolicy.CLIP_US)) {
                store.seal(clip); ClipAssembler.enqueue(context, false);
                clip = store.beginClip(wall + (pts - origin) / 1000); groupStart = pts;
            }
            if (!store.makeSpace()) { requestStop("Storage quota or free-space reserve reached"); return; }
            openFragment(pts);
        }
        if (!keyframe && pts - fragmentStart >= DashcamPolicy.FRAGMENT_US - 500000) {
            Bundle request = new Bundle(); request.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0); encoder.setParameters(request);
        }
        MediaCodec.BufferInfo adjusted = new MediaCodec.BufferInfo();
        adjusted.set(info.offset, info.size, pts - fragmentStart, info.flags);
        mux.writeSampleData(track, data, adjusted); lastSample = pts;
    }
    private void openFragment(long pts) throws Exception {
        fragment = store.beginFragment(clip); fragmentStart = pts;
        mux = new MediaMuxer(store.fragmentFile(fragment).getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        mux.setOrientationHint(orientation); track = mux.addTrack(format); mux.start();
    }
    private void closeFragment(long end) {
        if (mux == null) return;
        MediaMuxer current = mux; mux = null;
        try {
            current.stop(); current.release();
            store.finishFragment(fragment, Math.max(33333, end - fragmentStart));
        } catch (RuntimeException e) { try { current.release(); } catch (RuntimeException ignored) {} throw e; }
    }
    private void finish() {
        if (finished) return; finished = true;
        if (handler != null) handler.removeCallbacksAndMessages(null);
        if (session != null) session.close(); if (camera != null) camera.close();
        try { closeFragment(lastSample + 33333); }
        catch (RuntimeException e) { reason = "Final fragment could not be finalized"; }
        if (clip != null) store.seal(clip);
        if (encoder != null) { try { if (started) encoder.stop(); } catch (RuntimeException ignored) {} encoder.release(); }
        if (surface != null) surface.release();
        ClipAssembler.enqueue(context, false); listener.stopped(reason);
        // An asynchronous open callback must still be allowed to close its returned camera.
        if (!opening) thread.quitSafely();
        else handler.postDelayed(new Runnable() { public void run() { thread.quitSafely(); } }, 15000);
    }
}
