package club.midlife.rb1speed;

import android.content.Context;
import android.media.*;
import android.os.Handler;
import android.os.Looper;

/** Best-effort foreground audio. No network or background playback. */
final class DriveAudio {
    static final String GREETING = "安全運転で、いってらっしゃい";
    private static final long GREETING_DELAY_MS = 5000;
    private static final AudioAttributes ATTRIBUTES = new AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
    private static final AudioAttributes CHIME_ATTRIBUTES = new AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AudioManager manager;
    private final AudioFocusRequest focus;
    private AudioTrack chime;
    private MediaPlayer greeting;
    private boolean active, focused, focusLost, pendingGreeting, closed, chimePlaying;
    private String greetingStatus = "idle";
    private final Runnable speakGreeting = new Runnable() {
        @Override public void run() { playGreeting(); }
    };
    private final Runnable finishChime = new Runnable() {
        @Override public void run() { stopChime(); }
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
                        abandonFocus();
                    } else if (change == AudioManager.AUDIOFOCUS_GAIN) { focusLost = false; focused = true; }
                }
            }, handler).build();
        try {
            short[] pcm = ChimeSound.samples();
            chime = new AudioTrack.Builder().setAudioAttributes(CHIME_ATTRIBUTES)
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

    void setLaunchVolume() {
        if (manager == null || manager.isVolumeFixed()) return;
        try {
            int maximum = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            manager.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(maximum * .7f), 0);
        } catch (SecurityException ignored) {
            // Device policy may prevent volume changes; opening the dashboard must still work.
        }
    }

    String volumeDiagnostics() {
        if (manager == null) return "Media volume: audio service unavailable";
        int maximum = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int current = manager.getStreamVolume(AudioManager.STREAM_MUSIC);
        return "Media volume: " + current + "/" + maximum + " (" +
            (maximum > 0 ? Math.round(100f * current / maximum) : 0) + "%)" +
            "\n70% target: step " + Math.round(maximum * .7f) +
            "\nFixed volume: " + (manager.isVolumeFixed() ? "yes" : "no") +
            "\nGreeting: " + greetingStatus;
    }

    void resume(boolean greet) {
        active = true; focusLost = false;
        if (greet) {
            cancelGreeting();
            pendingGreeting = true;
            greetingStatus = "waiting for five-second delay";
            handler.postDelayed(speakGreeting, GREETING_DELAY_MS);
        }
    }

    private void playGreeting() {
        if (closed || !active || !pendingGreeting) return;
        pendingGreeting = false;
        if (!requestFocus()) {
            greetingStatus = "audio focus unavailable";
            return;
        }
        try {
            // The standalone APK build compiles Java before Android resources are linked.
            int resource = context.getResources().getIdentifier("startup_greeting", "raw", context.getPackageName());
            if (resource != 0) greeting = MediaPlayer.create(context, resource, ATTRIBUTES, AudioManager.AUDIO_SESSION_ID_GENERATE);
            if (greeting == null) {
                finishGreeting("audio unavailable");
                return;
            }
            greeting.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override public void onCompletion(MediaPlayer player) {
                    if (player == greeting) finishGreeting("played");
                }
            });
            greeting.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override public boolean onError(MediaPlayer player, int what, int extra) {
                    if (player == greeting) finishGreeting("playback error (" + what + "/" + extra + ")");
                    return true;
                }
            });
            greeting.start();
            greetingStatus = "playing";
        } catch (RuntimeException e) {
            finishGreeting("playback failed: " + e.getClass().getSimpleName());
        }
    }

    void playChime() {
        if (!active || closed) return;
        cancelGreeting();
        // Release any greeting focus, then mix the bell without asking music to duck.
        abandonFocus();
        if (chime == null) return;
        handler.removeCallbacks(finishChime);
        try {
            chime.stop();
            chimePlaying = false;
            if (chime.reloadStaticData() != AudioTrack.SUCCESS) return;
            chime.play();
            chimePlaying = true;
            handler.postDelayed(finishChime, ChimeSound.DURATION_MS + 50);
        } catch (IllegalStateException e) { stopChime(); }
    }

    void cancelChime() {
        if (chimePlaying) {
            handler.removeCallbacks(finishChime); stopChime();
        }
    }

    private boolean requestFocus() {
        if (!active || closed || focusLost || manager == null) return false;
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
        abandonFocus();
    }
    private void finishGreeting(String status) {
        greetingStatus = status;
        if (greeting != null) {
            greeting.release();
            greeting = null;
        }
        abandonFocus();
    }
    private void cancelGreeting() {
        if (pendingGreeting || greeting != null) finishGreeting("cancelled");
        pendingGreeting = false;
        handler.removeCallbacks(speakGreeting);
    }
    void pause() { active = false; stop(); }
    void close() {
        closed = true; pause();
        if (chime != null) { chime.release(); chime = null; }
    }
}
