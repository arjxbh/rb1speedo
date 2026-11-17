package club.midlife.rb1speed;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import java.util.HashSet;
import java.util.Set;

/** One camera owner: foreground loop recording, or a preview bound to a visible Activity. */
public final class DashcamService extends Service {
    static final String STOP = "club.midlife.rb1speed.STOP_CAMERA";
    static final String CHANGE_CAMERA = "club.midlife.rb1speed.CHANGE_CAMERA";
    private final Set<String> rejectedCameras = new HashSet<String>();
    private static volatile boolean running;
    private static volatile long lastFrame;
    private CameraRecorder recorder;
    private volatile boolean switching, destroyed;
    private boolean recorderRecording, foreground, stopping;
    private CameraPreviewFrames.Listener previewListener;
    private long disconnectAt = -1;
    private final Handler main = new Handler(Looper.getMainLooper());
    private PowerManager power;
    private PowerManager.WakeLock wake;
    private PowerManager.OnThermalStatusChangedListener thermal;
    private final Runnable unplugged = new Runnable() { public void run() { stopRecording("Parked: external power disconnected"); }};
    private final BroadcastReceiver battery = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (!running) return;
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
    static boolean recording() { return running && DashcamPolicy.recordingFresh(lastFrame, SystemClock.elapsedRealtime()); }
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
        super.onCreate();
        power = (PowerManager)getSystemService(POWER_SERVICE);
        if (Build.VERSION.SDK_INT >= 29) {
            thermal = new PowerManager.OnThermalStatusChangedListener() { public void onThermalStatusChanged(int status) {
                if (status >= PowerManager.THERMAL_STATUS_SEVERE) stopRecording("Paused: device thermal protection");
            }};
            power.addThermalStatusListener(getMainExecutor(), thermal);
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(battery, new IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(battery, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }
    private void foreground() {
        if (foreground) return;
        NotificationManager notifications = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        notifications.createNotificationChannel(new NotificationChannel("dashcam", "Dashcam recording", NotificationManager.IMPORTANCE_LOW));
        PendingIntent content = PendingIntent.getActivity(this, 1, new Intent(this, DashcamActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 2, new Intent(this, DashcamService.class).setAction(STOP), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, "dashcam").setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle("RB1 dashcam active").setContentText("Camera service running · tap for status")
            .setContentIntent(content).setOngoing(true).addAction(new Notification.Action.Builder(null, "Stop", stop).build()).build();
        startForeground(81, n); foreground = true;
        wake = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RB1:camera"); wake.acquire(4 * 60 * 60 * 1000L);
    }
    private void releaseForeground() {
        if (wake != null && wake.isHeld()) wake.release(); wake = null;
        if (foreground) { stopForeground(STOP_FOREGROUND_REMOVE); foreground = false; }
    }
    /** Always wait for the old camera to close before promoting a preview to recording. */
    private void reconcile() {
        if (destroyed || switching) return;
        if (recorder != null) {
            if (recorderRecording != running || (!running && previewListener == null)) {
                switching = true; stopping = true; recorder.stop("Switching camera mode");
            } else recorder.preview(previewListener);
            return;
        }
        if (!running && previewListener == null) { releaseForeground(); stopSelf(); return; }
        recorderRecording = running;
        if (running) {
            lastFrame = 0;
            getSharedPreferences("dashcam", 0).edit().putLong("recordingStarted", 0).apply();
            report(this, "Starting rear camera"); ClipAssembler.enqueue(this, true);
        }
        recorder = new CameraRecorder(this, recorderRecording, new HashSet<String>(rejectedCameras), new CameraRecorder.Listener() {
          public void written() { if (!destroyed && running && !switching) lastFrame = SystemClock.elapsedRealtime(); }
          public void stopped(final String reason, final String retryCamera) {
            main.post(new Runnable() { public void run() {
                if (destroyed) return;
                boolean wasRecording = recorderRecording;
                recorder = null; stopping = false;
                if (switching) {
                    switching = false;
                    if (!running) { lastFrame = 0; releaseForeground(); UploadJob.schedule(DashcamService.this, false); }
                    reconcile(); return;
                }
                if (retryCamera != null && (running || previewListener != null)) {
                    rejectedCameras.add(retryCamera); lastFrame = 0;
                    getSharedPreferences("dashcam", 0).edit().putString("cameraSelectionStatus", "Camera unavailable; trying the next widest compatible lens").apply();
                    reconcile(); return;
                }
                if (wasRecording) {
                    running = false; lastFrame = 0;
                    getSharedPreferences("dashcam", 0).edit().putLong("cameraRetryAfter", reason.startsWith("Parked:") ? 0 : System.currentTimeMillis() + 60000).apply();
                    report(DashcamService.this, reason); releaseForeground(); UploadJob.schedule(DashcamService.this, false);
                }
                getSharedPreferences("dashcam", 0).edit().putString("previewStatus", reason).apply();
                if (!running) releaseForeground();
                stopSelf();
                if (wasRecording && previewListener != null) reconcile();
            }});
        }});
        recorder.preview(previewListener); recorder.start();
    }
    private void changeCamera() {
        rejectedCameras.clear(); lastFrame = 0;
        getSharedPreferences("dashcam", 0).edit().putLong("cameraRetryAfter", 0)
            .putString("previewStatus", "Switching camera").apply();
        if (recorder != null) {
            switching = true; stopping = true; recorder.preview(null); recorder.stop("Changing camera");
        } else reconcile();
    }
    private void stopRecording(String reason) {
        if (!running && recorder == null) return;
        running = false; lastFrame = 0; main.removeCallbacks(unplugged); disconnectAt = -1;
        switching = false;
        if (recorder != null) { stopping = true; recorder.stop(reason); }
        else { releaseForeground(); stopSelf(); }
    }
    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (intent != null && STOP.equals(intent.getAction())) {
            getSharedPreferences("dashcam", 0).edit().putBoolean("enabled", false).apply(); stopRecording("Stopped manually");
        } else if (intent != null && CHANGE_CAMERA.equals(intent.getAction())) {
            changeCamera();
        } else {
            if (!running) rejectedCameras.clear();
            foreground(); running = true;
            if (stopping) switching = true;
            reconcile();
            Intent current = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (current != null) battery.onReceive(this, current);
        }
        return START_NOT_STICKY;
    }
    final class PreviewBinder extends Binder {
        void cameraChanged() { changeCamera(); }
        void listen(CameraPreviewFrames.Listener listener) {
            previewListener = listener;
            if (listener != null) getSharedPreferences("dashcam", 0).edit().putString("previewStatus", "Opening rear-camera preview").apply();
            reconcile();
        }
    }
    @Override public IBinder onBind(Intent intent) { return new PreviewBinder(); }
    @Override public boolean onUnbind(Intent intent) { previewListener = null; reconcile(); return false; }
    @Override public void onDestroy() {
        destroyed = true; main.removeCallbacks(unplugged); unregisterReceiver(battery);
        if (Build.VERSION.SDK_INT >= 29 && thermal != null) power.removeThermalStatusListener(thermal);
        if (recorder != null) { recorder.preview(null); recorder.stop("Recording service stopped"); }
        previewListener = null; running = false; lastFrame = 0; releaseForeground();
        UploadJob.schedule(this, false); super.onDestroy();
    }
}
