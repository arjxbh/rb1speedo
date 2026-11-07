package club.midlife.rb1speed;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.location.*;
import android.os.*;
import android.provider.Settings;
import android.net.Uri;
import android.view.*;
import java.util.Locale;

public class MainActivity extends Activity implements LocationListener {
    private LocationManager manager;
    private Dial dial;
    private final SpeedReading reading = new SpeedReading();
    private boolean demo, active, subscribed, asked;
    private String status = "TAP TO ENABLE GPS";
    private float accuracy;
    private long demoStart;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        public void run() { if (active) { dial.invalidate(); handler.postDelayed(this, 33); } }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        manager = (LocationManager)getSystemService(LOCATION_SERVICE);
        dial = new Dial(); setContentView(dial);
    }
    @Override public void onResume() { super.onResume(); active = true; demo = false; reading.clear(); startGps(); handler.post(ticker); }
    @Override public void onPause() { active = false; handler.removeCallbacks(ticker); stopGps(); reading.clear(); super.onPause(); }
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
        private final int cyan = Color.rgb(114,239,255), blue = Color.rgb(0,71,255);
        Dial() { super(MainActivity.this); setLayerType(View.LAYER_TYPE_SOFTWARE, null); setContentDescription("RB1 GPS speedometer. Tap bottom panel to enable GPS. Tap top right for demo."); setFocusable(true); }
        private void text(Canvas c, String value, float x, float y, float size, int color, boolean glow) {
            p.reset(); p.setAntiAlias(true); p.setColor(color); p.setTextSize(size); p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            if (glow) p.setShadowLayer(9,0,0,blue); c.drawText(value,x,y,p); p.clearShadowLayer();
        }
        private void line(Canvas c,float x1,float y1,float x2,float y2,int color,float width,boolean glow) {
            p.reset(); p.setAntiAlias(true); p.setColor(color); p.setStrokeWidth(width); p.setStrokeCap(Paint.Cap.ROUND);
            if (glow) p.setShadowLayer(12,0,0,color); c.drawLine(x1,y1,x2,y2,p); p.clearShadowLayer();
        }
        private float x(float r,double a) { return 500 + r*(float)Math.cos(Math.toRadians(a)); }
        private float y(float r,double a) { return 460 + r*(float)Math.sin(Math.toRadians(a)); }
        @Override protected void onDraw(Canvas c) {
            c.drawColor(Color.rgb(2,4,10));
            float availW=getWidth()-getPaddingLeft()-getPaddingRight(), availH=getHeight()-getPaddingTop()-getPaddingBottom();
            s=Math.min(availW/1000f,availH/940f); ox=(getWidth()-1000*s)/2; oy=(getHeight()-940*s)/2;
            c.save(); c.translate(ox,oy); c.scale(s,s);
            long now=SystemClock.elapsedRealtime(); boolean fresh=reading.fresh(now);
            double speed=demo ? 58+22*Math.sin((now-demoStart)/5000.0) : reading.mph();
            boolean valid=demo||fresh;
            text(c,"ODYSSEY",500,48,25,Color.rgb(159,176,192),false);
            text(c,"RB1  /  GPS SPEED",500,80,14,Color.rgb(91,116,144),false);
            text(c,demo ? "EXIT DEMO" : "DEMO",875,59,19,demo?Color.rgb(255,167,76):Color.rgb(118,145,168),false);
            // A continuous luminous blue annulus, matching the reference cluster.
            p.reset(); p.setAntiAlias(true); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(105);
            p.setShader(new RadialGradient(500,460,405,new int[]{0xff020611,0xff020611,0xff082cf2,0xff031441,0xff01040c},new float[]{0,.67f,.76f,.93f,1},Shader.TileMode.CLAMP));
            c.drawArc(new RectF(147,107,853,813),140,260,false,p); p.setShader(null);
            p.setColor(0xff14263d);p.setStrokeWidth(2);c.drawArc(new RectF(89,49,911,871),140,260,false,p);
            for(int i=0;i<=120;i+=2) {
                double a=140+i*260.0/120; boolean major=i%10==0;
                line(c,x(400,a),y(400,a),x(major?367:382,a),y(major?367:382,a),major?0xff20c9ff:0xffe5f6ff,major?5:2.5f,major);
                if(i%20==0) text(c,String.valueOf(i),x(317,a),y(317,a)+16,49,cyan,true);
            }
            text(c,"mph",500,265,29,cyan,true);
            float target=(float)Math.min(120,Math.max(0,speed));
            float dt=previous==0?0.033f:Math.min(.1f,(now-previous)/1000f);previous=now;
            needle+=(target-needle)*(1-(float)Math.exp(-dt/0.13));
            if(valid) {
                double a=140+needle*260.0/120;
                line(c,x(101,a+180),y(101,a+180),x(365,a),y(365,a),0xffff4015,7,true);
                line(c,x(100,a+180),y(100,a+180),x(361,a),y(361,a),0xffffa13a,2,false);
                p.reset();p.setAntiAlias(true);p.setColor(0xff060b12);c.drawCircle(500,460,17,p);
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(0xff32445c);c.drawCircle(500,460,17,p);
            }
            p.reset();p.setAntiAlias(true);p.setColor(0xff020611);c.drawCircle(500,460,220,p);
            text(c,valid?String.format(Locale.US,"%.0f",speed):"—",500,474,133,cyan,true);
            text(c,demo?"SIMULATED SPEED":"GPS SPEED",500,520,18,demo?0xffffb15e:0xff7ba3bd,false);
            // Digital readout sits above the moving needle, retaining legibility.
            p.reset();p.setAntiAlias(true);p.setColor(0xff050c19);c.drawRoundRect(382,557,618,618,10,10,p);
            text(c,valid?String.format(Locale.US,"%03d MPH",Math.round(speed)):"--- MPH",500,599,28,cyan,true);
            String label=demo?"DEMO · TAP BELOW FOR LIVE GPS":fresh?"GPS CONNECTED":status.equals("GPS CONNECTED")?"SIGNAL LOST · WAITING FOR GPS":status;
            p.setColor(demo?0xff2c1a0c:0xff061327);c.drawRoundRect(125,818,875,884,12,12,p);
            text(c,label,500,858,21,demo?0xffffb15e:fresh?cyan:0xffb4c3d0,false);
            text(c,demo?"DISPLAY PREVIEW ONLY":fresh?String.format(Locale.US,"GPS accuracy ±%.0f m · PHONE GPS",accuracy):"USE OUTDOORS WITH A CLEAR VIEW OF THE SKY",500,920,16,0xff668097,false);
            c.restore();
            setContentDescription(valid?String.format(Locale.US,"%.0f miles per hour. %s",speed,demo?"Demo mode":"GPS connected"):label);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if(e.getAction()==MotionEvent.ACTION_UP) {
                float x=(e.getX()-ox)/s,y=(e.getY()-oy)/s;
                if(x>760&&y<110) { demo=!demo; reading.clear(); if(demo) {stopGps();demoStart=SystemClock.elapsedRealtime();} else startGps(); performClick(); }
                else if(y>790) { action(); performClick(); }
                return true;
            } return true;
        }
        @Override public boolean performClick() { super.performClick(); return true; }
    }
}
