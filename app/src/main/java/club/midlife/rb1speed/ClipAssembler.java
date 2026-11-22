package club.midlife.rb1speed;

import android.content.Context;
import android.media.*;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import org.json.JSONObject;

/** Copies encoded samples; no decode/re-encode, network, or UI-thread disk work. */
final class ClipAssembler {
    private static final ExecutorService work = Executors.newSingleThreadExecutor();
    static void enqueue(Context caller, final boolean recover) {
        final Context context = caller.getApplicationContext();
        work.execute(new Runnable() { public void run() {
            ClipStore store = ClipStore.get(context);
            if (recover) store.recover();
            for (JSONObject c : store.clips()) if ("FINALIZING".equals(c.optString("state"))) {
                String id = c.optString("id"); if (!store.lease(id)) continue;
                try { assemble(store, id); }
                catch (Exception e) { DashcamService.report(context, "Clip assembly paused: " + e.getClass().getSimpleName()); }
                finally { store.release(id); }
            }
            if (ClipGallery.supported()) {
                boolean complete = true;
                for (JSONObject c : store.clips()) if ("READY".equals(c.optString("state"))) {
                    String id = c.optString("id"); if (!store.lease(id)) { complete = false; continue; }
                    try {
                        if (ClipGallery.needsCopy(context, c) && !store.makeSpace(c.optLong("bytes")))
                            throw new java.io.IOException("Gallery copy waiting for loop space or free-space reserve");
                        ClipGallery.publish(context, store, c);
                    }
                    catch (Exception e) {
                        complete = false;
                        String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                        context.getSharedPreferences("dashcam", 0).edit().putString("galleryStatus", "Gallery copy waiting: " + reason.substring(0, Math.min(200, reason.length())) + ". Originals remain available in Camera & clips.").apply();
                    } finally { store.release(id); }
                }
                if (complete) context.getSharedPreferences("dashcam", 0).edit().putString("galleryStatus", "Completed clips: Movies/RB1 Speed · Google Photos → Collections → On this device").apply();
            }
            UploadJob.schedule(context, false);
        }});
    }
    private static void assemble(ClipStore store, String id) throws Exception {
        File stage = new File(store.directory, id + ".assembling"), target = new File(store.directory, id + ".mp4");
        MediaMuxer mux = null; long offset = 0; int videoTrack = -1, audioTrack = -1;
        ByteBuffer buffer = ByteBuffer.allocate(4 * 1024 * 1024);
        try {
            for (JSONObject f : store.fragments(id)) {
                if (f.optInt("ready") == 0) continue;
                MediaExtractor input = new MediaExtractor();
                try {
                    input.setDataSource(f.getString("path"));
                    int video = findTrack(input, "video/"), audio = findTrack(input, "audio/");
                    if (video < 0) throw new IllegalStateException("Fragment has no video");
                    MediaFormat format = input.getTrackFormat(video);
                    input.selectTrack(video);
                    long first = input.getSampleTime(), last = first;
                    if (first < 0) throw new IllegalStateException("Fragment has no video samples");
                    if (mux == null) {
                        mux = new MediaMuxer(stage.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                        if (format.containsKey(MediaFormat.KEY_ROTATION)) mux.setOrientationHint(format.getInteger(MediaFormat.KEY_ROTATION));
                        videoTrack = mux.addTrack(format);
                        if (audio >= 0) audioTrack = mux.addTrack(input.getTrackFormat(audio));
                        mux.start();
                    }
                    if (audio >= 0) {
                        if (audioTrack < 0) throw new IllegalStateException("Unexpected audio track");
                        input.selectTrack(audio);
                        input.seekTo(first, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                    }
                    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                    while (true) {
                        int source = input.getSampleTrackIndex();
                        if (source < 0) break;
                        long pts = input.getSampleTime();
                        buffer.clear();
                        int size = input.readSampleData(buffer, 0);
                        if (size < 0) break;
                        if (size >= buffer.capacity()) throw new IllegalStateException("Encoded frame too large");
                        if (source == video) last = Math.max(last, pts);
                        if (pts >= first) {
                            info.set(0, size, offset + pts - first, input.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC);
                            mux.writeSampleData(source == video ? videoTrack : audioTrack, buffer, info);
                        }
                        input.advance();
                    }
                    offset += Math.max(f.optLong("duration"), last - first + 33333);
                } finally { input.release(); }
            }
            if (mux == null) { store.state(id, "EMPTY"); return; }
            mux.stop(); mux.release(); mux = null;
            MediaExtractor verify = new MediaExtractor();
            try { verify.setDataSource(stage.getAbsolutePath()); if (verify.getTrackCount() != (audioTrack >= 0 ? 2 : 1)) throw new IllegalStateException("Invalid assembled clip"); }
            finally { verify.release(); }
            if (!stage.renameTo(target)) throw new IllegalStateException("Cannot commit assembled clip");
            store.ready(id, target); store.deleteFragments(id);
        } finally { if (mux != null) { try { mux.release(); } catch (RuntimeException ignored) {} } }
    }
    private static int findTrack(MediaExtractor input, String prefix) {
        for (int i = 0; i < input.getTrackCount(); i++) {
            String mime = input.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith(prefix)) return i;
        }
        return -1;
    }
}
