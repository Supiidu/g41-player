# G41 Glass Player

**Experimental Android media UI for the Moto G41 · Java · Android · SystemUI hooks**

G41 Glass Player is a personal project to turn a Motorola Moto G41 into a dedicated music player (DAP) with a custom lockscreen media panel. It works as a **legacy Xposed API 82 module**, scoped to `com.android.systemui` via the Vector framework on an **Infinity X Android 16** test environment. This is an experimental ROM-specific integration, **not** a standalone Spotify or ML-based audio player.

## Features (v0.8.1 Fluid Wave)

- Custom expanded media card with artwork, metadata, progress line, and playback controls.
- Media-session integration with a fallback to standard media key events.
- **Real-time audio visualization:** FFT / waveform data through Android `Visualizer`, interpolated into continuous, filled wave shapes with `Canvas` and `Path`.
- Dynamic color accents derived from album artwork when available.
- Lockscreen clock handling and experimental InfinityBar compact-visibility control.
- Safety toggle via `Settings.Global` to fall back to the original ROM panel.

## Compatibility and risks

> **Experimental, rooted Android setup required.** Hooks depend on ROM internals and can stop working after a SystemUI update. Installing or enabling an incompatible module can cause SystemUI crashes or a bootloop. Keep ADB, a backup, and a tested recovery path available.

**Original target:** Motorola Moto G41, custom Infinity X 3.12 ROM (Android 16), Magisk/Zygisk and Vector with scope restricted to `com.android.systemui`. It is not verified on stock Motorola Android or other ROMs.

## Build on Linux

Requirements: Android SDK Platform 36 (`ANDROID_HOME`), Java 17 (`JAVA_HOME`), locally installed Gradle, `curl`, and `unzip`.

```bash
export ANDROID_HOME=/opt/android-sdk
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
bash setup.sh
```

The original `setup.sh` fetches the Xposed API 82 `compileOnly` JAR and generates a Gradle 8.13 wrapper with the system Gradle on first use. The generated JAR, wrapper outputs, and APKs are not committed. Result, when the build succeeds: `app/build/outputs/apk/debug/app-debug.apk`.

**Build status:** Source patches were reconstructed and checked for successful application; a real Android SDK compilation and on-device test have **not** been run as part of this import. Historical patch scripts can be found under [`docs/history/scripts/`](docs/history/scripts/), but their original patch Java files are represented by the Git commit history, not stored next to the scripts.

## Install and disable

Review the [original version notes](docs/history/) before changing a live phone. With a compatible rooted test device and Vector configured for **SystemUI only**:

```bash
adb shell settings put global g41_glass_player_enabled 0
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb reboot
adb shell settings put global g41_glass_player_enabled 1
adb logcat -v time -s G41GlassPlayer:V '*:S'
```

Disable without uninstalling:

```bash
adb shell settings put global g41_glass_player_enabled 0
```

If SystemUI becomes unstable, disable the module in Vector. The original notes also discuss disabling the Vector Magisk module using root/ADB in a recovery scenario; do not use that method without understanding its impact on other hooks.

## Version history

The repository was **reconstructed from provided snapshots**, not recovered from the original chronological `.git` metadata. The imported commits are ordered by version and dated when published; they are not claimed to be original commit timestamps. See [CHANGELOG.md](CHANGELOG.md) and [reconstruction notes](docs/RECONSTRUCTION.md).

## Asset note

An illustration used as `g41_lockscreen.jpg` in the v0.4/v0.5 patch archives is **not redistributed** because its license/ownership was not established. Those versions attempt to fall back to the device wallpaper when the bundled illustration is absent. The v0.8.1 interface uses the actual lockscreen wallpaper instead.

## License

No open-source license has been assigned yet. Please ask before reusing or redistributing this code; any externally sourced assets retain their own terms.
