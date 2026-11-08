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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.content.res.Configuration;
import java.util.Locale;

public class MainActivity extends Activity implements LocationListener {
    private LocationManager manager;
    private DashboardDial dial;
    private LinearLayout dashboard;
    private TextView speedText, sourceText, footerText, carText;
    private Button skinButton, demoButton, gpsButton;
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
                refreshDashboard(now); handler.postDelayed(this, 33);
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
        buildDashboard();
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
                refreshDashboard(SystemClock.elapsedRealtime());
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

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void setText(TextView view, String text) {
        if (!text.contentEquals(view.getText())) view.setText(text);
    }
    private TextView label(float size, int color) {
        TextView view = new TextView(this);
        view.setTextSize(size); view.setTextColor(color); view.setGravity(Gravity.CENTER);
        view.setFontFeatureSettings("tnum");
        view.setIncludeFontPadding(false);
        return view;
    }
    private Button button(String title, View.OnClickListener action) {
        Button button = new Button(this);
        button.setText(title); button.setAllCaps(false); button.setTextSize(14);
        button.setTextColor(0xffe4edf7); button.setMinWidth(0); button.setMinimumWidth(0);
        button.setMinHeight(dp(48)); button.setMinimumHeight(dp(48));
        button.setPadding(dp(12), dp(4), dp(12), dp(4));
        StateListDrawable states = new StateListDrawable();
        for (boolean pressed : new boolean[]{true, false}) {
            GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                pressed ? new int[]{0xff2a3c50,0xff182738} : new int[]{0xff19232e,0xff0c121a});
            background.setCornerRadius(dp(10)); background.setStroke(dp(1), pressed ? 0xff8298ac : 0xff344150);
            states.addState(pressed ? new int[]{android.R.attr.state_pressed} : new int[]{}, background);
        }
        button.setBackground(states); button.setOnClickListener(action);
        return button;
    }
    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL); return row;
    }
    private LinearLayout.LayoutParams weightedButton() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,dp(48),1);
        params.setMargins(dp(4),dp(4),dp(4),dp(4)); return params;
    }
    private LinearLayout header() {
        LinearLayout header = row();
        carText = label(18,0xffdce7f0); carText.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        carText.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD));
        header.addView(carText,new LinearLayout.LayoutParams(0,dp(56),1));
        skinButton = button("Skins ▾",new View.OnClickListener() { public void onClick(View view) { openSkins(); }});
        header.addView(skinButton,new LinearLayout.LayoutParams(dp(100),dp(48)));
        return header;
    }
    private void toggleDemo() {
        if (!demo && !driveSession.demoVisible()) return;
        demo=!demo; reading.clear(); speedWarning.reset(); audio.cancelChime();
        if (demo) { stopGps(); demoStart=SystemClock.elapsedRealtime(); } else startGps();
        refreshDashboard(SystemClock.elapsedRealtime());
    }
    private void buildDashboard() {
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        dashboard = new LinearLayout(this); dashboard.setOrientation(LinearLayout.VERTICAL);
        dashboard.setPadding(dp(12),dp(8),dp(12),dp(8));
        // Keep controls within the visible window, including cutout/system-bar insets.
        dashboard.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                int left=insets.getSystemWindowInsetLeft(), top=insets.getSystemWindowInsetTop();
                int right=insets.getSystemWindowInsetRight(), bottom=insets.getSystemWindowInsetBottom();
                if (Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null) {
                    DisplayCutout cutout=insets.getDisplayCutout();
                    left=Math.max(left,cutout.getSafeInsetLeft()); top=Math.max(top,cutout.getSafeInsetTop());
                    right=Math.max(right,cutout.getSafeInsetRight()); bottom=Math.max(bottom,cutout.getSafeInsetBottom());
                }
                view.setPadding(dp(12)+left,dp(8)+top,dp(12)+right,dp(8)+bottom);
                return insets;
            }
        });
        if (!landscape) dashboard.addView(header(),new LinearLayout.LayoutParams(-1,dp(64)));
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        dashboard.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        dial = new DashboardDial(this);
        body.addView(dial,landscape ? new LinearLayout.LayoutParams(0,-1,1.45f) : new LinearLayout.LayoutParams(-1,0,1.55f));
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL);
        if (landscape) panel.setPadding(dp(16),0,0,0);
        body.addView(panel,landscape ? new LinearLayout.LayoutParams(0,-1,1) : new LinearLayout.LayoutParams(-1,0,1));
        if (landscape) panel.addView(header(),new LinearLayout.LayoutParams(-1,dp(56)));
        speedText=label(landscape ? 84 : 100,0xff72efff);
        speedText.setTypeface(Typeface.create("sans-serif-condensed",Typeface.NORMAL));
        if (Build.VERSION.SDK_INT >= 26) speedText.setAutoSizeTextTypeUniformWithConfiguration(36,landscape?100:120,2,android.util.TypedValue.COMPLEX_UNIT_SP);
        panel.addView(speedText,new LinearLayout.LayoutParams(-1,0,1));
        sourceText=label(13,0xffa9bacb);
        panel.addView(sourceText,new LinearLayout.LayoutParams(-1,dp(24)));
        gpsButton=button("Enable GPS",new View.OnClickListener() { public void onClick(View view) { action(); refreshDashboard(SystemClock.elapsedRealtime()); }});
        gpsButton.setTextSize(13);
        LinearLayout.LayoutParams gpsParams=new LinearLayout.LayoutParams(-1,dp(52)); gpsParams.setMargins(0,dp(4),0,dp(4));
        panel.addView(gpsButton,gpsParams);
        LinearLayout controls=row();
        controls.addView(button("Updates",new View.OnClickListener() { public void onClick(View view) { openUpdates(); }}),weightedButton());
        demoButton=button("Demo",new View.OnClickListener() { public void onClick(View view) { toggleDemo(); }});
        controls.addView(demoButton,weightedButton());
        panel.addView(controls,new LinearLayout.LayoutParams(-1,dp(56)));
        footerText=label(11,0xff8a9dad); footerText.setMaxLines(2);
        panel.addView(footerText,new LinearLayout.LayoutParams(-1,dp(28)));
        setContentView(dashboard); dashboard.requestApplyInsets();
        refreshDashboard(SystemClock.elapsedRealtime());
    }
    private void refreshDashboard(long now) {
        boolean fresh=reading.fresh(now), valid=demo||fresh;
        double speed=speedAt(now);
        dashboard.setBackgroundColor(skin.background);
        setText(carText,skin.title + "  /  " + skin.code);
        skinButton.setContentDescription("Choose dashboard skin. Current: " + skin.label);
        int ink=skin == DashboardSkin.INTEGRA ? 0xfff0eee4 : skin.ink;
        speedText.setTextColor(ink);
        setText(speedText,valid?String.format(Locale.US,"%.0f",speed):"—");
        speedText.setContentDescription(valid?String.format(Locale.US,"%.0f miles per hour",speed):"Speed unavailable");
        setText(sourceText,demo?"mph  /  Simulated speed":"mph  /  GPS speed");
        sourceText.setTextColor(demo?0xffffb15e:0xffa9bacb);
        setText(demoButton,demo?"Exit demo":"Demo");
        demoButton.setVisibility(demo||driveSession.demoVisible()?View.VISIBLE:View.INVISIBLE);
        String label=demo?"Return to live GPS":fresh?"GPS connected":status.equals("GPS CONNECTED")?"Signal lost · Waiting for GPS":status;
        setText(gpsButton,label);
        gpsButton.setTextColor(demo?0xffffb15e:fresh?0xffb4efdb:0xffe4edf7);
        setText(footerText,now<greetingUntil?DriveAudio.GREETING:demo?"Display preview only":fresh?String.format(Locale.US,"GPS accuracy ±%.0f m",accuracy):"Use outdoors with a clear view of the sky");
        dial.update(skin,speed,valid,now);
    }
}
