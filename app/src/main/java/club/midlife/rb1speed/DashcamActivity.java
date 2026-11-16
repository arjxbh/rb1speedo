package club.midlife.rb1speed;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.*;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** Parked setup and diagnostics. Recording lives in the service, not this Activity. */
public final class DashcamActivity extends Activity {
    private TextView status;
    private EditText endpoint, code;
    private Button pair;
    private CameraPreviewView preview;
    private TextView previewStatus;
    private Button previewButton;
    private boolean previewRequested, previewBound, previewHasFrame;
    private long previewFrameAt;
    private DashcamService.PreviewBinder previewService;
    private final CameraPreviewFrames.Listener previewListener = new CameraPreviewFrames.Listener() {
        public void frame(android.graphics.Bitmap bitmap) {
            if (!visible || !previewRequested) return;
            preview.setImageBitmap(bitmap); previewHasFrame = true; previewFrameAt = SystemClock.elapsedRealtime(); refreshPreviewStatus();
        }
    };
    private final ServiceConnection previewConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder binder) {
            previewService = (DashcamService.PreviewBinder)binder;
            if (visible && previewRequested) previewService.listen(previewListener);
        }
        public void onServiceDisconnected(ComponentName name) {
            previewService = null; previewHasFrame = false; refreshPreviewStatus();
        }
    };
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private boolean visible;
    private final BroadcastReceiver powerReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (!visible) return;
            if (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                DashcamService.maybeStart(DashcamActivity.this);
            } else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    };
    private final Runnable refresh = new Runnable() { public void run() { if (visible) { showStatus(); main.postDelayed(this, 1000); } }};
    private Button button(String title, LinearLayout parent, View.OnClickListener listener) {
        Button button = new Button(this); button.setText(title); button.setAllCaps(false); button.setMinHeight(48);
        parent.addView(button, new LinearLayout.LayoutParams(-1, -2)); button.setOnClickListener(listener); return button;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this); LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL); content.setPadding(24, 16, 24, 24); scroll.addView(content);
        final LinearLayout insetContent = content;
        scroll.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                int margin = Math.round(12 * getResources().getDisplayMetrics().density);
                insetContent.setPadding(margin + insets.getSystemWindowInsetLeft(), margin + insets.getSystemWindowInsetTop(),
                    margin + insets.getSystemWindowInsetRight(), margin + insets.getSystemWindowInsetBottom());
                return insets;
            }
        });
        TextView title = new TextView(this); title.setText("Dashcam · setup while parked"); title.setTextSize(23); content.addView(title);
        previewRequested = state == null ? getIntent().getBooleanExtra("preview", false) : state.getBoolean("preview", false);
        previewButton = button("Show aiming preview", content, new View.OnClickListener() { public void onClick(View view) {
            previewRequested = !previewRequested; updatePreview();
        }});
        preview = new CameraPreviewView(this);
        content.addView(preview, new LinearLayout.LayoutParams(-1, Math.round(220 * getResources().getDisplayMetrics().density)));
        previewStatus = new TextView(this); previewStatus.setTextSize(14); content.addView(previewStatus);
        status = new TextView(this); status.setTextSize(15); status.setTextIsSelectable(true); content.addView(status);
        button("Enable recording on external power", content, new View.OnClickListener() { public void onClick(View view) {
            new AlertDialog.Builder(DashcamActivity.this).setTitle("Enable dashcam loop?")
                .setMessage("Records silent 1080p video while powered. The 32 GiB loop can overwrite the oldest unprotected footage before it uploads. Save incident protects current and surrounding clips. Keep at least 5 GiB free. Camera use and charging generate heat.")
                .setPositiveButton("Enable", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int which) {
                    getSharedPreferences("dashcam", 0).edit().putBoolean("enabled", true).putLong("cameraRetryAfter", 0).commit();
                    if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.CAMERA}, 81);
                    else notificationsAndStart();
                }}).setNegativeButton("Cancel", null).show();
        }});
        button("Stop and disable recording", content, new View.OnClickListener() { public void onClick(View view) {
            getSharedPreferences("dashcam", 0).edit().putBoolean("enabled", false).commit();
            if (DashcamService.running()) startService(new Intent(DashcamActivity.this, DashcamService.class).setAction(DashcamService.STOP));
            showStatus();
        }});
        button("Save incident · protect surrounding clips", content, new View.OnClickListener() { public void onClick(View view) {
            ClipStore.get(DashcamActivity.this).protectIncident(); UploadJob.schedule(DashcamActivity.this, true); showStatus();
        }});
        button("View local clips", content, new View.OnClickListener() { public void onClick(View view) { clips(); }});
        button("Local loop size", content, new View.OnClickListener() { public void onClick(View view) {
            new AlertDialog.Builder(DashcamActivity.this).setTitle("Local recording quota · 5 GiB free reserve")
                .setItems(new String[]{"8 GiB", "16 GiB", "32 GiB", "64 GiB"}, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        long size = new long[]{8, 16, 32, 64}[which] * 1073741824L;
                        getSharedPreferences("dashcam", 0).edit().putLong("quota", size).commit(); showStatus();
                    }
                }).setNegativeButton("Cancel", null).show();
        }});
        endpoint = new EditText(this); endpoint.setSingleLine(true); endpoint.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        endpoint.setHint("HTTPS API address"); endpoint.setText(getSharedPreferences("dashcam", 0).getString("endpoint", "https://dashcam.midlifemotor.club")); content.addView(endpoint);
        code = new EditText(this); code.setSingleLine(true); code.setHint("One-time pairing code");
        code.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); code.setSaveEnabled(false); content.addView(code);
        pair = button("Pair this phone", content, new View.OnClickListener() { public void onClick(View view) { enroll(); }});
        button("Upload now on Wi-Fi", content, new View.OnClickListener() { public void onClick(View view) {
            UploadJob.schedule(DashcamActivity.this, true); Toast.makeText(DashcamActivity.this, "Upload requested; Wi-Fi and battery limits still apply", Toast.LENGTH_LONG).show();
        }});
        button("Reset phone pairing", content, new View.OnClickListener() { public void onClick(View view) {
            new AlertDialog.Builder(DashcamActivity.this).setMessage("Remove this phone's stored API credentials? Local footage remains. Revoke the old device on the server if retiring it.")
                .setPositiveButton("Reset pairing", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int which) {
                    io.execute(new Runnable() { public void run() {
                        DeviceAuth.forget(DashcamActivity.this);
                        main.post(new Runnable() { public void run() { if (!isDestroyed()) showStatus(); }});
                    }});
                }}).setNegativeButton("Cancel", null).show();
        }});
        button("Back to speedometer", content, new View.OnClickListener() { public void onClick(View view) { finish(); }});
        setContentView(scroll); scroll.requestApplyInsets();
    }
    private void notificationsAndStart() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 82);
        DashcamService.maybeStart(this);
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 81 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) notificationsAndStart();
        if (request == 83) {
            if (results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED) previewRequested = false;
            updatePreview();
        }
    }
    @Override public void onResume() {
        super.onResume(); visible = true; main.post(refresh);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(powerReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(powerReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        DashcamService.maybeStart(this);
        updatePreview();
    }
    @Override public void onPause() {
        visible = false; main.removeCallbacks(refresh); unregisterReceiver(powerReceiver); detachPreview();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); super.onPause();
    }
    @Override public void onDestroy() { io.shutdown(); super.onDestroy(); }
    @Override public void onSaveInstanceState(Bundle state) { state.putBoolean("preview", previewRequested); super.onSaveInstanceState(state); }
    private void updatePreview() {
        preview.setVisibility(previewRequested ? View.VISIBLE : View.GONE);
        previewStatus.setVisibility(previewRequested ? View.VISIBLE : View.GONE);
        previewButton.setText(previewRequested ? "Hide aiming preview" : "Show aiming preview");
        if (!visible || !previewRequested) { detachPreview(); return; }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 83); return;
        }
        if (!previewBound) {
            previewHasFrame = false;
            previewBound = bindService(new Intent(this, DashcamService.class), previewConnection, Context.BIND_AUTO_CREATE);
        }
        refreshPreviewStatus();
    }
    private void detachPreview() {
        if (previewService != null) previewService.listen(null);
        previewService = null;
        if (previewBound) { unbindService(previewConnection); previewBound = false; }
        previewHasFrame = false;
        if (preview != null) preview.setImageDrawable(null);
    }
    private void refreshPreviewStatus() {
        if (previewStatus == null || !previewRequested) return;
        if (previewHasFrame && SystemClock.elapsedRealtime() - previewFrameAt >= 3000) {
            previewHasFrame = false; preview.setImageDrawable(null);
        }
        previewStatus.setText(previewHasFrame ?
            (DashcamService.recording() ? "● Recording · live aiming preview" : "Live preview · preview alone does not save video") + "\nAlign the horizon with the guides. Rear camera · no microphone." :
            "Waiting for camera frames · " + getSharedPreferences("dashcam", 0).getString("previewStatus", "Opening rear camera"));
    }
    private void showStatus() {
        refreshPreviewStatus();
        SharedPreferences p = getSharedPreferences("dashcam", 0); ClipStore store = ClipStore.get(this);
        long pending = 0, saved = 0; int count = 0, protectionPending = 0; long oldest = Long.MAX_VALUE;
        for (JSONObject c : store.clips()) {
            if (!"READY".equals(c.optString("state"))) continue;
            count++;
            if (c.optInt("verified") == 0) { pending += c.optLong("bytes"); oldest = Math.min(oldest, c.optLong("started")); }
            if (c.optInt("protected") != 0) { saved += c.optLong("bytes"); if (c.optInt("remoteProtected") == 0) protectionPending++; }
        }
        String pairing;
        try { pairing = DeviceAuth.get(this).paired() ? "paired; renews automatically\nDevice ID: " + DeviceAuth.get(this).deviceId() : "not paired"; }
        catch (Exception e) { pairing = "unreadable; reset and pair again"; }
        long budget = Math.max(0, p.getLong("uploadDeadline", 0) - System.currentTimeMillis()) / 60000;
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int percent = battery == null ? 0 : battery.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / Math.max(1, battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100));
        float temperature = battery == null ? 0 : battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f;
        status.setText("Recording: " + (DashcamService.recording() ? "● REC · writing video" : DashcamService.running() ? "waiting for video frames" : "stopped") +
            "\n" + p.getString("cameraStatus", "Not started") + "\nAuto-record: " + (p.getBoolean("enabled", false) ? "enabled" : "disabled") +
            "\nRecording elapsed: " + (DashcamService.running() && p.getLong("recordingStarted", 0) > 0 ? (System.currentTimeMillis() - p.getLong("recordingStarted", 0)) / 1000 + " sec" : "—") +
            "\nRear camera · 1080p / 30 fps · 8 Mbps · no microphone" +
            String.format(Locale.US, "\nLocal use: %.2f / %.0f GiB · free %.2f GiB\nClips: %d · pending %.2f GiB · protected %.2f GiB",
                store.usedBytes() / 1073741824.0, p.getLong("quota", DashcamPolicy.QUOTA_BYTES) / 1073741824.0, store.directory.getUsableSpace() / 1073741824.0, count, pending / 1073741824.0, saved / 1073741824.0) +
            "\nAwaiting verified cloud protection: " + protectionPending +
            String.format(Locale.US, "\nBattery: %d%% · %.1f°C", percent, temperature) +
            "\nOverwritten before upload: " + p.getLong("overwritten", 0) +
            "\nOldest pending: " + (oldest == Long.MAX_VALUE ? "none" : new Date(oldest).toString()) +
            "\nAPI: " + pairing + "\nWi-Fi: " + (UploadJob.wifi(this) == null ? "waiting for validated, unmetered Wi-Fi" : "eligible") +
            "\nBattery upload budget remaining: " + budget + " min (unlimited while powered)" +
            "\n" + p.getString("uploadStatus", "Waiting for finalized clips") + "\n");
    }
    private void enroll() {
        final String address = endpoint.getText().toString(), pairingCode = code.getText().toString();
        pair.setEnabled(false);
        io.execute(new Runnable() { public void run() {
            String message;
            try {
                android.net.Network network = UploadJob.wifi(DashcamActivity.this);
                if (network == null) throw new IllegalStateException("Connect to unmetered Wi-Fi first");
                DeviceAuth.get(DashcamActivity.this).enroll(network, address, pairingCode);
                getSharedPreferences("dashcam", 0).edit().putString("endpoint", DeviceAuth.validateEndpoint(address)).commit();
                UploadJob.schedule(DashcamActivity.this, true); message = "Phone paired";
            } catch (Exception e) { message = "Pairing failed: " + e.getClass().getSimpleName(); }
            final String result = message;
            main.post(new Runnable() { public void run() {
                if (isDestroyed()) return; pair.setEnabled(true); code.setText(""); Toast.makeText(DashcamActivity.this, result, Toast.LENGTH_LONG).show(); showStatus();
            }});
        }});
    }
    private void clipActions(final JSONObject clip, final Uri uri) {
        new AlertDialog.Builder(this).setTitle("Local clip")
            .setItems(new String[]{"Play video", "Share MP4", "Allow local overwrite", "Delete local copy"}, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    if (which < 2) {
                        Intent intent = which == 0 ? new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4") :
                            new Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, uri);
                        intent.setClipData(ClipData.newRawUri("Dashcam clip", uri)); intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        try { startActivity(Intent.createChooser(intent, which == 0 ? "Play clip" : "Share clip")); }
                        catch (ActivityNotFoundException e) { Toast.makeText(DashcamActivity.this, "No compatible app installed", Toast.LENGTH_LONG).show(); }
                        return;
                    }
                    final boolean remove = which == 3;
                    new AlertDialog.Builder(DashcamActivity.this).setMessage(remove ?
                        "Delete this local file? Any verified cloud copy remains. A clip that has not uploaded will be lost." :
                        "Allow loop recording to overwrite this local clip? Any protected S3 copy remains protected.")
                        .setPositiveButton(remove ? "Delete local file" : "Allow overwrite", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int choice) {
                                ClipStore store = ClipStore.get(DashcamActivity.this);
                                if (remove && !store.deleteLocal(clip.optString("id"))) Toast.makeText(DashcamActivity.this, "Clip is in use; try later", Toast.LENGTH_LONG).show();
                                else if (!remove) store.allowOverwrite(clip.optString("id"));
                                showStatus();
                            }
                        }).setNegativeButton("Cancel", null).show();
                }
            }).setNegativeButton("Close", null).show();
    }
    private void clips() {
        final java.util.List<JSONObject> clips = new ArrayList<JSONObject>();
        for (JSONObject c : ClipStore.get(this).clips()) if ("READY".equals(c.optString("state"))) clips.add(c);
        Collections.reverse(clips);
        if (clips.isEmpty()) { Toast.makeText(this, "No finalized clips yet", Toast.LENGTH_SHORT).show(); return; }
        String[] names = new String[clips.size()];
        for (int i = 0; i < names.length; i++) names[i] = new Date(clips.get(i).optLong("started")).toString() +
            (clips.get(i).optInt("protected") != 0 ? " · protected" : "") + (clips.get(i).optInt("verified") != 0 ? " · uploaded" : " · local");
        new AlertDialog.Builder(this).setTitle("Play or export a local clip").setItems(names, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int which) {
                Uri uri = Uri.parse("content://" + getPackageName() + ".clips/" + clips.get(which).optString("id"));
                clipActions(clips.get(which), uri);
            }
        }).setNegativeButton("Close", null).show();
    }
}
