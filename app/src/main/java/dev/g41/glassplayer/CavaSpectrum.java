package dev.g41.glassplayer;

import java.util.Arrays;

/**
 * CAVA-inspired single-channel spectrum display processing.
 *
 * The Android Visualizer provides a MONO mix: one 24-band spectrum is mirrored
 * so low frequencies appear at the center and higher ones at both edges. This
 * does not pretend to reconstruct left/right stereo channels.
 *
 * Tracks three slowly moving reference levels, giving quiet-but-present treble
 * a similar visual range to bass without producing energy in silent bands.
 * Beta4 preserves the bass/mid behavior and improves real treble transients,
 * lateral spread and spatial smoothing.
 * The input is already logarithmically scaled by GlassOverlay's FFT capture.
 */
final class CavaSpectrum {
    static final int BANDS = 24;
    private static final int GROUPS = 3;
    private static final int BANDS_PER_GROUP = BANDS / GROUPS;
    private static final float INPUT_GATE = .028f;
    private static final float TREBLE_GATE = .021f;
    private final float[] levels = new float[BANDS];
    private final float[] targets = new float[BANDS];
    private final float[] spread = new float[BANDS];
    private final float[] energy = new float[GROUPS];
    private final float[] reference = new float[GROUPS];
    private final float[] groupGain = new float[GROUPS];
    private float autoGain = 1f;
    private boolean visible;
    private final float trebleBoost;

    CavaSpectrum() { this(1.25f); }

    /** Experimental treble tuning without recompiling; baseline=1.25. */
    CavaSpectrum(float boost) {
        trebleBoost = clamp(boost, .7f, 2f);
        reset();
    }

    private static float clamp(float value, float low, float high) {
        return Math.max(low, Math.min(high, value));
    }

    /**
     * Convert Android Visualizer's packed 8-bit FFT to logarithmic bands.
     * The Visualizer capture is typically only ~1024 points; for the lowest
     * frequencies, neighboring target bands can share a single FFT bin.
     * Interpolate those missing SAMPLES, not synthetic audio amplitudes.
     * Sample rate from the Android callback is reported in milliHertz.
     */
    static float[] extractBands(byte[] packedFft, int milliHz) {
        float[] result = new float[BANDS];
        if (packedFft == null || packedFft.length < 64) return result;
        final int n = packedFft.length;
        double sampleHz = milliHz / 1000d;
        if (sampleHz < 8000d) sampleHz = 44100d;
        final double lowHz = 35d;
        final double highHz = Math.min(12000d, sampleHz * .48d);
        final double logRange = Math.log(highHz / lowHz);
        final double[] sum = new double[BANDS];
        final int[] count = new int[BANDS];
        final double[] maximum = new double[BANDS];
        for (int k = 1; k < n / 2; k++) {
            double hz = k * sampleHz / n;
            if (hz < lowHz || hz > highHz) continue;
            int band = (int) (Math.log(hz / lowHz) / logRange * BANDS);
            band = Math.max(0, Math.min(BANDS - 1, band));
            // RMS within a log-band preserves transient treble bins better
            // than an arithmetic magnitude mean across a very wide band.
            double magnitude = Math.hypot((double) packedFft[2 * k],
                    (double) packedFft[2 * k + 1]);
            sum[band] += magnitude * magnitude;
            maximum[band] = Math.max(maximum[band], magnitude);
            count[band]++;
        }
        double[] mean = new double[BANDS];
        for (int i = 0; i < BANDS; i++) {
            if (count[i] > 0) {
                double rms = Math.sqrt(sum[i] / count[i]);
                // A sharp hi-hat can be diluted across many wide-band bins.
                // Blend genuine peak magnitude with RMS in treble bands only.
                mean[i] = i >= 16 ? (.62d * rms + .38d * maximum[i]) : rms;
                continue;
            }
            int left = i - 1, right = i + 1;
            while (left >= 0 && count[left] == 0) left--;
            while (right < BANDS && count[right] == 0) right++;
            if (left >= 0 && right < BANDS) {
                float t = (i - left) / (float) (right - left);
                mean[i] = mean[left] * (1f - t)
                        + Math.sqrt(sum[right] / count[right]) * t;
            } else if (left >= 0) {
                mean[i] = mean[left];
            } else if (right < BANDS) {
                mean[i] = Math.sqrt(sum[right] / count[right]);
            }
        }
        for (int i = 0; i < BANDS; i++) {
            // Lower only the treble threshold; the later gate keeps
            // low-level noise from turning into a permanent edge stripe.
            double threshold = i >= 16 ? 2.25d : 3.5d;
            result[i] = clamp((float) ((Math.log1p(mean[i]) - Math.log(threshold)) / 2.9d),
                    0f, 1f);
        }
        return result;
    }

    void updateFft(float[] fft, float sensitivity) {
        if (fft == null || fft.length < BANDS) { reset(); return; }
        Arrays.fill(energy, 0f);
        for (int i = 0; i < BANDS; i++) {
            float v = Math.max(0f, fft[i] - (i >= 16 ? TREBLE_GATE : INPUT_GATE));
            energy[i / BANDS_PER_GROUP] += v * v;
        }

        // CAVA applies frequency-dependent EQ. Our input has already passed
        // logarithmic magnitude compression, so use a GENTLER treble tilt
        // and a strictly bounded adaptive gain for low/mid/high groups.
        for (int g = 0; g < GROUPS; g++) {
            float rms = (float) Math.sqrt(energy[g] / BANDS_PER_GROUP);
            float observed = Math.max(.14f, rms);
            float response = observed > reference[g] ? .045f : .009f;
            reference[g] += (observed - reference[g]) * response;
            groupGain[g] = clamp((float) Math.pow(.32f / reference[g], .60d),
                    .65f, g == 2 ? 2.25f : 2f);
        }

        float peak = 0f;
        for (int i = 0; i < BANDS; i++) {
            float position = i / (float) (BANDS - 1);
            float frequencyTilt = .78f + .90f * (float) Math.pow(position, .80d);
            // Do not alter the bass and mid-frequency curve from beta3.
            if (i >= 16) {
                float treblePosition = (i - 15f) / 8f;
                frequencyTilt *= 1f + (.35f * treblePosition * trebleBoost);
            }
            float usable = Math.max(0f,
                    fft[i] - (i >= 16 ? TREBLE_GATE : INPUT_GATE));
            targets[i] = usable * frequencyTilt * groupGain[i / BANDS_PER_GROUP];
        }

        // Spread only actual treble energy to its neighbors, preserving
        // the far edge by clamping index 23. No energy in = no energy out.
        System.arraycopy(targets, 0, spread, 0, BANDS);
        for (int i = 16; i < BANDS; i++) {
            float previous = targets[i - 1];
            float next = targets[Math.min(BANDS - 1, i + 1)];
            spread[i] = .56f * targets[i] + .22f * previous + .22f * next;
        }
        System.arraycopy(spread, 0, targets, 0, BANDS);
        for (int i = 0; i < BANDS; i++) peak = Math.max(peak, targets[i]);
        float wantedGain = clamp(.77f / Math.max(.60f, peak), .85f, 1.55f);
        autoGain += (wantedGain - autoGain) * .04f;

        visible = false;
        for (int i = 0; i < BANDS; i++) {
            float scaled = clamp(targets[i] * autoGain * sensitivity, 0f, 1f);
            float target = (float) Math.pow(scaled, .80d);
            smooth(i, target);
            if (levels[i] > .006f) visible = true;
        }
    }

    void updateRms(float rms, float sensitivity) {
        // RMS supplies a volume envelope, not information about frequencies.
        float scaled = clamp((rms - .02f) * sensitivity * 1.4f, 0f, 1f);
        float target = (float) Math.pow(scaled, .80d);
        visible = false;
        for (int i = 0; i < BANDS; i++) {
            smooth(i, target);
            if (levels[i] > .006f) visible = true;
        }
    }

    private void smooth(int index, float target) {
        float current = levels[index];
        // Let short high-frequency sounds linger for one or two extra frames.
        float decay = index >= 16 ? .12f : .15f;
        current += (target - current) * (target > current ? .42f : decay);
        levels[index] = current < .001f ? 0f : current;
    }

    /**
     * Spatial 5-tap smoothing turns narrow treble impulses into rounded hills,
     * without inventing frequencies or blurring temporal (attack) behavior.
     */
    private float smoothBand(int i) {
        return .07f * levels[Math.max(0, i - 2)]
                + .23f * levels[Math.max(0, i - 1)]
                + .40f * levels[i]
                + .23f * levels[Math.min(BANDS - 1, i + 1)]
                + .07f * levels[Math.min(BANDS - 1, i + 2)];
    }

    /** x=0: treble, x=.5: bass, x=1: treble (mirrored mono). */
    float levelAt(float x) {
        float distance = clamp(Math.abs(2f * x - 1f), 0f, 1f);
        float pos = distance * (BANDS - 1);
        int left = Math.min(BANDS - 1, (int) pos);
        int right = Math.min(BANDS - 1, left + 1);
        float t = pos - left;
        return clamp(smoothBand(left) * (1f - t) + smoothBand(right) * t,
                0f, 1f);
    }

    boolean isVisible() { return visible; }

    void reset() {
        Arrays.fill(levels, 0f);
        Arrays.fill(targets, 0f);
        Arrays.fill(spread, 0f);
        Arrays.fill(energy, 0f);
        Arrays.fill(reference, .28f);
        Arrays.fill(groupGain, 1f);
        autoGain = 1f;
        visible = false;
    }
}
