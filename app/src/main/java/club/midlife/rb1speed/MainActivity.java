package club.midlife.rb1speed;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.hardware.*;
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
import android.widget.ScrollView;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.content.res.Configuration;
import java.util.Locale;

public class MainActivity extends Activity implements LocationListener {
    private LocationManager manager;
    private DashboardDial dial;
    private LinearLayout dashboard;
    private TextView speedText, sourceText, footerText, carText, recordingText;
    private Button settingsButton, demoButton, gpsButton;
    private AlertDialog settingsDialog;
    private DashboardSkin skin;
    private final SpeedReading reading = new SpeedReading();
    private final DriveSession driveSession = new DriveSession();
    private final SpeedWarning speedWarning = new SpeedWarning();
    private DriveAudio audio;
    private boolean demo, active, subscribed, asked;
    private boolean greetingShown;
    private SensorManager sensors;
    private boolean powered;
    private float lux = Float.NaN;
    private Location daylightLocation;
    private final SensorEventListener lightListener = new SensorEventListener() {
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
        public void onSensorChanged(SensorEvent event) {
            if (active && event.values.length > 0 && Float.isFinite(event.values[0]) && event.values[0] >= 0) {
                lux = event.values[0]; updateBrightness();
            }
        }
    };
    private final BroadcastReceiver powerReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                greetingShown = false; greetingUntil = 0; audio.pause();
                return;
            }
            boolean wasPowered = powered;
            powered = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
            if (active) {
                updatePower();
                if (powered) DashcamService.maybeStart(MainActivity.this);
                if (powered && !wasPowered && ((PowerManager)getSystemService(POWER_SERVICE)).isInteractive()) greetOnce();
            }
        }
    };
    private void updatePower() {
        if (powered) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    private void greetOnce() {
        if (greetingShown) return;
        greetingShown = true;
        greetingUntil = SystemClock.elapsedRealtime() + 6000;
        audio.resume(true);
    }
    private AlertDialog debugDialog;
    private TextView debugText;
    private float debugBrightness = -1f;
    private long debugRefreshAt;
    private String brightnessSource = "Waiting";
    private final Runnable endBrightnessPreview = new Runnable() {
        public void run() { debugBrightness = -1f; updateBrightness(); refreshDebug(); }
    };
    private long brightnessChecked;
    private void updateBrightness() {
        float ambient = lux;
        java.util.Calendar clock = java.util.Calendar.getInstance();
        boolean night = daylightLocation == null
            ? clock.get(java.util.Calendar.HOUR_OF_DAY) < 7 || clock.get(java.util.Calendar.HOUR_OF_DAY) >= 19
            : DisplayBrightness.isNight(System.currentTimeMillis(), daylightLocation.getLatitude(), daylightLocation.getLongitude());
        float brightness = debugBrightness >= 0 ? debugBrightness : DisplayBrightness.level(ambient, night);
        brightnessSource = debugBrightness >= 0 ? "Temporary test" : Float.isFinite(ambient) ? "Ambient sensor"
            : (daylightLocation == null ? "Local clock fallback" : "GPS sunset/sunrise fallback") + (night ? " (night)" : " (day)");
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        if (attributes.screenBrightness != brightness) {
            attributes.screenBrightness = brightness; getWindow().setAttributes(attributes);
        }
        if (debugDialog != null && debugDialog.getWindow() != null) {
            WindowManager.LayoutParams panel = debugDialog.getWindow().getAttributes();
            panel.screenBrightness = brightness; debugDialog.getWindow().setAttributes(panel);
        }
    }
    private void previewBrightness(float value) {
        debugBrightness = value;
        handler.removeCallbacks(endBrightnessPreview);
        handler.postDelayed(endBrightnessPreview, 5000);
        updateBrightness(); refreshDebug();
    }
    private void refreshDebug() {
        if (debugText == null) return;
        boolean keepOn = (getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0;
        float output = getWindow().getAttributes().screenBrightness;
        String timeout;
        try { timeout = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT) / 1000 + " seconds"; }
        catch (Settings.SettingNotFoundException e) { timeout = "unavailable"; }
        String sensor = sensors.getDefaultSensor(Sensor.TYPE_LIGHT) == null ? "not available" :
            Float.isFinite(lux) ? String.format(Locale.US, "%.1f lux", lux) : "waiting for reading";
        setText(debugText, "External power: " + (powered ? "connected" : "battery") +
            "\nKeep screen on: " + (keepOn ? "enabled" : "disabled — normal timeout") +
            "\nSystem timeout: " + timeout + "\n\n" + audio.volumeDiagnostics() +
            "\nGreeting used this screen session: " + (greetingShown ? "yes" : "no") +
            "\n\nLight sensor: " + sensor + "\nBrightness source: " + brightnessSource +
            "\nRequested app brightness: " + (output < 0 ? "Android system setting" : Math.round(output * 100) + "%") +
            "\n\nDashcam: " + getSharedPreferences("dashcam", 0).getString("cameraStatus", "not started") +
            "\nUploads: " + getSharedPreferences("dashcam", 0).getString("uploadStatus", "not started") +
            "\n\nBrightness is an app request, not a measurement of panel output. Tests return to automatic control after five seconds.\n" +
            "Cover/uncover the sensor or unplug/reconnect power to check live values. Touching controls restarts Android’s idle timer.");
    }
    private void openDebug() {
        if (debugDialog != null) return;
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(16), dp(8), dp(16), dp(8));
        debugText = label(14, 0xffe4edf7); debugText.setGravity(Gravity.START);
        debugText.setTextIsSelectable(true);
        content.addView(debugText, new LinearLayout.LayoutParams(-1, -2));
        content.addView(button("Apply 70% media volume", new View.OnClickListener() {
            public void onClick(View view) { audio.setLaunchVolume(); refreshDebug(); }
        }), new LinearLayout.LayoutParams(-1, dp(48)));
        content.addView(button("Test dim · 5 seconds", new View.OnClickListener() {
            public void onClick(View view) { previewBrightness(.08f); }
        }), new LinearLayout.LayoutParams(-1, dp(48)));
        content.addView(button("Test bright · 5 seconds", new View.OnClickListener() {
            public void onClick(View view) { previewBrightness(1f); }
        }), new LinearLayout.LayoutParams(-1, dp(48)));
        ScrollView scroll = new ScrollView(this); scroll.addView(content);
        debugDialog = new AlertDialog.Builder(this).setTitle("Debug · parked checks")
            .setView(scroll).setPositiveButton("Close", null).create();
        debugDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            public void onDismiss(DialogInterface dialog) {
                handler.removeCallbacks(endBrightnessPreview);
                debugBrightness = -1f; debugText = null; debugDialog = null;
                if (active) updateBrightness();
            }
        });
        debugDialog.show(); updateBrightness(); refreshDebug();
    }
    private String status = "TAP TO ENABLE GPS";
    private float accuracy;
    private long demoStart;
    private long greetingUntil;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        public void run() {
            if (active) {
                long now = SystemClock.elapsedRealtime();
                if (debugText != null && now - debugRefreshAt >= 500) { debugRefreshAt = now; refreshDebug(); }
                if (now - brightnessChecked >= 1000) { brightnessChecked = now; updateBrightness(); }
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
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        manager = (LocationManager)getSystemService(LOCATION_SERVICE);
        audio = new DriveAudio(this);
        if (state == null) audio.setLaunchVolume();
        greetingShown = state != null && state.getBoolean("greetingShown", false);
        skin = DashboardSkin.fromId(getPreferences(MODE_PRIVATE).getString("skin", "rb1"));
        sensors = (SensorManager)getSystemService(SENSOR_SERVICE);
        IntentFilter powerFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        powerFilter.addAction(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(powerReceiver, powerFilter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(powerReceiver, powerFilter);
        buildDashboard();
    }
    @Override public void onResume() {
        super.onResume(); active = true; demo = false; reading.clear();
        driveSession.reset(); speedWarning.reset();
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        powered = battery != null && battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
        updatePower();
        lux = Float.NaN;
        Sensor light = sensors.getDefaultSensor(Sensor.TYPE_LIGHT);
        if (light != null) sensors.registerListener(lightListener, light, SensorManager.SENSOR_DELAY_NORMAL);
        updateBrightness();
        audio.resume(false); greetOnce(); startGps(); handler.post(ticker);
        DashcamService.maybeStart(this); UploadJob.schedule(this, false);
    }
    @Override public void onPause() {
        active = false; handler.removeCallbacks(ticker); audio.pause();
        if (settingsDialog != null) settingsDialog.dismiss();
        if (debugDialog != null) debugDialog.dismiss();
        handler.removeCallbacks(endBrightnessPreview);
        debugBrightness = -1f;
        sensors.unregisterListener(lightListener);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        attributes.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(attributes);
        speedWarning.reset(); stopGps(); reading.clear(); super.onPause();
    }
    @Override public void onDestroy() { unregisterReceiver(powerReceiver); audio.close(); super.onDestroy(); }
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
    private void openSettings() {
        if (!driveSession.controlsVisible() || settingsDialog != null) return;
        settingsDialog = new AlertDialog.Builder(this).setTitle("Settings")
            .setItems(new String[]{"Skins", "Camera & clips", "Aim camera · live preview", "Debug", "Updates"},
                new DialogInterface.OnClickListener() { public void onClick(DialogInterface dialog, int which) {
                    if (which == 0) openSkins();
                    else if (which == 1 || which == 2) startActivity(new Intent(MainActivity.this, DashcamActivity.class).putExtra("preview", which == 2));
                    else if (which == 3) openDebug();
                    else openUpdates();
                }})
            .setNegativeButton("Close", null).create();
        settingsDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            public void onDismiss(DialogInterface dialog) { settingsDialog = null; }
        });
        settingsDialog.show();
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
        if (location.hasAccuracy() && location.getAccuracy() <= 1000) daylightLocation = new Location(location);
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
        settingsButton = button("Settings",new View.OnClickListener() { public void onClick(View view) { openSettings(); }});
        header.addView(settingsButton,new LinearLayout.LayoutParams(dp(100),dp(48)));
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
        recordingText = label(13, 0xffa9bacb);
        controls.addView(recordingText, new LinearLayout.LayoutParams(0, dp(48), 1));
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
        settingsButton.setContentDescription("Settings: skins, camera, aiming preview, debug and updates");
        settingsButton.setVisibility(driveSession.controlsVisible() ? View.VISIBLE : View.INVISIBLE);
        if (!driveSession.controlsVisible() && settingsDialog != null) settingsDialog.dismiss();
        boolean recording = DashcamService.recording();
        setText(recordingText, recording ? "● REC" : DashcamService.running() ? "○ Starting camera" : "○ Camera off");
        recordingText.setTextColor(recording ? 0xffff6868 : 0xffa9bacb);
        recordingText.setContentDescription(recording ? "Dashcam recording" : DashcamService.running() ? "Dashcam starting or waiting for video" : "Dashcam not recording");
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
