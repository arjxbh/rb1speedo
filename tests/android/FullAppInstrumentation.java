package club.midlife.rb1speed;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.Arrays;

/** Default app entry points, update identity, and private clip provider. */
public final class FullAppInstrumentation extends Instrumentation {
    private Activity activity;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{exercise();result.putString("stream","Full app integration: all checks passed\n");finish(Activity.RESULT_OK,result);}
        catch(Throwable e){result.putString("stream","Full app integration FAILED: "+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}
    }
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private void exercise() throws Exception {
        Context context=getTargetContext(); PackageManager pm=context.getPackageManager(); String pkg=context.getPackageName();
        ApplicationInfo app=pm.getApplicationInfo(pkg,PackageManager.GET_META_DATA);
        check(pkg.equals("org.codeaurora.snapcam"),"default package must retain the working Wide identity");
        check("RB1 Speed".contentEquals(pm.getApplicationLabel(app)),"default app label not merged");
        check(app.metaData!=null && app.metaData.getBoolean("rb1.fullApp"),"full-app metadata missing");
        SharedPreferences upgrade=context.getSharedPreferences("upgradeAcceptance",0);
        String token=upgrade.getString("token",null);
        if(token!=null) {
            File marker=new File(context.getFilesDir(),"upgrade-acceptance.bin");
            try(DataInputStream input=new DataInputStream(new FileInputStream(marker))){check(token.equals(input.readUTF()),"upgrade lost private files or preferences");}
            Bundle progress=new Bundle();progress.putString("stream","Upgrade from "+upgrade.getString("version", "unknown")+" preserved preferences and private file\n");sendStatus(0,progress);
            check(marker.delete(),"upgrade test marker not removed");upgrade.edit().clear().commit();
        }
        Intent launch=pm.getLaunchIntentForPackage(pkg);
        check(launch!=null && launch.getComponent().getClassName().equals(MainActivity.class.getName()),"launcher still points to probe");
        check(!pm.getActivityInfo(new ComponentName(context,CameraDiagnosticsActivity.class),0).exported,"debug screen should be private");
        pm.getServiceInfo(new ComponentName(context,DashcamService.class),0);
        pm.getServiceInfo(new ComponentName(context,UploadJob.class),0);
        pm.getReceiverInfo(new ComponentName(context,UploadReceiver.class),0);
        ProviderInfo provider=pm.resolveContentProvider(pkg+".clips",0);
        check(provider!=null && pkg.equals(provider.packageName),"clip provider resolves to a different app");
        if(pkg.equals("org.codeaurora.snapcam")) {
            // Android hides another app's private provider from resolveContentProvider.
            // Verify its registration through the emulator shell, not by weakening app visibility.
            try(InputStream input=new ParcelFileDescriptor.AutoCloseInputStream(getUiAutomation().executeShellCommand("dumpsys package club.midlife.rb1speed"))) {
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int size;
                while((size=input.read(buffer))!=-1)bytes.write(buffer,0,size);
                String installed=bytes.toString("UTF-8");
                if(installed.contains("installed=true"))
                    check(installed.contains("[club.midlife.rb1speed.clips]"),"legacy app provider must remain independent");
            }
        }
        ClipStore store=ClipStore.get(context); String id=store.beginClip(System.currentTimeMillis()); File file=new File(store.directory,id+".mp4");
        byte[] fixture={12,34,56,78,90};
        try {
            try(OutputStream out=new FileOutputStream(file)){out.write(fixture);} store.ready(id,file);
            Uri uri=Uri.parse("content://"+pkg+".clips/"+id);
            check("video/mp4".equals(context.getContentResolver().getType(uri)),"clip MIME type missing");
            try(InputStream input=context.getContentResolver().openInputStream(uri)){
                byte[] actual=new byte[fixture.length];check(input.read(actual)==fixture.length && Arrays.equals(actual,fixture),"provider did not return this app's clip");
            }
            try(Cursor row=context.getContentResolver().query(uri,new String[]{OpenableColumns.SIZE},null,null,null)){
                check(row!=null && row.moveToFirst() && row.getLong(0)==fixture.length,"clip query failed");
            }
        } finally {store.deleteLocal(id);}
        SharedPreferences preferences=context.getSharedPreferences("dashcam",0);boolean enabled=preferences.getBoolean("enabled",false);
        preferences.edit().putBoolean("enabled",false).commit();
        try {
            activity=startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            runOnMainSync(new Runnable(){public void run(){
                View root=activity.findViewById(android.R.id.content);
                check(button(root,"Start recording") && button(root,"Flip camera") && button(root,"Stop recording"),"dashboard controls missing");
            }});
            final Activity main=activity;runOnMainSync(new Runnable(){public void run(){main.finish();}});activity=null;
            activity=startActivitySync(new Intent(context,CameraDiagnosticsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            check(DashcamService.diagnosticsActive,"full app debug did not acquire camera isolation");
            SystemClock.sleep(1000);check(!DashcamService.diagnosticsBusy(),"full app debug did not release its recording service");
        } finally {
            if(activity!=null){final Activity old=activity;runOnMainSync(new Runnable(){public void run(){old.finish();}});activity=null;}
            preferences.edit().putBoolean("enabled",enabled).commit();
        }
    }
    private boolean button(View view,String text){
        if(view instanceof Button && text.equals(((Button)view).getText().toString()))return true;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)if(button(((ViewGroup)view).getChildAt(i),text))return true;
        return false;
    }
}
