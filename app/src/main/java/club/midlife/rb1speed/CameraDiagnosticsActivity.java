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
    private CameraDiagnostics diagnostics;
    private String pending;
    private String report;
    private boolean visible, running;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(20,20,20,20);
        TextView title=new TextView(this); title.setText("Camera diagnostics · parked checks"); title.setTextSize(22); content.addView(title);
        TextView help=new TextView(this); help.setText("Normal dashcam recording is paused while this screen is open. Each button tests every directly exposed rear/front pair in both opening orders. Frame/encoder tests run for 3 seconds per configuration. Test MP4s are temporary, deleted after each probe, and never uploaded.\n\nRun Scan, then Open cameras only first. A PASS proves a short capture test, not long-term reliability. External USB, vendor/root changes and CameraX are described in the scan; they are not implemented recording tests."); content.addView(help);
        status=new TextView(this); status.setText("Ready · choose a test"); content.addView(status);
        for(final String method:new String[]{"Scan capabilities","Single-camera baseline","Open cameras only","Dual YUV frames","Two encoders","One split-screen encoder","Camera1 preview"}) {
            Button button=new Button(this); button.setText(method); content.addView(button); tests.add(button);
            button.setOnClickListener(new View.OnClickListener(){public void onClick(View v){run(method);}});
        }
        TextView methods=new TextView(this); methods.setText("Dual YUV / two encoders: 1080p → 720p → 480p, supported sizes only. Split-screen: two 480p inputs → one 1280×480 encoder. Camera1: two 480p previews; no saved recording."); content.addView(methods);
        Button stop=new Button(this); stop.setText("Stop tests"); content.addView(stop);
        stop.setOnClickListener(new View.OnClickListener(){public void onClick(View v){if(diagnostics!=null) diagnostics.cancel();}});
        Button copy=new Button(this); copy.setText("Copy results"); content.addView(copy);
        copy.setOnClickListener(new View.OnClickListener(){public void onClick(View v){
            ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("RB1 camera diagnostics",report));
            Toast.makeText(CameraDiagnosticsActivity.this,"Results copied",Toast.LENGTH_SHORT).show();
        }});
        Button clear=new Button(this); clear.setText("Clear results"); content.addView(clear); tests.add(clear);
        clear.setOnClickListener(new View.OnClickListener(){public void onClick(View v){report=""; append("RB1 camera diagnostics · "+Build.MODEL+" / "+Build.DISPLAY);}});
        results=new TextView(this); results.setTextSize(12); results.setTypeface(android.graphics.Typeface.MONOSPACE); results.setTextIsSelectable(true); content.addView(results);
        report=getSharedPreferences("cameraDiagnostics",0).getString("report",""); results.setText(report);
        if(report.isEmpty()) append("RB1 camera diagnostics · "+Build.MODEL+" / "+Build.DISPLAY);
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
        startService(new Intent(this,DashcamService.class).setAction(DashcamService.PAUSE_DIAGNOSTICS));
        diagnostics=new CameraDiagnostics(this,new CameraDiagnostics.Listener(){
            public void line(final String line){ main.post(new Runnable(){public void run(){append(line);}}); }
            public void idle(){main.post(new Runnable(){public void run(){if(visible) busy(false);}});}
        });
        busy(false); resumePending();
    }
    @Override public void onPause() {
        visible=false;
        if(running) append("INTERRUPTED: diagnostic screen left; tests cancelled and cameras closing.");
        if(diagnostics!=null) { diagnostics.close(); diagnostics=null; }
        busy(false); super.onPause();
    }
    private void append(String line) {
        report += line + "\n";
        if(report.length()>120000) report="[Earlier results trimmed]\n"+report.substring(report.length()-100000);
        results.setText(report); getSharedPreferences("cameraDiagnostics",0).edit().putString("report",report).apply();
    }
    private void busy(boolean value) { running=value; for(Button test:tests) test.setEnabled(!value); status.setText(value ? "Running · scroll down for results; Stop cancels the remaining tests" : "Ready · results saved below"); }
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
