package club.midlife.rb1speed;

import android.app.*;
import android.content.*;
import android.location.Location;
import android.graphics.*;
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
        final SharedPreferences peakPrefs=context.getSharedPreferences("drivePeak",0);
        final java.util.Map<String,?> savedPeak=peakPrefs.getAll();
        boolean enabled=prefs.getBoolean("enabled",false);String status=prefs.getString("cameraStatus",null);
        prefs.edit().putBoolean("enabled",false).putString("cameraStatus","Camera unavailable: test status").commit();
        try {
            activity=(MainActivity)startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            SystemClock.sleep(400);
            runOnMainSync(new Runnable(){public void run(){
                for(DashboardSkin skin:DashboardSkin.values()) {
                    DashboardDial gauge=new DashboardDial(activity);gauge.layout(0,0,900,900);
                    for(double peak:new double[]{80,150}) {
                        gauge.update(skin,20,false,peak,false,SystemClock.elapsedRealtime());
                        Bitmap bitmap=Bitmap.createBitmap(900,900,Bitmap.Config.ARGB_8888);gauge.draw(new Canvas(bitmap));
                        double angle=Math.toRadians(skin.start+Math.min(120,peak)*skin.sweep/120);
                        int pixel=bitmap.getPixel((int)Math.round(450+397*Math.cos(angle)),(int)Math.round(450+397*Math.sin(angle)));
                        check(pixel==(skin==DashboardSkin.INTEGRA?0xff164e75:0xffffcf70),"peak marker missing: "+skin);
                        check(gauge.getContentDescription().toString().contains(String.valueOf((int)peak)),"peak accessibility missing");
                        bitmap.recycle();
                    }
                }
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
            runOnMainSync(new Runnable(){public void run(){
                try {
                    java.lang.reflect.Field field=MainActivity.class.getDeclaredField("drivePeak");field.setAccessible(true);
                    DrivePeak peak=(DrivePeak)field.get(activity);peak.restore(72,true,false);
                    Location fix=new Location("gps");fix.setAccuracy(5);fix.setSpeed((float)(95/2.2369362920544));fix.setSpeedAccuracyMetersPerSecond(.1f);fix.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());fix.setTime(System.currentTimeMillis());
                    activity.onLocationChanged(fix);
                    check(Math.abs(peak.maximumMph()-95)<.01,"accepted GPS did not raise drive peak");
                    fix.setAccuracy(100);fix.setSpeed(60);fix.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos()+1);activity.onLocationChanged(fix);
                    check(Math.abs(peak.maximumMph()-95)<.01,"rejected GPS raised peak");
                    java.lang.reflect.Method toggle=MainActivity.class.getDeclaredMethod("toggleDemo");toggle.setAccessible(true);toggle.invoke(activity);
                    DashboardDial dial=(DashboardDial)activity.findViewById(android.R.id.content).findViewWithTag("speed-gauge");
                    check(dial.getContentDescription().toString().startsWith("Demo maximum"),"demo peak not separate");
                    toggle.invoke(activity);
                    check(dial.getContentDescription().toString().contains("Current drive maximum speed 95"),"demo changed real peak");
                } catch(ReflectiveOperationException e){throw new AssertionError(e);}
            }});
            final MainActivity old=activity;runOnMainSync(new Runnable(){public void run(){old.finish();}});activity=null;
            waitForIdleSync();
            activity=(MainActivity)startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            runOnMainSync(new Runnable(){public void run(){
                DashboardDial dial=(DashboardDial)activity.findViewById(android.R.id.content).findViewWithTag("speed-gauge");
                check(dial.getContentDescription().toString().contains("Current drive maximum speed 95"),"reopening lost drive peak");
            }});
            check(!DashcamService.running(),"status inspection enabled recording");
        } finally {
            if(activity!=null){final MainActivity old=activity;runOnMainSync(new Runnable(){public void run(){old.finish();}});activity=null;}
            waitForIdleSync();
            SharedPreferences.Editor restore=peakPrefs.edit().clear();
            for(java.util.Map.Entry<String,?> entry:savedPeak.entrySet()) {
                Object value=entry.getValue();
                if(value instanceof Boolean)restore.putBoolean(entry.getKey(),(Boolean)value);
                else if(value instanceof Long)restore.putLong(entry.getKey(),(Long)value);
                else if(value instanceof Integer)restore.putInt(entry.getKey(),(Integer)value);
            }
            restore.commit();
            prefs.edit().putBoolean("enabled",enabled).putString("cameraStatus",status).commit();
        }
    }
}
