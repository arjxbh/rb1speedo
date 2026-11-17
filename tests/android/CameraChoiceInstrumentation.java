package club.midlife.rb1speed;

import android.app.*;
import android.content.*;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.view.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Actual Camera2 preview and service switching, including emulators without a 1080p encoder route. */
public final class CameraChoiceInstrumentation extends Instrumentation {
    private Activity activity;
    private static void check(boolean value, String why) { if (!value) throw new AssertionError(why); }
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try { exercise(); result.putString("stream", "Camera selection integration: all checks passed\n"); finish(Activity.RESULT_OK, result); }
        catch (Throwable e) { result.putString("stream", "Camera selection integration FAILED: " + android.util.Log.getStackTraceString(e)); finish(Activity.RESULT_CANCELED, result); }
    }
    private void exercise() throws Exception {
        final Context context = getTargetContext(); final SharedPreferences p = context.getSharedPreferences("dashcam", 0);
        String saved = CameraCatalog.preference(context), label = p.getString("cameraChoiceLabel", "Automatic (widest)");
        boolean enabled = p.getBoolean("enabled", false); long retry = p.getLong("cameraRetryAfter", 0);
        List<CameraCatalog.Option> options = CameraCatalog.available(context);
        if (options.isEmpty()) options = CameraCatalog.available(context, false);
        check(!options.isEmpty(), "rear camera must expose preview");
        CameraCatalog.Option manual = options.get(options.size() - 1);
        try {
            p.edit().putBoolean("enabled", false).putString("cameraChoice", manual.key).putString("cameraChoiceLabel", manual.label())
                .remove("activeCamera").remove("cameraSelectionStatus").commit();
            open(); frames(p, manual.label(), "Manual selection"); close();
            check(manual.key.equals(CameraCatalog.preference(context)), "manual preference persists across service teardown");
            open(); frames(p, manual.label(), "Manual selection");
            p.edit().putString("cameraChoice", CameraCatalog.AUTO).putString("cameraChoiceLabel", "Automatic (widest)").commit();
            context.startService(new Intent(context, DashcamService.class).setAction(DashcamService.CHANGE_CAMERA));
            CameraCatalog.Option widest = CameraCatalog.choose(options, CameraCatalog.AUTO, new HashSet<String>());
            frames(p, widest.label(), "Automatic · widest available");
            check(!DashcamService.running() && !DashcamService.recording(), "preview-only switching must not enable recording");
            List<CameraCatalog.Option> fronts=CameraCatalog.available(context,false,android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT);
            check(!fronts.isEmpty(),"front preview route missing");
            context.startService(new Intent(context,DashcamService.class).setAction(DashcamService.FLIP));
            CameraCatalog.Option front=CameraCatalog.choose(fronts,CameraCatalog.AUTO,new HashSet<String>());
            frames(p,front.label(),"Automatic · widest available");
            check(DashcamService.front(),"flip did not select front");
            check(!DashcamService.running(),"flip enabled recording from preview");
            context.startService(new Intent(context,DashcamService.class).setAction(DashcamService.FLIP));
            frames(p,widest.label(),"Automatic · widest available");
            check(!DashcamService.front(),"flip did not return to rear");
            close();
        } finally {
            close();
            p.edit().putString("cameraChoice", saved).putString("cameraChoiceLabel", label).putBoolean("enabled", enabled).putLong("cameraRetryAfter", retry).commit();
        }
    }
    private void open() {
        activity = startActivitySync(new Intent(getTargetContext(), DashcamActivity.class).putExtra("preview", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
    private void close() {
        final Activity old = activity; activity = null;
        if (old != null) { runOnMainSync(new Runnable() { public void run() { old.finish(); }}); SystemClock.sleep(800); }
    }
    private CameraPreviewView preview(View view) {
        if (view instanceof CameraPreviewView) return (CameraPreviewView)view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i = 0; i < group.getChildCount(); i++) { CameraPreviewView result = preview(group.getChildAt(i)); if (result != null) return result; }
        }
        return null;
    }
    private void frames(SharedPreferences p, String label, String state) {
        long deadline = SystemClock.elapsedRealtime() + 15000;
        Set<Drawable> received = Collections.newSetFromMap(new IdentityHashMap<Drawable, Boolean>());
        while (SystemClock.elapsedRealtime() < deadline) {
            if (label.equals(p.getString("activeCamera", "")) && state.equals(p.getString("cameraSelectionStatus", ""))) {
                final AtomicReference<Drawable> image = new AtomicReference<Drawable>();
                runOnMainSync(new Runnable() { public void run() {
                    CameraPreviewView view = preview(activity.findViewById(android.R.id.content)); if (view != null) image.set(view.getDrawable());
                }});
                if (image.get() != null) received.add(image.get());
                if (received.size() >= 2) return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("No fresh preview frames for " + label + " / " + state + "; actual " + p.getString("activeCamera", "") + " / " + p.getString("previewStatus", ""));
    }
}
