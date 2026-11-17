package club.midlife.rb1speed;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

/** Real Camera2 probes, stop/reopen behavior, and isolation from dashcam preferences/storage. */
public final class CameraDiagnosticsInstrumentation extends Instrumentation {
    private Activity activity;
    private Context context;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try { exercise(); result.putString("stream","Camera diagnostics integration: all checks passed\n"); finish(Activity.RESULT_OK,result); }
        catch(Throwable e) { result.putString("stream","Camera diagnostics integration FAILED: "+android.util.Log.getStackTraceString(e)); finish(Activity.RESULT_CANCELED,result); }
    }
    private static void check(boolean ok,String why) { if(!ok) throw new AssertionError(why); }
    private void exercise() throws Exception {
        context=getTargetContext();
        SharedPreferences settings=context.getSharedPreferences("dashcam",0);
        boolean enabled=settings.getBoolean("enabled",false); String choice=CameraCatalog.preference(context);
        long retry=settings.getLong("cameraRetryAfter",0);
        settings.edit().putBoolean("enabled",false).commit();
        try {
            open(); click("Clear results"); click("Scan capabilities"); await("PASS: capability scan completed.",10000);
            check(report().contains("Fingerprint:"),"device report missing");
            check(report().contains("USB devices:"),"USB inventory missing");
            idle(); click("Open cameras only"); await("=== Tests finished ===",20000);
            check(report().contains("OPENED"),"no actual camera open observed");
            idle(); click("Clear results"); click("Single-camera baseline"); await("=== Tests finished ===",30000);
            check(report().contains("PASS camera="),"single-camera recording failed: "+report());
            check(report().contains("Temporary MP4"),"no recording file produced");
            for(String method:new String[]{"Dual YUV frames","Two encoders","One split-screen encoder","Camera1 preview"}) {
                Bundle progress=new Bundle(); progress.putString("stream","Testing "+method+"\n"); sendStatus(0,progress);
                idle(); click("Clear results"); click(method); await("=== Tests finished ===",100000);
                check(report().contains("TRY "+method),"probe was not attempted: "+method);
                check(report().contains("PASS") || report().contains("FAIL") || report().contains("SKIP"),"probe has no outcome: "+report());
            }
            idle(); click("Dual YUV frames"); SystemClock.sleep(600); click("Stop tests"); await("STOPPED:",10000);
            check(!DashcamService.diagnosticsBusy(),"dashcam still owns cameras during diagnostics");
            for(File f:context.getCacheDir().listFiles()) check(!f.getName().startsWith("camera-diag-"),"temporary footage leaked: "+f);
            check(!settings.getBoolean("enabled",true),"diagnostics changed recording-enabled preference");
            check(choice.equals(CameraCatalog.preference(context)),"camera preference changed");
            check(retry==settings.getLong("cameraRetryAfter",0),"recording retry preference changed");
            idle(); click("Open cameras only"); close();
            check(!DashcamService.diagnosticsActive,"late camera open retained diagnostic lease");
            String saved=report(); check(!DashcamService.diagnosticsActive,"diagnostic lease not released");
            open(); check(report().equals(saved),"report not preserved after reopening");
        } finally { close(); settings.edit().putBoolean("enabled",enabled).putString("cameraChoice",choice).putLong("cameraRetryAfter",retry).commit(); }
    }
    private void open() { activity=startActivitySync(new Intent(context,CameraDiagnosticsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
    private void close() { if(activity!=null) { final Activity old=activity; activity=null; runOnMainSync(new Runnable(){public void run(){old.finish();}}); SystemClock.sleep(800); } }
    private String report() { return context.getSharedPreferences("cameraDiagnostics",0).getString("report",""); }
    private void await(String text,long timeout) { long end=SystemClock.elapsedRealtime()+timeout; while(SystemClock.elapsedRealtime()<end) { if(report().contains(text)) return; SystemClock.sleep(100); } throw new AssertionError("Missing "+text+"\n"+report()); }
    private Button find(View view,String label) {
        if(view instanceof Button && label.equals(((Button)view).getText().toString())) return (Button)view;
        if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) { Button found=find(((ViewGroup)view).getChildAt(i),label); if(found!=null) return found; }
        return null;
    }
    private void idle() { SystemClock.sleep(1500); }
    private void click(final String label) { runOnMainSync(new Runnable(){public void run(){Button button=find(activity.findViewById(android.R.id.content),label); check(button!=null && button.isEnabled(),"button unavailable: "+label); button.performClick();}}); }
}
