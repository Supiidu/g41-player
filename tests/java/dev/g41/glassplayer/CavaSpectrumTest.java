package dev.g41.glassplayer;

/**
 * No Android runtime/JUnit required: run with tests/test-cava-spectrum.sh.
 * Synthetic inputs verify geometry and level normalization, not real-device
 * Visualizer callback behavior.
 */
public final class CavaSpectrumTest {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static void repeat(CavaSpectrum spectrum, float[] bands) {
        for (int i = 0; i < 150; i++) spectrum.updateFft(bands, 1f);
    }

    public static void main(String[] args) {
        CavaSpectrum spectrum = new CavaSpectrum();
        require(!spectrum.isVisible(), "Initially hidden");

        float[] bands = new float[CavaSpectrum.BANDS];
        repeat(spectrum, bands);
        require(!spectrum.isVisible(), "Silent spectrum stays invisible");

        java.util.Arrays.fill(bands, .021f);
        repeat(spectrum, bands);
        require(!spectrum.isVisible(), "Noise gate suppresses tiny inputs");

        spectrum.reset();
        java.util.Arrays.fill(bands, 0f);
        bands[0] = .85f;
        repeat(spectrum, bands);
        require(spectrum.levelAt(.5f) > spectrum.levelAt(0f),
                "Bass must peak in center");
        require(Math.abs(spectrum.levelAt(0f) - spectrum.levelAt(1f)) < .00001f,
                "The mono spectrum must mirror left/right");

        spectrum.reset();
        java.util.Arrays.fill(bands, 0f);
        bands[23] = .72f;
        repeat(spectrum, bands);
        require(spectrum.levelAt(0f) > spectrum.levelAt(.5f),
                "Treble must peak at both edges");

        spectrum.reset();
        for (int i = 0; i < bands.length; i++) {
            bands[i] = i < 8 ? .85f : i < 16 ? .46f : .16f;
        }
        repeat(spectrum, bands);
        float bass = spectrum.levelAt(.5f);
        float mid = spectrum.levelAt(.24f);
        float treble = spectrum.levelAt(0f);
        System.out.printf("Bass %.3f, mids %.3f, treble %.3f; bass/treble %.2f%n",
                bass, mid, treble, bass / treble);
        require(bass > .1f && mid > .1f && treble > .1f,
                "All three bands should appear");
        require(bass / treble < 2.3f && bass / treble > .6f,
                "Quiet but real treble should remain visually present");

        byte[] lowPacked = new byte[1024];
        lowPacked[2] = 100; // 43 Hz, Fs=44.1 kHz
        float[] extractedLow = CavaSpectrum.extractBands(lowPacked, 44100000);
        require(extractedLow[0] > 0f, "Low FFT bin reaches center");

        byte[] highPacked = new byte[1024];
        highPacked[2 * 230] = 100; // ~9.9 kHz
        float[] extractedHigh = CavaSpectrum.extractBands(highPacked, 44100000);
        require(extractedHigh[23] > 0f, "Treble FFT bin survives wide-band averaging");

        // Beta4: a quiet real treble-only transient must show at the edges.
        spectrum.reset();
        java.util.Arrays.fill(bands, 0f);
        bands[23] = .14f;
        repeat(spectrum, bands);
        require(spectrum.isVisible() && spectrum.levelAt(0f) > .05f,
                "Quiet hi-hat band must visibly reach the edge");
        require(spectrum.levelAt(.5f) < .001f,
                "High-only transient must not create synthetic bass");

        // High-frequency boost should be tunable without distorting bass.
        CavaSpectrum weakerTreble = new CavaSpectrum(.7f);
        CavaSpectrum strongerTreble = new CavaSpectrum(2f);
        repeat(weakerTreble, bands);
        repeat(strongerTreble, bands);
        require(strongerTreble.levelAt(0f) > weakerTreble.levelAt(0f),
                "Treble boost control must have a measurable effect");

        // Spatial smoothing should keep small horizontal increments gentle.
        float last = spectrum.levelAt(0f);
        for (int i = 1; i <= 1000; i++) {
            float now = spectrum.levelAt(i / 1000f);
            require(Math.abs(now - last) < .06f,
                    "Wave amplitude must not jump sharply between adjacent pixels");
            last = now;
        }

        // A single weak 8-bit high-frequency bin survives wide-band averaging.
        byte[] quietHigh = new byte[1024];
        quietHigh[2 * 230] = 20;
        float[] restoredHigh = CavaSpectrum.extractBands(quietHigh, 44100000);
        require(restoredHigh[23] > .04f,
                "Treble bin peak should not vanish into the wide-band RMS mean");

        // Zero FFT data must never create fake high-frequency motion.
        float[] silentExtracted = CavaSpectrum.extractBands(new byte[1024], 44100000);
        for (float amplitude : silentExtracted) {
            require(amplitude == 0f, "Silence must remain zero after extraction");
        }

        spectrum.reset();
        for (int i = 0; i < 150; i++) spectrum.updateRms(.30f, 1f);
        require(spectrum.isVisible(), "RMS mode should react");
        require(Math.abs(spectrum.levelAt(.5f) - spectrum.levelAt(0f)) < .00001f,
                "RMS should not invent frequency distribution");

        spectrum.reset();
        require(!spectrum.isVisible() && spectrum.levelAt(.5f) == 0f,
                "Pause must completely clear the wave");
        System.out.println("CavaSpectrum tests passed");
    }
}
