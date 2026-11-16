package club.midlife.rb1speed;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;

/** Started only by visible user interaction; already-started recording survives UI pauses. */
public final class DashcamService extends Service {
    static final String STOP = "club.midlife.rb1speed.STOP_CAMERA";
    private static volatile boolean running;
    private CameraRecorder recorder;
    private long disconnectAt = -1;
    private final Handler main = new Handler(Looper.getMainLooper());
    private PowerManager power;
    private PowerManager.WakeLock wake;
    private PowerManager.OnThermalStatusChangedListener thermal;
    private final Runnable unplugged = new Runnable() { public void run() { stopRecording("Parked: external power disconnected"); }};
    private final BroadcastReceiver battery = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            boolean plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
            long now = SystemClock.elapsedRealtime();
            disconnectAt = DashcamPolicy.powerStopAt(plugged, now, disconnectAt);
            main.removeCallbacks(unplugged);
            if (disconnectAt >= 0) main.postDelayed(unplugged, Math.max(0, disconnectAt - now));
            if (intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) >= 450) stopRecording("Paused: battery temperature at least 45°C");
        }
    };
    static void report(Context context, String text) { context.getSharedPreferences("dashcam", 0).edit().putString("cameraStatus", text).apply(); }
    static boolean running() { return running; }
    static void maybeStart(Context context) {
        if (System.currentTimeMillis() < context.getSharedPreferences("dashcam", 0).getLong("cameraRetryAfter", 0)) return;
        if (running || !context.getSharedPreferences("dashcam", 0).getBoolean("enabled", false) ||
            context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
        Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null || battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) == 0) return;
        try { context.startForegroundService(new Intent(context, DashcamService.class)); }
        catch (RuntimeException e) { report(context, "Open Camera to resume recording: " + e.getClass().getSimpleName()); }
    }
    @Override public void onCreate() {
        super.onCreate(); running = true;
        NotificationManager notifications = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        notifications.createNotificationChannel(new NotificationChannel("dashcam", "Dashcam recording", NotificationManager.IMPORTANCE_LOW));
        Intent open = new Intent(this, DashcamActivity.class);
        PendingIntent content = PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 2, new Intent(this, DashcamService.class).setAction(STOP), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, "dashcam").setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle("RB1 dashcam active").setContentText("Camera service running · tap for status")
            .setContentIntent(content).setOngoing(true).addAction(new Notification.Action.Builder(null, "Stop", stop).build()).build();
        startForeground(81, n);
        power = (PowerManager)getSystemService(POWER_SERVICE);
        wake = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RB1:camera"); wake.acquire(4 * 60 * 60 * 1000L);
        if (Build.VERSION.SDK_INT >= 29) {
            thermal = new PowerManager.OnThermalStatusChangedListener() { public void onThermalStatusChanged(int status) {
                if (status >= PowerManager.THERMAL_STATUS_SEVERE) stopRecording("Paused: device thermal protection");
            }};
            power.addThermalStatusListener(getMainExecutor(), thermal);
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(battery, new IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(battery, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        getSharedPreferences("dashcam", 0).edit().putLong("recordingStarted", 0).apply();
        report(this, "Starting rear camera");
        ClipAssembler.enqueue(this, true);
        recorder = new CameraRecorder(this, new CameraRecorder.Listener() { public void stopped(final String reason) {
            main.post(new Runnable() { public void run() { recorder = null; getSharedPreferences("dashcam", 0).edit().putLong("cameraRetryAfter", reason.startsWith("Parked:") ? 0 : System.currentTimeMillis() + 60000).apply(); report(DashcamService.this, reason); stopSelf(); }});
        }}); recorder.start();
    }
    private void stopRecording(String reason) { if (recorder != null) recorder.stop(reason); }
    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (intent != null && STOP.equals(intent.getAction())) {
            getSharedPreferences("dashcam", 0).edit().putBoolean("enabled", false).apply(); stopRecording("Stopped manually");
        } return START_NOT_STICKY;
    }
    @Override public void onDestroy() {
        main.removeCallbacks(unplugged); unregisterReceiver(battery);
        if (Build.VERSION.SDK_INT >= 29 && thermal != null) power.removeThermalStatusListener(thermal);
        if (recorder != null) recorder.stop("Recording service stopped");
        if (wake != null && wake.isHeld()) wake.release(); running = false;
        UploadJob.schedule(this, false); super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
