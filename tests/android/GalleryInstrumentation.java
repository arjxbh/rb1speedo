package club.midlife.rb1speed;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import org.json.JSONObject;

/** Real MediaStore checks in a separate database/directory; does not require a camera. */
public final class GalleryInstrumentation extends Instrumentation {
    private static void check(boolean value, String why) { if (!value) throw new AssertionError(why); }
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try { exercise(); result.putString("stream", "Gallery integration: all checks passed\n"); finish(Activity.RESULT_OK, result); }
        catch (Throwable e) { result.putString("stream", "Gallery integration FAILED: " + android.util.Log.getStackTraceString(e)); finish(Activity.RESULT_CANCELED, result); }
    }
    private void exercise() throws Exception {
        final Context base = getTargetContext();
        Context context = new ContextWrapper(base) {
            @Override public android.content.SharedPreferences getSharedPreferences(String name, int mode) {
                return base.getSharedPreferences("gallery-test-" + name, mode);
            }
        };
        String database = "gallery-test-" + UUID.randomUUID() + ".db";
        File directory = new File(context.getFilesDir(), "gallery-test-" + UUID.randomUUID());
        check(directory.mkdirs(), "test directory");
        String id = UUID.randomUUID().toString(); File video = new File(directory, id + ".mp4");
        video(video);
        // Seed the released v1 schema and a real pre-existing completed clip.
        try (SQLiteDatabase old = context.openOrCreateDatabase(database, 0, null)) {
            old.execSQL("CREATE TABLE clips (id TEXT PRIMARY KEY, started INTEGER, state TEXT, path TEXT, bytes INTEGER DEFAULT 0, protected INTEGER DEFAULT 0, verified INTEGER DEFAULT 0, remoteProtected INTEGER DEFAULT 0, hashes TEXT)");
            old.execSQL("CREATE TABLE fragments (id TEXT PRIMARY KEY, clip TEXT, path TEXT, duration INTEGER DEFAULT 0, ready INTEGER DEFAULT 0)");
            ContentValues row = new ContentValues(); row.put("id", id); row.put("started", System.currentTimeMillis());
            row.put("state", "READY"); row.put("path", video.getAbsolutePath()); row.put("bytes", video.length());
            old.insertOrThrow("clips", null, row); old.setVersion(1);
        }
        ClipStore store = new ClipStore(context, database, directory);
        Uri uri = null;
        try {
            check(store.getReadableDatabase().getVersion() == 2, "upgrade database to v2");
            check(store.clip(id).isNull("gallery"), "migration preserves old clip with empty gallery link");
            long before = store.usedBytes();
            ClipGallery.publish(context, store, store.clip(id));
            uri = Uri.parse(store.clip(id).getString("gallery"));
            verify(context, uri, video.length());
            check(store.usedBytes() == before + video.length(), "quota includes private original plus gallery copy");
            check(!ClipGallery.needsCopy(context, store.clip(id)), "published clip needs no extra copy");
            ClipGallery.publish(context, store, store.clip(id));
            check(uri.toString().equals(store.clip(id).getString("gallery")), "retry reuses published URI");
            check(count(context, id) == 1, "retry must not create duplicates");
            context.getContentResolver().delete(uri, null, null);
            check(ClipGallery.delete(context, uri.toString()), "already-deleted Photos copy must not block loop cleanup");
            ClipGallery.publish(context, store, store.clip(id));
            check(count(context, id) == 0 && video.isFile(), "Photos deletion is respected without losing upload original");
            check(!ClipGallery.needsCopy(context, store.clip(id)), "deleted published copies are not recreated");
            store.gallery(id, null);
            ContentValues pending = new ContentValues(); pending.put(MediaStore.MediaColumns.DISPLAY_NAME, "RB1-" + id + ".mp4");
            pending.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4"); pending.put(MediaStore.MediaColumns.RELATIVE_PATH, ClipGallery.FOLDER);
            pending.put(MediaStore.MediaColumns.IS_PENDING, 1);
            uri = context.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, pending);
            store.gallery(id, uri.toString());
            context.getContentResolver().delete(uri, null, null);
            check(ClipGallery.needsCopy(context, store.clip(id)), "missing pending copy remains retryable");
            ClipGallery.publish(context, store, store.clip(id));
            verify(context, Uri.parse(store.clip(id).getString("gallery")), video.length());
            context.getContentResolver().delete(Uri.parse(store.clip(id).getString("gallery")), null, null);
            store.gallery(id, null);
            uri = context.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, pending);
            try (OutputStream out = context.getContentResolver().openOutputStream(uri, "w")) { out.write(new byte[37]); }
            // Simulate death after insert/partial copy but before recording the media URI.
            ClipGallery.publish(context, store, store.clip(id));
            check(uri.toString().equals(store.clip(id).getString("gallery")), "adopt interrupted row instead of duplicating");
            verify(context, uri, video.length()); check(count(context, id) == 1, "one complete gallery copy after recovery");
            store.lease(id); store.makeSpace(1L << 40);
            check(video.isFile(), "loop must not remove a leased original or its gallery copy");
            store.release(id); store.protectIncident(); store.makeSpace(1L << 40);
            check(video.isFile() && count(context, id) == 1, "protected gallery clip survives loop pressure");
            store.allowOverwrite(id); store.makeSpace(1L << 40);
            check(!video.exists() && count(context, id) == 0 && store.clip(id) == null, "loop deletes both copies and metadata");
            String second = store.beginClip(System.currentTimeMillis()); File secondFile = new File(directory, second + ".mp4");
            video(secondFile); store.ready(second, secondFile);
            ClipGallery.publish(context, store, store.clip(second));
            check(store.deleteLocal(second) && !secondFile.exists() && count(context, second) == 0, "manual delete removes both copies");
        } finally {
            for (JSONObject c : store.clips()) { ClipGallery.delete(context, c.isNull("gallery") ? null : c.optString("gallery")); new File(c.optString("path")).delete(); }
            if (uri != null) ClipGallery.delete(context, uri.toString());
            store.close(); context.deleteDatabase(database);
            File[] files = directory.listFiles(); if (files != null) for (File file : files) file.delete(); directory.delete();
        }
    }
    private int count(Context context, String id) {
        try (Cursor rows = context.getContentResolver().query(MediaStore.setIncludePending(MediaStore.Video.Media.EXTERNAL_CONTENT_URI),
            new String[]{MediaStore.MediaColumns._ID}, MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " + MediaStore.MediaColumns.RELATIVE_PATH + "=?",
            new String[]{"RB1-" + id + ".mp4", ClipGallery.FOLDER}, null)) { return rows.getCount(); }
    }
    private void verify(Context context, Uri uri, long bytes) throws Exception {
        try (Cursor row = context.getContentResolver().query(uri, new String[]{MediaStore.MediaColumns.IS_PENDING, MediaStore.MediaColumns.RELATIVE_PATH}, null, null, null)) {
            check(row.moveToFirst() && row.getInt(0) == 0 && ClipGallery.FOLDER.equals(row.getString(1)), "complete video is visible in Movies/RB1 Speed");
        }
        try (ParcelFileDescriptor descriptor = context.getContentResolver().openFileDescriptor(uri, "r")) {
            check(descriptor.getStatSize() == bytes, "gallery bytes match original");
            MediaExtractor input = new MediaExtractor();
            try {
                input.setDataSource(descriptor.getFileDescriptor()); check(input.getTrackCount() == 1, "shared video has one playable track");
                check("video/avc".equals(input.getTrackFormat(0).getString(MediaFormat.KEY_MIME)), "shared video is H.264 MP4");
                input.selectTrack(0); check(input.getSampleTime() >= 0, "shared video contains encoded frames");
            } finally { input.release(); }
        }
    }
    private void video(File file) throws Exception {
        MediaCodec codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        MediaMuxer mux = new MediaMuxer(file.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        boolean muxStarted = false; int track = -1, frame = 0; boolean done = false;
        try {
            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 64, 64);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 64000); format.setInteger(MediaFormat.KEY_FRAME_RATE, 10); format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); codec.start();
            long deadline = SystemClock.elapsedRealtime() + 15000;
            while (!done && SystemClock.elapsedRealtime() < deadline) {
                if (frame <= 6) {
                    int slot = codec.dequeueInputBuffer(10000);
                    if (slot >= 0) {
                        ByteBuffer data = codec.getInputBuffer(slot); data.clear();
                        if (frame < 6) for (int i = 0; i < 64 * 64 * 3 / 2; i++) data.put((byte)128);
                        codec.queueInputBuffer(slot, 0, frame < 6 ? 64 * 64 * 3 / 2 : 0, frame * 100000L, frame == 6 ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0); frame++;
                    }
                }
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo(); int slot = codec.dequeueOutputBuffer(info, 10000);
                if (slot == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { track = mux.addTrack(codec.getOutputFormat()); mux.start(); muxStarted = true; }
                else if (slot >= 0) {
                    if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) mux.writeSampleData(track, codec.getOutputBuffer(slot), info);
                    done = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0; codec.releaseOutputBuffer(slot, false);
                }
            }
            check(done && muxStarted, "generate test video"); mux.stop();
        } finally { codec.stop(); codec.release(); mux.release(); }
    }
}
