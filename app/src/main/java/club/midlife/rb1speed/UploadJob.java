package club.midlife.rb1speed;

import android.app.job.*;
import android.content.*;
import android.net.*;
import android.os.*;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded persisted jobs upload through a selected Wi-Fi network, even with the screen off. */
public final class UploadJob extends JobService {
    private static final AtomicBoolean busy = new AtomicBoolean();
    private volatile boolean cancelled;
    private volatile HttpURLConnection uploading;
    private long jobDeadline, checkedAt;
    private boolean lastAllowed;
    static void schedule(Context context, boolean now) {
        JobScheduler scheduler = (JobScheduler)context.getSystemService(JOB_SCHEDULER_SERVICE);
        ComponentName component = new ComponentName(context, UploadJob.class);
        scheduler.schedule(new JobInfo.Builder(83, component).setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
            .setRequiresBatteryNotLow(true).setPersisted(true).setPeriodic(15 * 60 * 1000L).build());
        if (now || scheduler.getPendingJob(82) == null) scheduler.schedule(new JobInfo.Builder(82, component)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED).setRequiresBatteryNotLow(true).setPersisted(true)
            .setMinimumLatency(now ? 0 : 30000).setBackoffCriteria(60000, JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());
    }
    static Network wifi(Context context) {
        ConnectivityManager manager = (ConnectivityManager)context.getSystemService(CONNECTIVITY_SERVICE);
        for (Network network : manager.getAllNetworks()) if (eligible(manager.getNetworkCapabilities(network))) return network;
        return null;
    }
    private static boolean eligible(NetworkCapabilities c) {
        return c != null && c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) && !c.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
    static void powerChanged(Context context, boolean plugged) {
        SharedPreferences p = context.getSharedPreferences("dashcam", 0);
        if (p.contains("uploadWasPlugged") && p.getBoolean("uploadWasPlugged", false) == plugged &&
            (plugged || p.getLong("uploadDeadline", 0) != 0)) return;
        SharedPreferences.Editor edit = p.edit();
        if (!plugged && (p.getBoolean("uploadWasPlugged", false) || p.getLong("uploadDeadline", 0) == 0))
            edit.putLong("uploadDeadline", System.currentTimeMillis() + DashcamPolicy.BATTERY_BUDGET_MS);
        edit.putBoolean("uploadWasPlugged", plugged).commit();
    }
    private void status(String text) { getSharedPreferences("dashcam", 0).edit().putString("uploadStatus", text).apply(); }
    private boolean allowed(Network network) {
        if (cancelled || SystemClock.elapsedRealtime() >= jobDeadline) return false;
        long elapsed = SystemClock.elapsedRealtime();
        if (elapsed - checkedAt < 1000) return lastAllowed;
        checkedAt = elapsed;
        ConnectivityManager manager = (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        boolean plugged = battery != null && battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
        int level = battery == null ? 0 : battery.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
        int scale = battery == null ? 100 : battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        powerChanged(this, plugged);
        if (battery != null && battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) >= 450) return false;
        if (Build.VERSION.SDK_INT >= 29 && ((PowerManager)getSystemService(POWER_SERVICE)).getCurrentThermalStatus() >= PowerManager.THERMAL_STATUS_SEVERE) return false;
        lastAllowed = DashcamPolicy.uploadAllowed(eligible(manager.getNetworkCapabilities(network)), plugged, scale > 0 ? level * 100 / scale : 0,
            System.currentTimeMillis(), getSharedPreferences("dashcam", 0).getLong("uploadDeadline", 0));
        return lastAllowed;
    }
    @Override public boolean onStartJob(final JobParameters params) {
        if (!busy.compareAndSet(false, true)) return false;
        cancelled = false; checkedAt = 0; lastAllowed = false; jobDeadline = SystemClock.elapsedRealtime() + 7 * 60 * 1000L;
        new Thread(new Runnable() { public void run() {
            boolean retry = false;
            try { ClipAssembler.enqueue(getApplicationContext(), true); runUploads(); }
            catch (Exception e) {
                status(e instanceof DeviceAuth.ApiError && ((DeviceAuth.ApiError)e).status == 401
                    ? "Pairing expired or revoked; pair this phone again" : "Upload paused: " + e.getClass().getSimpleName()); retry = true;
            }
            finally { busy.set(false); jobFinished(params, retry && !cancelled); }
        }}, "dashcam-upload").start(); return true;
    }
    @Override public boolean onStopJob(JobParameters params) {
        cancelled = true; HttpURLConnection c = uploading; if (c != null) c.disconnect(); return true;
    }
    private void runUploads() throws Exception {
        Network network = wifi(this);
        if (network == null) { status("Waiting for unmetered Wi-Fi"); return; }
        DeviceAuth auth = DeviceAuth.get(this);
        if (!auth.paired()) { status("Pair the phone to enable cloud copies"); return; }
        ClipStore store = ClipStore.get(this);
        for (JSONObject clip : store.clips()) {
            if (!allowed(network)) { status("Paused: Wi-Fi, battery, temperature or time budget"); return; }
            String id = clip.optString("id"), path = "/v1/clips/" + id;
            if (!"READY".equals(clip.optString("state")) || !store.lease(id)) continue;
            try {
                if (clip.optInt("verified") != 0) {
                    if (clip.optInt("protected") != 0 && clip.optInt("remoteProtected") == 0) {
                        JSONObject protection = auth.api(network, path + "/protect", "POST", new JSONObject());
                        if (protection.optBoolean("verified")) store.remoteProtected(id); else store.unverified(id);
                    } continue;
                }
                File file = new File(clip.getString("path"));
                JSONArray hashes = clip.has("hashes") ? new JSONArray(clip.getString("hashes")) : hashes(file, network);
                store.hashes(id, hashes.toString());
                auth.api(network, "/v1/clips", "POST", new JSONObject().put("id", id).put("size", file.length())
                    .put("startedAt", clip.getLong("started")).put("protected", clip.optInt("protected") != 0).put("partHashes", hashes));
                JSONObject remote;
                try { remote = auth.api(network, path + "/upload", "POST", new JSONObject()); }
                catch (DeviceAuth.ApiError e) { if (e.status == 409) throw new IOException("Upload session reset; retrying later"); throw e; }
                if (!remote.optBoolean("verified")) {
                    JSONArray existing = remote.getJSONArray("parts");
                    for (int i = 0; i < hashes.length(); i++) {
                        boolean exists = false;
                        for (int j = 0; j < existing.length(); j++) {
                            JSONObject part = existing.getJSONObject(j);
                            if (part.optInt("number") == i + 1 && hashes.getString(i).equals(part.optString("checksum"))) exists = true;
                        }
                        if (exists) continue;
                        if (!allowed(network)) throw new IOException("Upload conditions changed; completed parts retained");
                        JSONObject signed = auth.api(network, path + "/parts/sign", "POST", new JSONObject().put("partNumber", i + 1));
                        status("Uploading clip " + id.substring(0, 8) + " · part " + (i + 1) + "/" + hashes.length());
                        upload(network, file, (long)i * DashcamPolicy.PART_BYTES, signed);
                    }
                    JSONObject result = auth.api(network, path + "/complete", "POST", new JSONObject());
                    if (!result.optBoolean("verified")) throw new IOException("Remote copy not verified");
                }
                // Re-read protection: an incident may be saved while this file uploads.
                JSONObject latest = store.clip(id);
                if (latest != null && latest.optInt("protected") != 0) {
                    JSONObject protection = auth.api(network, path + "/protect", "POST", new JSONObject());
                    if (!protection.optBoolean("verified")) { store.unverified(id); continue; }
                    store.remoteProtected(id);
                }
                store.verified(id); status("Last cloud copy verified: " + id.substring(0, 8));
            } finally { store.release(id); }
        }
    }
    private JSONArray hashes(File file, Network network) throws Exception {
        JSONArray hashes = new JSONArray(); byte[] buffer = new byte[65536];
        try (InputStream input = new FileInputStream(file)) {
            long left = file.length();
            while (left > 0) {
                MessageDigest digest = MessageDigest.getInstance("SHA-256"); long part = Math.min(left, DashcamPolicy.PART_BYTES);
                while (part > 0) {
                    if (!allowed(network)) throw new IOException("Hashing paused");
                    int n = input.read(buffer, 0, (int)Math.min(part, buffer.length)); if (n < 0) throw new EOFException();
                    digest.update(buffer, 0, n); part -= n; left -= n;
                }
                hashes.put(Base64.encodeToString(digest.digest(), Base64.NO_WRAP));
            }
        } return hashes;
    }
    private void upload(Network network, File file, long offset, JSONObject signed) throws Exception {
        URL url = new URL(signed.getString("url"));
        if (!"https".equals(url.getProtocol()) || !url.getHost().endsWith(".amazonaws.com")) throw new IOException("Unexpected storage endpoint");
        HttpURLConnection connection = (HttpURLConnection)network.openConnection(url); uploading = connection;
        connection.setConnectTimeout(15000); connection.setReadTimeout(20000); connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("PUT"); connection.setDoOutput(true);
        connection.setRequestProperty("x-amz-checksum-sha256", signed.getString("checksum"));
        long remaining = signed.getLong("length"); connection.setFixedLengthStreamingMode(remaining);
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            input.seek(offset);
            try (OutputStream output = connection.getOutputStream()) {
                byte[] buffer = new byte[65536];
                while (remaining > 0) {
                    if (!allowed(network)) throw new IOException("Upload paused; completed parts retained");
                    int n = input.read(buffer, 0, (int)Math.min(buffer.length, remaining)); if (n < 0) throw new EOFException();
                    output.write(buffer, 0, n); remaining -= n;
                }
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IOException("Storage upload returned " + status);
        } finally { uploading = null; connection.disconnect(); }
    }
}
