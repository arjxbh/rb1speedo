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
    static volatile boolean diagnosticsActive;
    private static volatile DashcamService owner;
    static final String PAUSE_DIAGNOSTICS = "club.midlife.rb1speed.PAUSE_DIAGNOSTICS";
    static boolean diagnosticsBusy() { DashcamService service=owner; return service!=null && (service.recorder!=null || service.frontRecorder!=null); }
    static final String STOP = "club.midlife.rb1speed.STOP_CAMERA";
    static final String CHANGE_CAMERA = "club.midlife.rb1speed.CHANGE_CAMERA";
    private final Set<String> rejectedCameras = new HashSet<String>();
    private static volatile boolean running;
    private static volatile long lastFrame, frontFrame;
    private volatile CameraRecorder recorder, frontRecorder;
    private static volatile boolean dual;
    private boolean recorderDual, frontStopping;
    private int camerasOpened;
    static boolean dual() { return dual; }
    static final String START = "club.midlife.rb1speed.START_CAMERA";
    static final String START_360 = "club.midlife.rb1speed.START_360";
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
    static boolean recording() { return running && DashcamPolicy.recordingFresh(lastFrame, SystemClock.elapsedRealtime()) && (!dual || DashcamPolicy.recordingFresh(frontFrame, SystemClock.elapsedRealtime())); }
    static void maybeStart(Context context) {
        if (diagnosticsActive) return;
        if (System.currentTimeMillis() < context.getSharedPreferences("dashcam", 0).getLong("cameraRetryAfter", 0)) return;
        if (running || !context.getSharedPreferences("dashcam", 0).getBoolean("enabled", false) ||
            context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
        Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null || battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) == 0) return;
        try { context.startForegroundService(new Intent(context, DashcamService.class)); }
        catch (RuntimeException e) { report(context, "Open Camera to resume recording: " + e.getClass().getSimpleName()); }
    }
    @Override public void onCreate() {
        super.onCreate(); owner=this;
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
        if (destroyed || switching || frontStopping || diagnosticsActive) return;
        if (frontRecorder != null && (recorder == null || !running || !dual || switching || recorderDual != dual)) {
            if (!frontStopping) { frontStopping = true; frontRecorder.stop("Switching camera mode"); }
            return;
        }
        if (recorder != null) {
            if (recorderDual != dual || recorderRecording != running || (!running && previewListener == null)) {
                switching = true; stopping = true; recorder.stop("Switching camera mode");
            } else recorder.preview(previewListener);
            return;
        }
        if (!running && previewListener == null) { releaseForeground(); stopSelf(); return; }
        final CameraCatalog.Option[] pair;
        try { pair = running && dual ? CameraCatalog.concurrent(this) : null; }
        catch (Exception e) {
            getSharedPreferences("dashcam", 0).edit().putLong("cameraRetryAfter", System.currentTimeMillis() + 60000).apply();
            stopRecording(e.getMessage()); report(this, e.getMessage()); return;
        }
        recorderRecording = running; recorderDual = dual; frontFrame = 0;
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
                if (wasRecording && recorderDual && running) {
                    fail360(reason);
                }
                if (wasRecording) {
                    running = false; lastFrame = 0;
                    if (!reason.equals("Paused for camera diagnostics"))
                        getSharedPreferences("dashcam", 0).edit().putLong("cameraRetryAfter", reason.startsWith("Parked:") ? 0 : System.currentTimeMillis() + 60000).apply();
                    report(DashcamService.this, reason); releaseForeground(); UploadJob.schedule(DashcamService.this, false);
                }
                getSharedPreferences("dashcam", 0).edit().putString("previewStatus", reason).apply();
                if (!running) releaseForeground();
                stopSelf();
                if (wasRecording && previewListener != null) reconcile();
            }});
        }});
        if (pair != null) {
            camerasOpened = 0;
            final CameraRecorder rear = recorder;
            Runnable opened = new Runnable() { public void run() {
                main.post(new Runnable() { public void run() {
                    if (destroyed || !running || !dual || recorder != rear || switching || frontStopping) return;
                    if (++camerasOpened == 2 && frontRecorder != null) { rear.configure(); frontRecorder.configure(); }
                }});
            }};
            recorder.select(pair[0], false).whenOpened(opened);
            frontRecorder = new CameraRecorder(this, true, new HashSet<String>(), new CameraRecorder.Listener() {
                public void written() { if (!destroyed && running && !frontStopping) frontFrame = SystemClock.elapsedRealtime(); }
                public void stopped(final String reason, String retryCamera) {
                    main.post(new Runnable() { public void run() {
                        frontRecorder = null;
                        boolean expected = frontStopping; frontStopping = false;
                        if (destroyed) return;
                        if (expected) reconcile();
                        else fail360(reason);
                    }});
                }
            }).select(pair[1], true).whenOpened(opened);
            frontRecorder.start();
            main.postDelayed(new Runnable() { public void run() {
                if (!destroyed && running && dual && recorder == rear && !switching && !frontStopping && !recording())
                    fail360("Both cameras did not produce video within 15 seconds on " + Build.MODEL + " / " + Build.DISPLAY);
            }}, 15000);
        }
        recorder.preview(previewListener); recorder.start();
    }
    private void fail360(String reason) {
        String message = "360 recording stopped: " + reason;
        getSharedPreferences("dashcam", 0).edit().putLong("cameraRetryAfter", System.currentTimeMillis() + 60000).apply();
        report(this, message);
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show();
        stopRecording(message);
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
        if (frontRecorder != null) { frontStopping = true; frontRecorder.stop(reason); }
        if (recorder != null) { stopping = true; recorder.stop(reason); }
        else { releaseForeground(); stopSelf(); }
    }
    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (intent != null && PAUSE_DIAGNOSTICS.equals(intent.getAction())) {
            previewListener=null;
            stopRecording("Paused for camera diagnostics");
            if (recorder==null && frontRecorder==null) stopSelf();
        } else if (diagnosticsActive) {
            // A queued automatic foreground start must not compete with a diagnostic probe.
            foreground(); releaseForeground(); stopRecording("Paused for camera diagnostics"); stopSelf();
        } else if (intent != null && STOP.equals(intent.getAction())) {
            getSharedPreferences("dashcam", 0).edit().putBoolean("enabled", false).apply(); stopRecording("Stopped manually");
        } else if (intent != null && CHANGE_CAMERA.equals(intent.getAction())) {
            changeCamera();
        } else {
            if (intent != null && START_360.equals(intent.getAction())) {
                // Validate before changing the requested mode or closing an active rear camera.
                try { CameraCatalog.concurrent(this); }
                catch (Exception e) {
                    if (!running) foreground(); // Satisfy startForegroundService even when rejected.
                    android.widget.Toast.makeText(this, "360 recording unavailable: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
                    if (!running) { report(this, e.getMessage()); releaseForeground(); stopSelf(); }
                    return START_NOT_STICKY;
                }
            }
            if (intent != null && (START.equals(intent.getAction()) || START_360.equals(intent.getAction()))) {
                dual = START_360.equals(intent.getAction());
                getSharedPreferences("dashcam", 0).edit().putBoolean("enabled", true).putString("cameraChoice", CameraCatalog.AUTO).apply();
            } else if (!running) dual = false;
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
        if (owner==this) owner=null;
        destroyed = true; main.removeCallbacks(unplugged); unregisterReceiver(battery);
        if (Build.VERSION.SDK_INT >= 29 && thermal != null) power.removeThermalStatusListener(thermal);
        if (recorder != null) { recorder.preview(null); recorder.stop("Recording service stopped"); }
        if (frontRecorder != null) frontRecorder.stop("Recording service stopped");
        previewListener = null; running = false; lastFrame = 0; releaseForeground();
        UploadJob.schedule(this, false); super.onDestroy();
    }
}
