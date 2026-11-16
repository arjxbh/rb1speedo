package club.midlife.rb1speed;

import android.content.*;
import android.net.Network;
import android.security.keystore.*;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Refresh credentials are encrypted at rest; no AWS credentials are present on the phone. */
final class DeviceAuth {
    private final SharedPreferences prefs;
    private volatile JSONObject credentials;
    private String access;
    private long accessExpires;
    private static DeviceAuth instance;
    static synchronized DeviceAuth get(Context context) throws Exception {
        if (instance == null) instance = new DeviceAuth(context.getApplicationContext());
        return instance;
    }
    static void forget(Context context) {
        DeviceAuth auth;
        synchronized (DeviceAuth.class) { auth = instance; }
        if (auth != null) synchronized (auth) {
            auth.credentials = null; auth.access = null;
            context.getSharedPreferences("dashcam-auth", 0).edit().clear().commit();
        } else context.getSharedPreferences("dashcam-auth", 0).edit().clear().commit();
    }
    String deviceId() { JSONObject current = credentials; return current == null ? "none" : current.optString("deviceId"); }
    private DeviceAuth(Context context) throws Exception {
        prefs = context.getSharedPreferences("dashcam-auth", 0);
        String saved = prefs.getString("encrypted", null);
        if (saved != null) {
            String[] parts = saved.split(":");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
            credentials = new JSONObject(new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8));
        }
    }
    private SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if (!ks.containsAlias("rb1-device-auth")) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder("rb1-device-auth", KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false).build()); generator.generateKey();
        }
        return (SecretKey)ks.getKey("rb1-device-auth", null);
    }
    private void save() throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] encrypted = cipher.doFinal(credentials.toString().getBytes(StandardCharsets.UTF_8));
        if (!prefs.edit().putString("encrypted", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit()) throw new IOException("Cannot persist device credentials");
    }
    static String validateEndpoint(String value) throws Exception {
        URI uri = new URI(value.trim());
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null ||
            uri.getFragment() != null || (uri.getPort() != -1 && uri.getPort() != 443) ||
            !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) throw new IllegalArgumentException("Use an HTTPS API hostname without a path");
        return "https://" + uri.getHost();
    }
    boolean paired() { return credentials != null; }
    synchronized void enroll(Network network, String endpoint, String code) throws Exception {
        endpoint = validateEndpoint(endpoint);
        JSONObject result = request(network, endpoint + "/v1/enroll", "POST", new JSONObject().put("code", code.trim()), null);
        credentials = new JSONObject().put("endpoint", endpoint).put("deviceId", result.getString("deviceId"))
            .put("refresh", result.getString("refreshToken"));
        save(); access = result.getString("accessToken"); accessExpires = result.getLong("expiresAt");
    }
    private static String random() { byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes); return Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING); }
    private String access(Network network) throws Exception {
        if (!paired()) throw new IOException("Pair the phone with the API first");
        if (access != null && accessExpires > System.currentTimeMillis() / 1000 + 60) return access;
        if (!credentials.has("pending")) credentials.put("pending", random());
        // Retry the durable write even if a previous attempt failed with the in-memory pending value set.
        save();
        JSONObject result;
        try { result = renew(network); }
        catch (ApiError e) {
            if (e.status != 401) throw e;
            // A response may have been lost after the previous rotation succeeded days ago.
            credentials.put("refresh", credentials.getString("pending")).put("pending", random()); save();
            result = renew(network);
        }
        credentials.put("refresh", credentials.getString("pending")); credentials.remove("pending"); save();
        access = result.getString("accessToken"); accessExpires = result.getLong("expiresAt"); return access;
    }
    private JSONObject renew(Network network) throws Exception {
        return request(network, credentials.getString("endpoint") + "/v1/token", "POST", new JSONObject()
            .put("deviceId", credentials.getString("deviceId")).put("refreshToken", credentials.getString("refresh"))
            .put("nextRefreshToken", credentials.getString("pending")), null);
    }
    synchronized JSONObject api(Network network, String path, String method, JSONObject body) throws Exception {
        if (!paired()) throw new IOException("Pair this phone first");
        try { return request(network, credentials.getString("endpoint") + path, method, body, access(network)); }
        catch (ApiError e) {
            if (e.status != 401) throw e;
            access = null;
            return request(network, credentials.getString("endpoint") + path, method, body, access(network));
        }
    }
    static final class ApiError extends IOException {
        final int status;
        ApiError(int status, String text) { super(text); this.status = status; }
    }
    static JSONObject request(Network network, String address, String method, JSONObject body, String bearer) throws Exception {
        URL url = new URL(address);
        if (!"https".equals(url.getProtocol())) throw new IOException("HTTPS is required");
        HttpURLConnection connection = (HttpURLConnection)network.openConnection(url);
        connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(15000); connection.setReadTimeout(20000);
        connection.setRequestMethod(method);
        if (bearer != null) connection.setRequestProperty("Authorization", "Bearer " + bearer);
        try {
            if (!"GET".equals(method)) {
                byte[] bytes = (body == null ? "{}" : body.toString()).getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true); connection.setRequestProperty("Content-Type", "application/json");
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (stream != null) try (InputStream input = stream) {
                byte[] buffer = new byte[4096]; int n;
                while ((n = input.read(buffer)) >= 0) { bytes.write(buffer, 0, n); if (bytes.size() > 262144) throw new IOException("API response too large"); }
            }
            JSONObject result = bytes.size() == 0 ? new JSONObject() : new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            if (status < 200 || status >= 300) throw new ApiError(status, "API " + status + ": " + result.optString("error", "request failed"));
            return result;
        } finally { connection.disconnect(); }
    }
}
