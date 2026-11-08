package club.midlife.rb1speed;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.DialogInterface;
import android.content.ActivityNotFoundException;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.location.*;
import android.os.*;
import android.provider.Settings;
import android.net.Uri;
import android.view.*;
import android.widget.Toast;
import java.util.Locale;

public class MainActivity extends Activity implements LocationListener {
    private LocationManager manager;
    private Dial dial;
    private DashboardSkin skin;
    private final SpeedReading reading = new SpeedReading();
    private final DriveSession driveSession = new DriveSession();
    private final SpeedWarning speedWarning = new SpeedWarning();
    private DriveAudio audio;
    private boolean demo, active, subscribed, asked;
    private boolean greetingShown;
    private String status = "TAP TO ENABLE GPS";
    private float accuracy;
    private long demoStart;
    private long greetingUntil;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        public void run() {
            if (active) {
                long now = SystemClock.elapsedRealtime();
                boolean live = !demo && reading.fresh(now);
                driveSession.update(reading.mph(), live, now);
                if (speedWarning.update(speedAt(now), demo || live, now)) audio.playChime();
                if (!speedWarning.active()) audio.cancelChime();
                dial.invalidate(); handler.postDelayed(this, 33);
            }
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        manager = (LocationManager)getSystemService(LOCATION_SERVICE);
        audio = new DriveAudio(this);
        greetingShown = state != null && state.getBoolean("greetingShown", false);
        skin = DashboardSkin.fromId(getPreferences(MODE_PRIVATE).getString("skin", "rb1"));
        dial = new Dial(); setContentView(dial);
    }
    @Override public void onResume() {
        super.onResume(); active = true; demo = false; reading.clear();
        driveSession.reset(); speedWarning.reset();
        boolean greet = !greetingShown;
        if (greet) { greetingShown = true; greetingUntil = SystemClock.elapsedRealtime() + 6000; }
        audio.resume(greet); startGps(); handler.post(ticker);
    }
    @Override public void onPause() {
        active = false; handler.removeCallbacks(ticker); audio.pause();
        speedWarning.reset(); stopGps(); reading.clear(); super.onPause();
    }
    @Override public void onDestroy() { audio.close(); super.onDestroy(); }
    @Override public void onSaveInstanceState(Bundle state) {
        state.putBoolean("greetingShown", greetingShown); super.onSaveInstanceState(state);
    }
    private boolean permitted() { return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED; }
    private void stopGps() { if (subscribed) { manager.removeUpdates(this); subscribed = false; } }
    private void startGps() {
        stopGps();
        if (!permitted()) { status = "TAP TO ENABLE PRECISE LOCATION"; return; }
        if (!manager.getAllProviders().contains(LocationManager.GPS_PROVIDER)) { status = "GPS HARDWARE UNAVAILABLE"; return; }
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) { status = "GPS OFF · TAP TO OPEN SETTINGS"; return; }
        status = "SEARCHING FOR SATELLITES";
        try { manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 0, this, Looper.getMainLooper()); subscribed = true; }
        catch (SecurityException e) { status = "TAP TO ENABLE PRECISE LOCATION"; }
    }
    private void action() {
        if (demo) { demo = false; reading.clear(); startGps(); return; }
        if (!permitted()) {
            if (asked && !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
            } else { asked = true; requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 7); }
        } else if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        } else { reading.clear(); startGps(); }
    }
    private void openSkins() {
        DashboardSkin[] skins = DashboardSkin.values();
        String[] labels = new String[skins.length];
        for (int i = 0; i < skins.length; i++) labels[i] = skins[i].label;
        new AlertDialog.Builder(this).setTitle("Choose a dashboard skin")
            .setSingleChoiceItems(labels, skin.ordinal(), new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                skin = skins[which];
                getPreferences(MODE_PRIVATE).edit().putString("skin", skin.id).apply();
                dial.invalidate();
                dialog.dismiss();
                }
            }).setNegativeButton("Cancel", null).show();
    }
    private void openUpdates() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/arjxbh/rb1speedo/releases/latest")));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Open github.com/arjxbh/rb1speedo/releases in a browser for updates", Toast.LENGTH_LONG).show();
        }
    }
    private double speedAt(long now) {
        return demo ? 58 + 22 * Math.sin((now - demoStart) / 5000.0) : reading.mph();
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results); if (active && !demo) startGps();
    }
    @Override public void onLocationChanged(Location location) {
        if (demo) return;
        long now = SystemClock.elapsedRealtime();
        boolean accepted = location.hasAccuracy() && reading.accept(location.getSpeed(), location.hasSpeed(), location.getAccuracy(),
            location.hasSpeedAccuracy(), location.getSpeedAccuracyMetersPerSecond(), location.getElapsedRealtimeNanos()/1000000L, now);
        if (accepted) { accuracy = location.getAccuracy(); status = "GPS CONNECTED"; }
        else if (!reading.fresh(now)) { status = "WAITING FOR RELIABLE GPS SPEED"; }
    }
    @Override public void onProviderDisabled(String provider) { reading.clear(); status = "GPS OFF · TAP TO OPEN SETTINGS"; }
    @Override public void onProviderEnabled(String provider) { if (active && !demo) startGps(); }
    @Override public void onStatusChanged(String provider, int value, Bundle extras) {}

    private final class Dial extends View {
        private final Paint p = new Paint(3);
        private float s, ox, oy, needle;
        private long previous;
        private int cyan, blue;
        Dial() { super(MainActivity.this); setLayerType(View.LAYER_TYPE_SOFTWARE, null); setContentDescription("RB1 GPS speedometer. Tap bottom panel to enable GPS. Tap center header to choose a skin. Tap top right for demo. Tap top left for updates."); setFocusable(true); }
        private void text(Canvas c, String value, float x, float y, float size, int color, boolean glow) {
            p.reset(); p.setAntiAlias(true); p.setColor(color); p.setTextSize(size); p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.create(skin.font, skin == DashboardSkin.INTEGRA ? Typeface.BOLD : Typeface.NORMAL));
            if (glow) p.setShadowLayer(9,0,0,blue); c.drawText(value,x,y,p); p.clearShadowLayer();
        }
        private void line(Canvas c,float x1,float y1,float x2,float y2,int color,float width,boolean glow) {
            p.reset(); p.setAntiAlias(true); p.setColor(color); p.setStrokeWidth(width); p.setStrokeCap(Paint.Cap.ROUND);
            if (glow) p.setShadowLayer(12,0,0,color); c.drawLine(x1,y1,x2,y2,p); p.clearShadowLayer();
        }
        private float x(float r,double a) { return 500 + r*(float)Math.cos(Math.toRadians(a)); }
        private float y(float r,double a) { return 460 + r*(float)Math.sin(Math.toRadians(a)); }
        @Override protected void onDraw(Canvas c) {
            cyan=skin.ink; blue=skin.accent;
            c.drawColor(skin.background);
            float availW=getWidth()-getPaddingLeft()-getPaddingRight(), availH=getHeight()-getPaddingTop()-getPaddingBottom();
            s=Math.min(availW/1000f,availH/940f); ox=(getWidth()-1000*s)/2; oy=(getHeight()-940*s)/2;
            c.save(); c.translate(ox,oy); c.scale(s,s);
            long now=SystemClock.elapsedRealtime(); boolean fresh=reading.fresh(now);
            double speed=speedAt(now);
            boolean valid=demo||fresh;
            if (skin == DashboardSkin.RB1) {
            // Preserve the original luminous RB1 annulus.
            p.reset(); p.setAntiAlias(true); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(105);
            p.setShader(new RadialGradient(500,460,405,new int[]{0xff020611,0xff020611,0xff082cf2,0xff031441,0xff01040c},new float[]{0,.67f,.76f,.93f,1},Shader.TileMode.CLAMP));
            c.drawArc(new RectF(147,107,853,813),140,260,false,p); p.setShader(null);
            p.setColor(0xff14263d);p.setStrokeWidth(2);c.drawArc(new RectF(89,49,911,871),140,260,false,p);
            } else {
                p.reset(); p.setAntiAlias(true); p.setColor(skin.face);
                c.drawArc(new RectF(90,50,910,870),skin.start,skin.sweep,true,p);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(4); p.setColor(skin.accent);
                c.drawCircle(500,460,414,p);
                p.setStrokeWidth(8); p.setColor(skin.pointer);
                c.drawArc(new RectF(106,66,894,854),skin.start+skin.sweep*100/120,skin.sweep*20/120,false,p);
            }
            p.reset(); p.setColor(skin.background); c.drawRect(240,0,760,100,p);
            text(c,skin.title,500,48,25,0xffd5dce2,false);
            text(c,skin.code + "  /  SKINS ▾",500,80,16,0xffa5b4c0,false);
            text(c,"UPDATES",125,59,19,Color.rgb(118,145,168),false);
            if (demo || driveSession.demoVisible()) text(c,demo ? "EXIT DEMO" : "DEMO",875,59,19,demo?Color.rgb(255,167,76):Color.rgb(118,145,168),false);
            for(int i=0;i<=120;i+=2) {
                double a=skin.start+i*skin.sweep/120; boolean major=i%10==0;
                line(c,x(400,a),y(400,a),x(major?367:382,a),y(major?367:382,a),skin == DashboardSkin.RB1 ? (major?0xff20c9ff:0xffe5f6ff) : skin.tick,major?5:2.5f,major && skin == DashboardSkin.RB1);
                if(i%skin.labelStep==0) text(c,String.valueOf(i),x(317,a),y(317,a)+16,skin.labelStep==10?34:49,cyan,skin == DashboardSkin.RB1);
            }
            text(c,"mph",500,265,29,cyan,skin == DashboardSkin.RB1);
            float target=(float)Math.min(120,Math.max(0,speed));
            float dt=previous==0?0.033f:Math.min(.1f,(now-previous)/1000f);previous=now;
            needle+=(target-needle)*(1-(float)Math.exp(-dt/0.13));
            if(valid) {
                double a=skin.start+needle*skin.sweep/120;
                line(c,x(101,a+180),y(101,a+180),x(365,a),y(365,a),skin.pointer,7,skin == DashboardSkin.RB1);
                line(c,x(100,a+180),y(100,a+180),x(361,a),y(361,a),skin == DashboardSkin.RB1 ? 0xffffa13a : skin.pointer,2,false);
                p.reset();p.setAntiAlias(true);p.setColor(0xff060b12);c.drawCircle(500,460,17,p);
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(0xff32445c);c.drawCircle(500,460,17,p);
            }
            p.reset();p.setAntiAlias(true);p.setColor(skin.face);c.drawCircle(500,460,220,p);
            text(c,valid?String.format(Locale.US,"%.0f",speed):"—",500,474,133,cyan,skin == DashboardSkin.RB1);
            text(c,demo?"SIMULATED SPEED":"GPS SPEED",500,520,18,skin == DashboardSkin.INTEGRA ? 0xff555a60 : demo?0xffffb15e:0xff7ba3bd,false);
            // Digital readout sits above the moving needle, retaining legibility.
            p.reset();p.setAntiAlias(true);p.setColor(skin == DashboardSkin.INTEGRA ? 0xffd8d9cd : 0xff050c19);c.drawRoundRect(382,557,618,618,10,10,p);
            text(c,valid?String.format(Locale.US,"%03d MPH",Math.round(speed)):"--- MPH",500,599,28,cyan,skin == DashboardSkin.RB1);
            String label=demo?"DEMO · TAP BELOW FOR LIVE GPS":fresh?"GPS CONNECTED":status.equals("GPS CONNECTED")?"SIGNAL LOST · WAITING FOR GPS":status;
            p.setColor(demo?0xff2c1a0c:0xff061327);c.drawRoundRect(125,818,875,884,12,12,p);
            text(c,label,500,858,21,demo?0xffffb15e:fresh?0xffb4efdb:0xffb4c3d0,false);
            text(c,now < greetingUntil ? DriveAudio.GREETING : demo?"DISPLAY PREVIEW ONLY":fresh?String.format(Locale.US,"GPS accuracy ±%.0f m · PHONE GPS",accuracy):"USE OUTDOORS WITH A CLEAR VIEW OF THE SKY",500,920,16,0xff668097,false);
            c.restore();
            setContentDescription((valid?String.format(Locale.US,"%.0f miles per hour. %s",speed,demo?"Demo mode":"GPS connected"):label) + ". " + skin.label + ". Tap center header to choose a skin, top left for updates, " + (demo || driveSession.demoVisible() ? "top right for demo, " : "") + "bottom panel for GPS.");
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if(e.getAction()==MotionEvent.ACTION_UP) {
                float x=(e.getX()-ox)/s,y=(e.getY()-oy)/s;
                if(x>=0&&x<240&&y>=0&&y<110) { openUpdates(); performClick(); }
                else if(x>=240&&x<=760&&y>=0&&y<110) { openSkins(); performClick(); }
                else if(x>760&&y<110&&(demo||driveSession.demoVisible())) { demo=!demo; reading.clear(); speedWarning.reset(); audio.cancelChime(); if(demo) {stopGps();demoStart=SystemClock.elapsedRealtime();} else startGps(); performClick(); }
                else if(y>790) { action(); performClick(); }
                return true;
            } return true;
        }
        @Override public boolean performClick() { super.performClick(); return true; }
    }
}
