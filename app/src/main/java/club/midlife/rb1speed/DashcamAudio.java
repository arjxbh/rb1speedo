package club.midlife.rb1speed;

import android.media.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;

/** Microphone capture and AAC encoding on the recorder's worker thread. */
final class DashcamAudio implements AutoCloseable {
    private static final int RATE = 48000;
    private static final int FRAME_BYTES = 2;
    private static final int MAX_PENDING = 256;
    private final ArrayDeque<Sample> pending = new ArrayDeque<>();
    private final AudioTimestamp timestamp = new AudioTimestamp();
    private final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
    private AudioRecord microphone;
    private MediaCodec encoder;
    private long frames, startUs = Long.MIN_VALUE;
    private int input = -1;
    private long lastOutputUs = Long.MIN_VALUE;
    MediaFormat format;

    private static final class Sample {
        final ByteBuffer data;
        final long pts;
        Sample(ByteBuffer data, long pts) { this.data = data; this.pts = pts; }
    }

    void start() throws IOException {
        int channel = AudioFormat.CHANNEL_IN_MONO;
        int pcm = AudioFormat.ENCODING_PCM_16BIT;
        int minimum = AudioRecord.getMinBufferSize(RATE, channel, pcm);
        if (minimum <= 0) throw new IOException("Microphone format unavailable");
        microphone = new AudioRecord(MediaRecorder.AudioSource.CAMCORDER, RATE, channel, pcm, Math.max(minimum, RATE / 2));
        if (microphone.getState() != AudioRecord.STATE_INITIALIZED) throw new IOException("Microphone unavailable");
        MediaFormat config = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, RATE, 1);
        config.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        config.setInteger(MediaFormat.KEY_BIT_RATE, 96000);
        encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        encoder.configure(config, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        encoder.start();
        microphone.startRecording();
        if (microphone.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) throw new IOException("Microphone did not start");
    }

    void poll() throws IOException {
        for (int i = 0; i < 8; i++) {
            if (input < 0) input = encoder.dequeueInputBuffer(0);
            if (input < 0) break;
            ByteBuffer data = encoder.getInputBuffer(input);
            data.clear();
            int bytes = microphone.read(data, Math.min(2048, data.capacity()), AudioRecord.READ_NON_BLOCKING);
            if (bytes < 0) throw new IOException("Microphone read failed: " + bytes);
            if (bytes == 0) break;
            if (startUs == Long.MIN_VALUE) {
                if (microphone.getTimestamp(timestamp, AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS)
                    startUs = timestamp.nanoTime / 1000 - micros(timestamp.framePosition);
                else startUs = System.nanoTime() / 1000 - micros(bytes / FRAME_BYTES);
            }
            encoder.queueInputBuffer(input, 0, bytes, startUs + micros(frames), 0);
            frames += bytes / FRAME_BYTES;
            input = -1;
        }

        for (int i = 0; i < 12; i++) {
            int output = encoder.dequeueOutputBuffer(info, 0);
            if (output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { format = encoder.getOutputFormat(); continue; }
            if (output < 0) break;
            try {
                if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                    ByteBuffer encoded = encoder.getOutputBuffer(output);
                    encoded.position(info.offset);
                    encoded.limit(info.offset + info.size);
                    ByteBuffer copy = ByteBuffer.allocate(info.size);
                    copy.put(encoded).flip();
                    if (pending.size() == MAX_PENDING) pending.removeFirst();
                    pending.addLast(new Sample(copy, info.presentationTimeUs));
                    lastOutputUs = info.presentationTimeUs;
                }
            } finally { encoder.releaseOutputBuffer(output, false); }
        }
    }

    boolean reached(long pts) { return lastOutputUs >= pts; }

    void writeBefore(MediaMuxer mux, int track, long start, long end) {
        while (!pending.isEmpty() && pending.peekFirst().pts < end) {
            Sample sample = pending.removeFirst();
            if (sample.pts < start) continue;
            info.set(0, sample.data.remaining(), sample.pts - start, 0);
            mux.writeSampleData(track, sample.data, info);
        }
    }

    private static long micros(long frames) { return frames * 1000000L / RATE; }

    @Override public void close() {
        if (microphone != null) {
            try { microphone.stop(); } catch (RuntimeException ignored) {}
            microphone.release();
        }
        if (encoder != null) {
            try { encoder.stop(); } catch (RuntimeException ignored) {}
            encoder.release();
        }
        pending.clear();
    }
}
