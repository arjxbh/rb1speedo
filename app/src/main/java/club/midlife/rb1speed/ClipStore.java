package club.midlife.rb1speed;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.io.File;
import java.util.*;

/** Persistent local queue. Files are never placed in Android's disposable cache. */
final class ClipStore extends SQLiteOpenHelper {
    private static ClipStore instance;
    static synchronized ClipStore get(Context context) {
        if (instance == null) instance = new ClipStore(context.getApplicationContext());
        return instance;
    }
    final File directory;
    private final Context context;
    private boolean recovered;
    private final Set<String> leased = new HashSet<String>();
    private ClipStore(Context context) {
        super(context, "dashcam.db", null, 1); this.context = context;
        directory = new File(context.getFilesDir(), "dashcam");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("Cannot create recording directory");
    }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE clips (id TEXT PRIMARY KEY, started INTEGER, state TEXT, path TEXT, bytes INTEGER DEFAULT 0, protected INTEGER DEFAULT 0, verified INTEGER DEFAULT 0, remoteProtected INTEGER DEFAULT 0, hashes TEXT)");
        db.execSQL("CREATE TABLE fragments (id TEXT PRIMARY KEY, clip TEXT, path TEXT, duration INTEGER DEFAULT 0, ready INTEGER DEFAULT 0)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int old, int next) { throw new IllegalStateException("Unsupported dashcam database upgrade"); }
    synchronized String beginClip(long started) {
        String id = UUID.randomUUID().toString(); ContentValues v = new ContentValues();
        v.put("id", id); v.put("started", started); v.put("state", "RECORDING");
        if (context.getSharedPreferences("dashcam", 0).getBoolean("protectNext", false)) {
            v.put("protected", 1); context.getSharedPreferences("dashcam", 0).edit().putBoolean("protectNext", false).commit();
        }
        getWritableDatabase().insertOrThrow("clips", null, v); return id;
    }
    synchronized String beginFragment(String clip) {
        String id = UUID.randomUUID().toString(); ContentValues v = new ContentValues();
        v.put("id", id); v.put("clip", clip); v.put("path", new File(directory, id + ".open").getAbsolutePath());
        getWritableDatabase().insertOrThrow("fragments", null, v); return id;
    }
    synchronized File fragmentFile(String id) { return new File(directory, id + ".open"); }
    synchronized void finishFragment(String id, long duration) {
        File source = fragmentFile(id), destination = new File(directory, id + ".fragment.mp4");
        if (!source.renameTo(destination)) throw new IllegalStateException("Cannot finalize recording fragment");
        ContentValues v = new ContentValues(); v.put("path", destination.getAbsolutePath()); v.put("duration", duration); v.put("ready", 1);
        getWritableDatabase().update("fragments", v, "id=?", new String[]{id});
    }
    synchronized void seal(String id) { state(id, "FINALIZING"); }
    synchronized void state(String id, String state) {
        ContentValues v = new ContentValues(); v.put("state", state); getWritableDatabase().update("clips", v, "id=?", new String[]{id});
    }
    synchronized void ready(String id, File file) {
        ContentValues v = new ContentValues(); v.put("path", file.getAbsolutePath()); v.put("bytes", file.length()); v.put("state", "READY");
        getWritableDatabase().update("clips", v, "id=?", new String[]{id});
    }
    synchronized void hashes(String id, String hashes) {
        ContentValues v = new ContentValues(); v.put("hashes", hashes); getWritableDatabase().update("clips", v, "id=?", new String[]{id});
    }
    synchronized void remoteProtected(String id) {
        ContentValues v = new ContentValues(); v.put("remoteProtected", 1); getWritableDatabase().update("clips", v, "id=?", new String[]{id});
    }
    synchronized void unverified(String id) {
        ContentValues v = new ContentValues(); v.put("verified", 0); v.put("remoteProtected", 0);
        getWritableDatabase().update("clips", v, "id=?", new String[]{id});
    }
    synchronized void verified(String id) {
        ContentValues v = new ContentValues(); v.put("verified", 1); getWritableDatabase().update("clips", v, "id=?", new String[]{id});
    }
    synchronized List<JSONObject> clips() { return rows("SELECT * FROM clips ORDER BY protected DESC, started ASC", null); }
    synchronized JSONObject clip(String id) {
        List<JSONObject> rows = rows("SELECT * FROM clips WHERE id=?", new String[]{id}); return rows.isEmpty() ? null : rows.get(0);
    }
    synchronized List<JSONObject> fragments(String clip) {
        return rows("SELECT * FROM fragments WHERE clip=? ORDER BY rowid", new String[]{clip});
    }
    private List<JSONObject> rows(String query, String[] args) {
        List<JSONObject> out = new ArrayList<JSONObject>();
        try (Cursor c = getReadableDatabase().rawQuery(query, args)) {
            while (c.moveToNext()) {
                JSONObject row = new JSONObject();
                for (int i = 0; i < c.getColumnCount(); i++) try {
                    if (!c.isNull(i)) row.put(c.getColumnName(i), c.getType(i) == Cursor.FIELD_TYPE_INTEGER ? (Object)c.getLong(i) : c.getString(i));
                } catch (JSONException impossible) { throw new IllegalStateException(impossible); }
                out.add(row);
            }
        } return out;
    }
    synchronized boolean lease(String id) { return leased.add(id); }
    synchronized void release(String id) { leased.remove(id); }
    synchronized void deleteFragments(String id) {
        for (JSONObject f : fragments(id)) new File(f.optString("path")).delete();
        getWritableDatabase().delete("fragments", "clip=?", new String[]{id});
    }
    synchronized void recover() {
        if (recovered) return;
        recovered = true;
        for (JSONObject c : clips()) {
            String id = c.optString("id");
            if ("RECORDING".equals(c.optString("state"))) seal(id);
            // A crash may occur between atomic fragment rename and its database update.
            for (JSONObject f : fragments(id)) if (f.optInt("ready") == 0) {
                File finalized = new File(directory, f.optString("id") + ".fragment.mp4");
                if (finalized.isFile()) {
                    ContentValues v = new ContentValues(); v.put("path", finalized.getAbsolutePath()); v.put("ready", 1);
                    getWritableDatabase().update("fragments", v, "id=?", new String[]{f.optString("id")});
                } else {
                    new File(f.optString("path")).delete();
                    getWritableDatabase().delete("fragments", "id=?", new String[]{f.optString("id")});
                }
            }
            if ("READY".equals(c.optString("state"))) deleteFragments(id);
        }
        // Incomplete remux outputs can be recreated from their retained source fragments.
        File[] files = directory.listFiles();
        if (files != null) for (File file : files) if (file.getName().endsWith(".assembling")) file.delete();
    }
    synchronized boolean makeSpace() {
        long quota = context.getSharedPreferences("dashcam", 0).getLong("quota", DashcamPolicy.QUOTA_BYTES);
        long used = usedBytes();
        List<JSONObject> candidates = rows("SELECT * FROM clips WHERE state='READY' AND protected=0 ORDER BY verified DESC, started ASC", null);
        for (JSONObject c : candidates) {
            if (used < quota && directory.getUsableSpace() > DashcamPolicy.RESERVE_BYTES) return true;
            String id = c.optString("id"); if (leased.contains(id)) continue;
            File file = new File(c.optString("path"));
            if (file.exists() && !file.delete()) continue;
            used -= c.optLong("bytes");
            if (c.optInt("verified") == 0) {
                SharedPreferences p = context.getSharedPreferences("dashcam", 0);
                p.edit().putLong("overwritten", p.getLong("overwritten", 0) + 1).commit();
            }
            getWritableDatabase().delete("clips", "id=?", new String[]{id}); deleteFragments(id);
        }
        return used < quota && directory.getUsableSpace() > DashcamPolicy.RESERVE_BYTES;
    }
    synchronized long usedBytes() {
        long used = 0; File[] files = directory.listFiles();
        if (files != null) for (File f : files) used += f.length(); return used;
    }
    synchronized void allowOverwrite(String id) {
        ContentValues values = new ContentValues(); values.put("protected", 0);
        getWritableDatabase().update("clips", values, "id=? AND state='READY'", new String[]{id});
    }
    synchronized boolean deleteLocal(String id) {
        JSONObject c = clip(id);
        if (c == null || leased.contains(id) || !"READY".equals(c.optString("state"))) return false;
        File file = new File(c.optString("path"));
        if (file.exists() && !file.delete()) return false;
        getWritableDatabase().delete("clips", "id=?", new String[]{id}); deleteFragments(id); return true;
    }
    synchronized void protectIncident() {
        List<JSONObject> recent = rows("SELECT * FROM clips ORDER BY started DESC LIMIT 2", null);
        for (JSONObject c : recent) {
            ContentValues v = new ContentValues(); v.put("protected", 1);
            getWritableDatabase().update("clips", v, "id=?", new String[]{c.optString("id")});
        }
        context.getSharedPreferences("dashcam", 0).edit().putBoolean("protectNext", true).commit();
    }
}
