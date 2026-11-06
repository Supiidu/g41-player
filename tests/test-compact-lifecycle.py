#!/usr/bin/env python3
"""Static regression checks for the Moto G41 InfinityBar v0.10-beta3.

Does NOT compile the Android app or verify SystemUI runtime behavior.
Run from the repository root: python3 tests/test-compact-lifecycle.py
"""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/dev/g41/glassplayer"
glass = (src / "GlassOverlay.java").read_text()
compact = (src / "CompactBarOverlay.java").read_text()
native = (src / "NativeMediaChipVisibility.java").read_text()
hook = (src / "G41Hook.java").read_text()
build = (root / "app/build.gradle").read_text()

checks = {
    "compact FFT is released before expanded FFT is created":
        glass.index("CompactBarOverlay.expandedChanged();")
        < glass.index("startAudioCapture();"),
    "compact has a synchronized expanded state handoff":
        'if (GlassOverlay.isOpen()) {' in compact
        and 'hide();' in compact.split("static void expandedChanged()")[1].split("private static boolean")[0],
    "expanded FFT still uses real Android Visualizer":
        "new Visualizer(0)" in glass and "CavaSpectrum.extractBands(fft, rate)" in glass,
    "native media chip stays suppressed during expanded":
        "(!CompactBarOverlay.isVisible() && !GlassOverlay.isOpen())" in native,
    "native chip suppression is optional and off by default":
        "SETTINGS_HIDE, 0) != 1" in native,
    "native suppression targets the boolean visibility overload only":
        "parameters[0] != boolean.class" in native
        and "param.args[0] = Boolean.FALSE" in native
        and "directChipCaller()" in native,
    "original expanded Compose transition is untouched":
        "MutableTransitionState" in native,
    "only media (not a notificationAlert) is suppressed":
        'getObjectField(state, "notificationAlert")' in native
        and "if (alert != null) return false;" in native
        and "if (!isMedia(shown)) return false;" in native,
    "compact keeps the window while screen is off":
        "if (!screenInteractive()) {" in compact
        and "MAIN.postDelayed(this, 600L);" in compact
        and "if (!screenWasOff)" in compact,
    "compact releases FFT when screen is off":
        "stopVisualizer();\n                latestFft = null;\n                spectrum.reset();" in compact,
    "compact resumes FFT only while playing":
        "if (!playing) {" in compact
        and "if (visualizer != null) stopVisualizer();" in compact,
    "compact refuses FFT while expanded":
        "if (GlassOverlay.isOpen() || !screenInteractive() || !playing) return;" in compact,
    "compact recovers after expanded closes":
        "CompactBarOverlay.expandedChanged();" in glass.split("public static void hide()")[1],
    "parent Compose observed to rearm compact if child stays hidden":
        '"AxDynamicBarKeyguardChip".equals(method.getName())' in hook
        and '"KeyguardChipBody".equals(method.getName())' not in hook,
    "compact remains opt-in":
        "getInt(context.getContentResolver(), SETTING, 0) != 1" in compact,
    "safe fallback for hidden native":
        "return false;" in native.split("private static boolean maySuppress")[1],
    "player approved treble boost remains unchanged":
        '"g41_glass_cava_treble_boost", .80f' in glass,
    "beta3 versionCode increments":
        "versionCode 19" in build and "0.10.0-beta3-audio-handoff" in build,
}

failed = [name for name, ok in checks.items() if not ok]
for name, passed in checks.items():
    print(("PASS" if passed else "FAIL") + ": " + name)
print(f"{len(checks) - len(failed)}/{len(checks)} static checks passed")
if failed:
    raise SystemExit(1)
print("NOTE: static checks only. Compile and test on the Moto G41 before merging.")
