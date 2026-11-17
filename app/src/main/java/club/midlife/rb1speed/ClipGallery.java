package club.midlife.rb1speed;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import java.io.*;
import org.json.JSONObject;

/** Publishes finalized copies, keeping upload originals independent of gallery edits/deletions. */
final class ClipGallery {
    static final String FOLDER = Environment.DIRECTORY_MOVIES + "/RB1 Speed/";
    private static final Uri VIDEOS = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
    static boolean supported() { return Build.VERSION.SDK_INT >= 29; }
    private static String selection() { return MediaStore.MediaColumns.RELATIVE_PATH + "=? AND " + MediaStore.MediaColumns.OWNER_PACKAGE_NAME + "=?"; }
    private static String[] args(Context context) { return new String[]{FOLDER, context.getPackageName()}; }
    static boolean needsCopy(Context context, JSONObject clip) throws Exception {
        if (!supported()) return false;
        if (clip.isNull("gallery")) return true;
        Uri uri = Uri.parse(clip.getString("gallery"));
        try (Cursor row = context.getContentResolver().query(MediaStore.setIncludePending(uri),
            new String[]{MediaStore.MediaColumns.IS_PENDING}, selection(), args(context), null)) {
            if (row == null) throw new IOException("Cannot inspect gallery copy");
            return row.moveToFirst() ? row.getInt(0) != 0 : clip.optInt("galleryReady") == 0;
        }
    }
    static void publish(Context context, ClipStore store, JSONObject clip) throws Exception {
        if (!supported()) return;
        String id = clip.getString("id"); File source = new File(clip.getString("path"));
        if (!source.isFile()) return;
        ContentResolver resolver = context.getContentResolver();
        Uri uri = clip.isNull("gallery") ? null : Uri.parse(clip.getString("gallery"));
        boolean pending = true;
        if (uri != null) {
            try (Cursor row = resolver.query(MediaStore.setIncludePending(uri), new String[]{MediaStore.MediaColumns.IS_PENDING}, selection(), args(context), null)) {
                // Respect a video the owner deleted through Photos; do not silently recreate it.
                if (row == null) throw new IOException("Cannot inspect gallery copy");
                if (!row.moveToFirst()) {
                    if (clip.optInt("galleryReady") != 0) return;
                    uri = null; // An abandoned pending row can expire; retry from the original.
                } else if (row.getInt(0) == 0) { store.galleryReady(id); return; }
            }
        }
        if (uri == null) {
            // Recover the small crash window between media insertion and saving its URI.
            try (Cursor row = resolver.query(MediaStore.setIncludePending(VIDEOS), new String[]{MediaStore.MediaColumns._ID, MediaStore.MediaColumns.IS_PENDING},
                selection() + " AND " + MediaStore.MediaColumns.DISPLAY_NAME + "=?",
                new String[]{FOLDER, context.getPackageName(), "RB1-" + id + ".mp4"}, null)) {
                if (row == null) throw new IOException("Cannot inspect gallery library");
                if (row.moveToFirst()) { uri = ContentUris.withAppendedId(VIDEOS, row.getLong(0)); pending = row.getInt(1) != 0; }
            }
            if (uri == null) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, "RB1-" + id + ".mp4");
                values.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, FOLDER);
                values.put(MediaStore.MediaColumns.DATE_ADDED, clip.getLong("started") / 1000);
                values.put(MediaStore.Video.Media.DATE_TAKEN, clip.getLong("started"));
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);
                uri = resolver.insert(VIDEOS, values);
                if (uri == null) throw new IOException("Cannot create gallery copy");
            }
            store.gallery(id, uri.toString());
            if (!pending) { store.galleryReady(id); return; }
        }
        try (InputStream input = new FileInputStream(source); OutputStream output = resolver.openOutputStream(uri, "wt")) {
            if (output == null) throw new IOException("Cannot write gallery copy");
            byte[] buffer = new byte[65536]; long copied = 0; int size;
            while ((size = input.read(buffer)) >= 0) { output.write(buffer, 0, size); copied += size; }
            if (copied != source.length()) throw new IOException("Incomplete gallery copy");
        }
        try (ParcelFileDescriptor descriptor = resolver.openFileDescriptor(uri, "r")) {
            if (descriptor == null || descriptor.getStatSize() != source.length()) throw new IOException("Gallery size mismatch");
        }
        ContentValues visible = new ContentValues(); visible.put(MediaStore.MediaColumns.IS_PENDING, 0);
        if (resolver.update(uri, visible, selection(), args(context)) != 1) throw new IOException("Cannot publish gallery copy");
        store.galleryReady(id);
    }
    static long usedBytes(Context context) {
        if (!supported()) return 0;
        try (Cursor rows = context.getContentResolver().query(MediaStore.setIncludePending(VIDEOS), new String[]{MediaStore.MediaColumns.SIZE}, selection(), args(context), null)) {
            if (rows == null) throw new IllegalStateException("Gallery unavailable");
            long bytes = 0; while (rows.moveToNext()) bytes += rows.getLong(0); return bytes;
        } catch (RuntimeException e) {
            // Unknown gallery usage must not be treated as empty storage.
            context.getSharedPreferences("dashcam", 0).edit().putString("galleryStatus", "Gallery storage unavailable; retry on next app open").apply();
            return Long.MAX_VALUE / 4;
        }
    }
    static boolean delete(Context context, String value) {
        if (value == null || value.isEmpty() || !supported()) return true;
        try {
            Uri uri = Uri.parse(value);
            if (!value.startsWith(VIDEOS.toString() + "/")) return false;
            String id = Long.toString(ContentUris.parseId(uri));
            context.getContentResolver().delete(VIDEOS, selection() + " AND " + MediaStore.MediaColumns._ID + "=?",
                new String[]{FOLDER, context.getPackageName(), id});
            return true;
        }
        catch (RuntimeException e) { return false; }
    }
}
