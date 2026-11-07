package club.midlife.rb1speed;

import android.content.Context;
import android.media.*;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import java.util.Set;

/** Best-effort foreground audio. No network, background playback, or volume changes. */
final class DriveAudio {
    static final String GREETING = "安全運転で、いってらっしゃい";
    private static final long GREETING_DELAY_MS = 5000;
    private static final AudioAttributes ATTRIBUTES = new AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AudioManager manager;
    private final AudioFocusRequest focus;
    private AudioTrack chime;
    private TextToSpeech speech;
    private boolean active, focused, focusLost, pendingGreeting, closed, chimePlaying;
    private long greetingAt, greetingDeadline;
    private final Runnable speakGreeting = new Runnable() {
        @Override public void run() { prepareGreeting(TextToSpeech.SUCCESS); }
    };
    private final Runnable finishChime = new Runnable() {
        @Override public void run() { stopChime(); abandonFocus(); }
    };

    DriveAudio(Context context) {
        this.context = context.getApplicationContext();
        manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(ATTRIBUTES)
            .setOnAudioFocusChangeListener(new AudioManager.OnAudioFocusChangeListener() {
                @Override public void onAudioFocusChange(int change) {
                    if (change < 0) {
                        focusLost = true; cancelGreeting();
                        handler.removeCallbacks(finishChime); stopChime();
                        if (speech != null) speech.stop();
                        if (change == AudioManager.AUDIOFOCUS_LOSS) abandonFocus();
                    } else if (change == AudioManager.AUDIOFOCUS_GAIN) { focusLost = false; focused = true; }
                }
            }, handler).build();
        try {
            short[] pcm = ChimeSound.samples();
            chime = new AudioTrack.Builder().setAudioAttributes(ATTRIBUTES)
                .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(ChimeSound.SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(pcm.length * 2).setTransferMode(AudioTrack.MODE_STATIC).build();
            if (chime.write(pcm, 0, pcm.length, AudioTrack.WRITE_BLOCKING) != pcm.length ||
                chime.getState() != AudioTrack.STATE_INITIALIZED) {
                chime.release(); chime = null;
            }
        } catch (RuntimeException e) {
            if (chime != null) chime.release();
            chime = null;
        }
    }

    void resume(boolean greet) {
        active = true; focusLost = false;
        if (greet) {
            pendingGreeting = true;
            greetingAt = SystemClock.elapsedRealtime() + GREETING_DELAY_MS;
            greetingDeadline = greetingAt + 6000;
            speech = new TextToSpeech(context, new TextToSpeech.OnInitListener() {
                @Override public void onInit(final int result) {
                    handler.post(new Runnable() { @Override public void run() { prepareGreeting(result); } });
                }
            });
        }
    }

    private void prepareGreeting(int result) {
        if (closed || !active || !pendingGreeting || SystemClock.elapsedRealtime() >= greetingDeadline ||
            speech == null || result != TextToSpeech.SUCCESS) { pendingGreeting = false; return; }
        long delay = greetingAt - SystemClock.elapsedRealtime();
        if (delay > 0) {
            handler.removeCallbacks(speakGreeting);
            handler.postDelayed(speakGreeting, delay);
            return;
        }
        Set<Voice> voices = speech.getVoices();
        Voice selected = null;
        if (voices != null) for (Voice voice : voices) {
            if ("ja".equals(voice.getLocale().getLanguage()) && !voice.isNetworkConnectionRequired() &&
                (voice.getFeatures() == null || !voice.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) &&
                (selected == null || voice.getQuality() > selected.getQuality())) selected = voice;
        }
        pendingGreeting = false;
        if (selected == null || speech.setVoice(selected) != TextToSpeech.SUCCESS || !requestFocus()) return;
        speech.setAudioAttributes(ATTRIBUTES);
        speech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) {}
            @Override public void onDone(String id) {
                handler.post(new Runnable() { @Override public void run() { if (!chimePlaying && !focusLost) abandonFocus(); } });
            }
            @Override public void onError(String id) { onDone(id); }
        });
        if (speech.speak(GREETING, TextToSpeech.QUEUE_FLUSH, null, "safe-drive") == TextToSpeech.ERROR) abandonFocus();
    }

    void playChime() {
        cancelGreeting();
        if (chime == null || !requestFocus()) return;
        if (speech != null) speech.stop();
        handler.removeCallbacks(finishChime);
        try {
            chime.stop();
            if (chime.reloadStaticData() != AudioTrack.SUCCESS) { abandonFocus(); return; }
            chime.play();
            chimePlaying = true;
            handler.postDelayed(finishChime, ChimeSound.DURATION_MS + 50);
        } catch (IllegalStateException e) { abandonFocus(); }
    }

    void cancelChime() {
        if (chimePlaying) {
            handler.removeCallbacks(finishChime); stopChime(); abandonFocus();
        }
    }

    private boolean requestFocus() {
        if (!active || closed || focusLost) return false;
        if (!focused) focused = manager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        return focused;
    }

    private void stopChime() {
        chimePlaying = false;
        if (chime != null) try { chime.stop(); } catch (IllegalStateException ignored) {}
    }
    private void abandonFocus() {
        if (focused) { manager.abandonAudioFocusRequest(focus); focused = false; }
    }
    private void stop() {
        cancelGreeting();
        handler.removeCallbacks(finishChime);
        stopChime();
        if (speech != null) speech.stop();
        abandonFocus();
    }
    private void cancelGreeting() {
        pendingGreeting = false;
        handler.removeCallbacks(speakGreeting);
    }
    void pause() { active = false; stop(); }
    void close() {
        closed = true; pause();
        if (chime != null) { chime.release(); chime = null; }
        if (speech != null) { speech.shutdown(); speech = null; }
    }
}
