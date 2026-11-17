package club.midlife.rb1speed;

import android.app.*;
import android.content.*;
import android.location.Location;
import android.os.*;
import android.view.*;
import android.widget.*;

/** User-visible recording states and GPS inspection without restarting a healthy fix. */
public final class DashboardInstrumentation extends Instrumentation {
    private MainActivity activity;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{exercise();result.putString("stream","Dashboard integration: all checks passed\n");finish(Activity.RESULT_OK,result);}
        catch(Throwable e){result.putString("stream","Dashboard integration FAILED: "+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}
    }
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private void exercise() throws Exception {
        Context context=getTargetContext(); final SharedPreferences prefs=context.getSharedPreferences("dashcam",0);
        boolean enabled=prefs.getBoolean("enabled",false);String status=prefs.getString("cameraStatus",null);
        prefs.edit().putBoolean("enabled",false).putString("cameraStatus","Camera unavailable: test status").commit();
        try {
            activity=(MainActivity)startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            SystemClock.sleep(400);
            runOnMainSync(new Runnable(){public void run(){
                View root=activity.findViewById(android.R.id.content);
                Button start=(Button)root.findViewWithTag("record-start"),stop=(Button)root.findViewWithTag("record-stop");
                check(start!=null && start.isEnabled(),"start unavailable while stopped");
                check(stop!=null && !stop.isEnabled(),"stop should be disabled while stopped");
                TextView detail=(TextView)root.findViewWithTag("camera-detail");
                check(detail!=null && detail.getText().toString().contains("Camera unavailable"),"camera error not visible on dashboard");
                detail.performClick();
                check(activity.statusDialog!=null,"camera status dialog missing");
                TextView message=(TextView)activity.statusDialog.findViewById(android.R.id.message);
                check(message.getText().toString().contains("Camera unavailable: test status"),"full camera error not inspectable");
                check(message.getText().toString().contains("Recording is off."),"current state must be distinct from the last camera message");
                activity.statusDialog.dismiss();
            }});
            waitForIdleSync();
            runOnMainSync(new Runnable(){public void run(){
                Location fix=new Location("gps"); fix.setAccuracy(5);fix.setSpeed(0);fix.setSpeedAccuracyMetersPerSecond(.1f);fix.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());fix.setTime(System.currentTimeMillis());
                activity.onLocationChanged(fix);
                Button gps=(Button)activity.findViewById(android.R.id.content).findViewWithTag("gps-control");gps.performClick();
                check(activity.statusDialog!=null,"fresh GPS click must inspect, not restart GPS");
                TextView message=(TextView)activity.statusDialog.findViewById(android.R.id.message);
                check(message.getText().toString().contains("Accuracy ±5 m"),"GPS accuracy missing");
                activity.statusDialog.dismiss();
            }});
            waitForIdleSync();
            runOnMainSync(new Runnable(){public void run(){
                Button gps=(Button)activity.findViewById(android.R.id.content).findViewWithTag("gps-control");gps.performClick();
                check(activity.statusDialog!=null,"inspecting GPS discarded its fresh reading");
            }});
            final MainActivity old=activity;runOnMainSync(new Runnable(){public void run(){old.finish();}});activity=null;
            waitForIdleSync();
            check(!DashcamService.running(),"status inspection enabled recording");
        } finally {
            if(activity!=null){final MainActivity old=activity;runOnMainSync(new Runnable(){public void run(){old.finish();}});activity=null;}
            prefs.edit().putBoolean("enabled",enabled).putString("cameraStatus",status).commit();
        }
    }
}
