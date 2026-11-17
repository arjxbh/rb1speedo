package club.midlife.rb1speed;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.util.UUID;

/** Seed the installed previous app before adb replaces it; no production settings are changed. */
public final class PrepareUpgradeInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try {
            Context context=getTargetContext();String token=UUID.randomUUID().toString();
            String version=context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionName;
            context.getSharedPreferences("upgradeAcceptance",0).edit().putString("token",token).putString("version",version).commit();
            try(DataOutputStream output=new DataOutputStream(new FileOutputStream(new File(context.getFilesDir(),"upgrade-acceptance.bin")))){output.writeUTF(token);}
            result.putString("stream","Upgrade marker prepared from "+version+"\n");finish(Activity.RESULT_OK,result);
        } catch(Exception e){result.putString("stream","Upgrade marker FAILED: "+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}
    }
}
