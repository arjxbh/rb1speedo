package club.midlife.rb1speed;

import android.content.*;
import android.graphics.ImageFormat;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaCodec;
import android.os.Build;
import android.util.*;
import java.util.*;

/** Available 1080p/30 rear-camera routes, including physical lenses behind logical cameras. */
final class CameraCatalog {
    static final String AUTO = "auto";
    static final class Option {
        final String key, openId, physicalId;
        final CameraCharacteristics characteristics;
        final float zoom, focal;
        final double field;
        Option(String openId, String physicalId, CameraCharacteristics c, float zoom) {
            this.openId = openId; this.physicalId = physicalId; characteristics = c; this.zoom = zoom;
            key = physicalId == null ? "camera:" + openId + ":" + zoom : "physical:" + openId + ":" + physicalId;
            float smallest = Float.NaN; float[] focals = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
            if (focals != null) for (float value : focals) if (value > 0 && (!Float.isFinite(smallest) || value < smallest)) smallest = value;
            focal = smallest;
            SizeF sensor = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
            field = sensor == null ? Double.NaN : CameraLens.fieldOfView(sensor.getWidth(), sensor.getHeight(), focal, zoom);
        }
        String label() {
            String lens = physicalId == null ? "Rear camera " + openId : "Rear lens " + physicalId + " (camera " + openId + ")";
            if (zoom < 1) lens += String.format(Locale.US, " · %.2f× wide", zoom);
            return lens + (Double.isFinite(field) ? String.format(Locale.US, " · ≈%.0f°", field) : " · field of view unavailable");
        }
    }
    static String preference(Context context) { return context.getSharedPreferences("dashcam", 0).getString("cameraChoice", AUTO); }
    static List<Option> available(Context context) throws CameraAccessException {
        return available(context, true);
    }
    static List<Option> available(Context context, boolean recording) throws CameraAccessException {
        CameraManager manager = (CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
        List<Option> result = new ArrayList<Option>();
        String[] ids = manager.getCameraIdList(); Arrays.sort(ids);
        Set<String> direct = new HashSet<String>(Arrays.asList(ids));
        for (String id : ids) {
            CameraCharacteristics c;
            try { c = manager.getCameraCharacteristics(id); } catch (CameraAccessException | IllegalArgumentException | SecurityException e) { continue; }
            Integer facing = c.get(CameraCharacteristics.LENS_FACING);
            if (facing == null || facing != CameraCharacteristics.LENS_FACING_BACK) continue;
            if (recording ? usable(c) : previewable(c)) {
                result.add(new Option(id, null, c, 1f));
                if (Build.VERSION.SDK_INT >= 30) {
                    Range<Float> range = c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
                    if (range != null && range.getLower() > 0 && range.getLower() < 1f)
                        result.add(new Option(id, null, c, range.getLower()));
                }
            }
            if (Build.VERSION.SDK_INT >= 29) {
                List<String> physical = new ArrayList<String>(c.getPhysicalCameraIds()); Collections.sort(physical);
                for (String lens : physical) {
                    if (direct.contains(lens)) continue;
                    try {
                        CameraCharacteristics p = manager.getCameraCharacteristics(lens);
                        Integer side = p.get(CameraCharacteristics.LENS_FACING);
                        if (side != null && side == CameraCharacteristics.LENS_FACING_BACK && (recording ? usable(p) : previewable(p))) result.add(new Option(id, lens, p, 1f));
                    } catch (CameraAccessException | IllegalArgumentException | SecurityException e) { /* Firmware may hide auxiliary metadata. */ }
                }
            }
        }
        Collections.sort(result, new Comparator<Option>() { public int compare(Option a, Option b) {
            int field = CameraLens.compare(a.field, b.field); return field != 0 ? field : a.key.compareTo(b.key);
        }});
        return result;
    }
    private static boolean previewable(CameraCharacteristics c) {
        StreamConfigurationMap map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        return map != null && CameraPreviewFrames.choose(map.getOutputSizes(ImageFormat.YUV_420_888)) != null;
    }
    private static boolean usable(CameraCharacteristics c) {
        StreamConfigurationMap map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) return false;
        Size[] sizes = map.getOutputSizes(MediaCodec.class); boolean video = false;
        if (sizes != null) for (Size size : sizes) if (size.getWidth() == 1920 && size.getHeight() == 1080) video = true;
        Range<Integer>[] ranges = c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES); boolean fps = false;
        if (ranges != null) for (Range<Integer> range : ranges) if (range.contains(30)) fps = true;
        return video && fps;
    }
    static Option choose(List<Option> options, String preference, Set<String> rejected) {
        String[] keys = new String[options.size()]; double[] fields = new double[options.size()];
        for (int i = 0; i < options.size(); i++) { keys[i] = options.get(i).key; fields[i] = options.get(i).field; }
        int index = CameraLens.choose(keys, fields, preference, rejected);
        return index < 0 ? null : options.get(index);
    }
}
