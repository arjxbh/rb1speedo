package club.midlife.rb1speed;

/** Original decaying bell sound, synthesized locally without an audio asset. */
public final class ChimeSound {
    public static final int SAMPLE_RATE = 22050;
    public static final int DURATION_MS = 650;
    private ChimeSound() {}

    public static short[] samples() {
        short[] pcm = new short[SAMPLE_RATE * DURATION_MS / 1000];
        for (int i = 0; i < pcm.length; i++) {
            double t = i / (double) SAMPLE_RATE;
            double attack = Math.min(1, t / .006);
            double release = Math.min(1, (pcm.length - 1 - i) / (SAMPLE_RATE * .025));
            double bell = Math.sin(2 * Math.PI * 1046.5 * t) * Math.exp(-6 * t)
                + .35 * Math.sin(2 * Math.PI * 1568 * t) * Math.exp(-9 * t)
                + .12 * Math.sin(2 * Math.PI * 2637 * t) * Math.exp(-14 * t);
            pcm[i] = (short) Math.round(16000 * attack * release * bell);
        }
        return pcm;
    }
}
