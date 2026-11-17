package club.midlife.rb1speed;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Manual parked checks with a persistent, copyable report. */
public final class CameraDiagnosticsActivity extends Activity {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final List<Button> tests=new ArrayList<Button>();
    private TextView results, status;
    private CameraPreviewView preview;
    private android.graphics.Bitmap previewBitmap;
    private List<CameraCatalog.Option> lenses=new ArrayList<CameraCatalog.Option>();
    private Button previewButton;
    AlertDialog lensDialog;
    private boolean standalone() {
        try {
            Bundle metadata=getPackageManager().getApplicationInfo(getPackageName(),PackageManager.GET_META_DATA).metaData;
            return metadata==null || !metadata.getBoolean("rb1.fullApp",false);
        } catch(PackageManager.NameNotFoundException e) { return true; }
    }
    private CameraDiagnostics diagnostics;
    private String pending;
    private String report;
    private boolean visible, running;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(20,20,20,20);
        TextView title=new TextView(this); title.setText("Ultrawide lens debug · parked checks"); title.setTextSize(22); content.addView(title);
        TextView help=new TextView(this); help.setText(standalone()
            ? "Standalone lens probe · " + getPackageName() + "\nStop recording in RB1 before testing. This app cannot pause another installed app. It saves no footage and changes no RB1 settings."
            : "Normal dashcam recording pauses while this screen is open. Scan rear lenses, optionally probe IDs 0–9, then choose a candidate to preview. Each preview lasts 8 seconds and saves no footage. Compare the view or cover the physical lenses to identify ultrawide. Lens choices and recording settings stay unchanged."); content.addView(help);
        status=new TextView(this); status.setText("Ready · choose a test"); content.addView(status);
        for(final String method:new String[]{"Scan rear lenses","Probe camera IDs 0–9","Package access info"}) {
            Button button=new Button(this); button.setText(method); content.addView(button); tests.add(button);
            button.setOnClickListener(new View.OnClickListener(){public void onClick(View v){run(method);}});
        }
        previewButton=new Button(this); previewButton.setText("Preview a lens · scan first"); content.addView(previewButton); tests.add(previewButton);
        previewButton.setOnClickListener(new View.OnClickListener(){public void onClick(View v){
            if(lenses.isEmpty()){Toast.makeText(CameraDiagnosticsActivity.this,"Run Scan rear lenses first",Toast.LENGTH_LONG).show();return;}
            String[] labels=new String[lenses.size()]; for(int i=0;i<labels.length;i++)labels[i]=lenses.get(i).label();
            lensDialog=new AlertDialog.Builder(CameraDiagnosticsActivity.this).setTitle("Preview rear lens · widest estimate first")
                .setItems(labels,new DialogInterface.OnClickListener(){public void onClick(DialogInterface dialog,int which){
                    if(diagnostics!=null){busy(true);diagnostics.preview(lenses.get(which));}
                }}).setNegativeButton("Cancel",null).show();
        }});
        preview=new CameraPreviewView(this); preview.setContentDescription("Selected rear-lens diagnostic preview");
        content.addView(preview,new LinearLayout.LayoutParams(-1,Math.round(220*getResources().getDisplayMetrics().density)));
        Button stop=new Button(this); stop.setText("Stop probe"); content.addView(stop);
        stop.setOnClickListener(new View.OnClickListener(){public void onClick(View v){if(diagnostics!=null) diagnostics.cancel();}});
        Button copy=new Button(this); copy.setText("Copy results"); content.addView(copy);
        copy.setOnClickListener(new View.OnClickListener(){public void onClick(View v){
            ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("RB1 ultrawide lens debug",report));
            Toast.makeText(CameraDiagnosticsActivity.this,"Results copied",Toast.LENGTH_SHORT).show();
        }});
        Button clear=new Button(this); clear.setText("Clear results"); content.addView(clear); tests.add(clear);
        clear.setOnClickListener(new View.OnClickListener(){public void onClick(View v){report=""; append("RB1 ultrawide lens debug · "+Build.MODEL+" / "+Build.DISPLAY);}});
        results=new TextView(this); results.setTextSize(12); results.setTypeface(android.graphics.Typeface.MONOSPACE); results.setTextIsSelectable(true); content.addView(results);
        report=getSharedPreferences("lensDiagnostics",0).getString("report",""); results.setText(report);
        if(report.isEmpty()) append("RB1 ultrawide lens debug · "+Build.MODEL+" / "+Build.DISPLAY);
        ScrollView scroll=new ScrollView(this); scroll.addView(content);
        final LinearLayout insetContent=content;
        scroll.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener(){public WindowInsets onApplyWindowInsets(View view,WindowInsets insets){
            int margin=(int)(12*getResources().getDisplayMetrics().density+.5f);
            insetContent.setPadding(margin+insets.getSystemWindowInsetLeft(),margin+insets.getSystemWindowInsetTop(),margin+insets.getSystemWindowInsetRight(),margin+insets.getSystemWindowInsetBottom());
            return insets;
        }});
        setContentView(scroll); scroll.requestApplyInsets();
    }
    @Override public void onResume() {
        super.onResume(); visible=true; DashcamService.diagnosticsActive=true;
        if(!standalone()) startService(new Intent(this,DashcamService.class).setAction(DashcamService.PAUSE_DIAGNOSTICS));
        diagnostics=new CameraDiagnostics(this,new CameraDiagnostics.Listener(){
            public void line(final String line){ main.post(new Runnable(){public void run(){append(line);}}); }
            public void lenses(final List<CameraCatalog.Option> found){main.post(new Runnable(){public void run(){lenses=found;previewButton.setText("Preview a lens · "+found.size()+" candidates");}});}
            public void frame(android.graphics.Bitmap bitmap){
                if(!visible){bitmap.recycle();return;}
                android.graphics.Bitmap old=previewBitmap;previewBitmap=bitmap;preview.setImageBitmap(bitmap);if(old!=null)old.recycle();
            }
            public void idle(){main.post(new Runnable(){public void run(){if(visible) busy(false);}});}
        });
        busy(false); resumePending();
    }
    @Override public void onPause() {
        visible=false;
        if(lensDialog!=null){lensDialog.dismiss();lensDialog=null;}
        if(running) append("INTERRUPTED: diagnostic screen left; tests cancelled and cameras closing.");
        if(diagnostics!=null) { diagnostics.close(); diagnostics=null; }
        if(previewBitmap!=null){preview.setImageDrawable(null);previewBitmap.recycle();previewBitmap=null;}
        busy(false); super.onPause();
    }
    private void append(String line) {
        report += line + "\n";
        if(report.length()>120000) report="[Earlier results trimmed]\n"+report.substring(report.length()-100000);
        results.setText(report); getSharedPreferences("lensDiagnostics",0).edit().putString("report",report).apply();
    }
    private void busy(boolean value) { running=value; for(Button test:tests) test.setEnabled(!value); status.setText(value ? "Running · scroll down for preview/results; Stop cancels the probe" : "Ready · results saved below"); }
    private void run(String method) {
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) {
            pending=method; requestPermissions(new String[]{Manifest.permission.CAMERA},91); return;
        }
        if(diagnostics!=null) { busy(true); diagnostics.run(method); }
    }
    private void resumePending() {
        if(visible && pending!=null && checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) {
            String method=pending; pending=null; run(method);
        }
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request==91) {
            if(grants.length>0 && grants[0]==PackageManager.PERMISSION_GRANTED) resumePending();
            else { pending=null; append("SKIP: camera permission denied."); }
        }
    }
}
