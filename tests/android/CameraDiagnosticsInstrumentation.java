package club.midlife.rb1speed;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

/** Real Camera2 probes, stop/reopen behavior, and isolation from dashcam preferences/storage. */
public class CameraDiagnosticsInstrumentation extends Instrumentation {
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
            open(); click("Clear results"); click("Scan rear lenses"); await("DONE: Scan rear lenses",10000);
            check(report().contains("Fingerprint:"),"device report missing");
            check(report().contains("Package: "+context.getPackageName()),"package identity missing");
            check(report().contains("estimated horizontal field="),"lens field missing");
            check(report().contains("zoom="),"zoom range missing");
            idle(); click("Probe camera IDs 0–9"); await("DONE: Probe camera IDs 0–9",10000);
            check(report().contains("Camera 9"),"bounded hidden ID scan incomplete");
            idle(); click("Package access info"); await("DONE: Package access info",10000);
            check(report().contains("vendor.camera.aux.packagelist"),"allowlist diagnosis missing");
            idle(); chooseFirstLens(); await("PASS · frames=",20000);
            check(report().contains("OPENED"),"no real camera was opened");
            idle(); chooseFirstLens(); SystemClock.sleep(500); click("Stop probe"); await("STOPPED:",10000);
            check(!settings.getBoolean("enabled",true),"debug changed enabled preference");
            check(choice.equals(CameraCatalog.preference(context)),"debug changed lens preference");
            check(retry==settings.getLong("cameraRetryAfter",0),"debug changed retry preference");
            idle(); chooseFirstLens(); close();
            check(!DashcamService.diagnosticsActive,"late open retained diagnostic lease");
            String saved=report(); open(); check(report().equals(saved),"report not preserved after reopening");
        } finally { close(); settings.edit().putBoolean("enabled",enabled).putString("cameraChoice",choice).putLong("cameraRetryAfter",retry).commit(); }
    }
    private void chooseFirstLens() {
        runOnMainSync(new Runnable(){public void run(){
            Button button=findPrefix(activity.findViewById(android.R.id.content),"Preview a lens");
            check(button!=null && button.isEnabled(),"preview button unavailable");button.performClick();
            AlertDialog dialog=((CameraDiagnosticsActivity)activity).lensDialog;
            check(dialog!=null,"lens dialog missing");
            ListView list=dialog.getListView(); check(list.getAdapter().getCount()>0,"no lens candidates");
            list.performItemClick(list.getChildAt(0),0,list.getAdapter().getItemId(0));
        }});
    }
    private Button findPrefix(View view,String prefix) {
        if(view instanceof Button && ((Button)view).getText().toString().startsWith(prefix))return (Button)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){Button b=findPrefix(((ViewGroup)view).getChildAt(i),prefix);if(b!=null)return b;}
        return null;
    }
    private void open() { activity=startActivitySync(new Intent(context,CameraDiagnosticsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
    private void close() { if(activity!=null) { final Activity old=activity; activity=null; runOnMainSync(new Runnable(){public void run(){old.finish();}}); SystemClock.sleep(800); } }
    private String report() { return context.getSharedPreferences("lensDiagnostics",0).getString("report",""); }
    private void await(String text,long timeout) { long end=SystemClock.elapsedRealtime()+timeout; while(SystemClock.elapsedRealtime()<end) { if(report().contains(text)) return; SystemClock.sleep(100); } throw new AssertionError("Missing "+text+"\n"+report()); }
    private Button find(View view,String label) {
        if(view instanceof Button && label.equals(((Button)view).getText().toString())) return (Button)view;
        if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) { Button found=find(((ViewGroup)view).getChildAt(i),label); if(found!=null) return found; }
        return null;
    }
    private void idle() { SystemClock.sleep(1500); }
    private void click(final String label) { runOnMainSync(new Runnable(){public void run(){Button button=find(activity.findViewById(android.R.id.content),label); check(button!=null && button.isEnabled(),"button unavailable: "+label); button.performClick();}}); }
}
