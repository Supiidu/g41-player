# G41 Glass Player

**An experimental, audio-reactive lock-screen media interface for a Motorola Moto G41 repurposed as a dedicated music device.**

![Version](https://img.shields.io/badge/version-0.8.1%20Fluid%20Wave-5965d9)
![Java](https://img.shields.io/badge/Java-17-orange)
![Android](https://img.shields.io/badge/Android%20SDK-36-3ddc84)
![Status](https://img.shields.io/badge/status-experimental-f59e0b)

> [!WARNING]
> **Rooted device and compatible ROM required.** G41 Glass Player hooks private SystemUI/InfinityBar internals. It is **not a standalone music or Spotify client** and is **not an AI/ML audio visualizer**. An incompatible module or ROM update can crash SystemUI or cause a bootloop. Have ADB access, backups, and a tested recovery plan before trying it on a device.

## Why this project exists

The goal is to give an older **Moto G41** a second life as a dedicated digital audio player (DAP). Rather than replace a streaming application, the project explores how a custom, music-focused interface can be integrated with an Android lock screen.

**G41 Glass Player** is a Java-based **legacy Xposed API 82 module** that runs in `com.android.systemui`. On the target Infinity X ROM, it intercepts the InfinityBar media expansion, displays a custom player when enabled, and preserves the original expansion when its prerequisites are not met.

*Resumo em português:* uma interface experimental de música para a tela de bloqueio do Moto G41, com controles de reprodução, cores da capa e ondas que reagem ao áudio real.

## Highlights

- **Fluid Wave (v0.8.1):** two filled, continuously interpolated waves drawn with Android `Canvas` / `Path`, rather than separate CAVA-style bars.
- **Real audio response:** Android `Visualizer(0)` provides FFT data (24 frequency bands) or waveform RMS when available. The UI interpolates those measurements for smooth rendering; it does not invent beats or generate random music activity.
- **Album-reactive colors:** the interface derives an accent from available artwork, with a fallback color.
- **Media controls:** track metadata, album art, previous/play-pause/next, and a seekable progress line backed by `MediaSessionManager` / `MediaController` when accessible; media key events are a fallback for playback controls.
- **Lock-screen integration:** an expanded card over the device's real wallpaper, with conservative attempts to hide identifiable clock and compact InfinityBar views.
- **Opt-in and recoverable:** the custom panel is disabled by default through an Android global setting. The original ROM expansion remains the fallback if hooking or overlay creation fails.

**Visual demo:** screenshots and a recording are not yet included in this repository. No mockup is presented as an actual device capture.

## Compatibility and limitations

| Item | Current project |
| --- | --- |
| Original development device | Motorola Moto G41 |
| Target test environment | Infinity X 3.12, Android 16, rooted with Magisk/Zygisk and Vector |
| Xposed entry point | `dev.g41.glassplayer.G41Hook` |
| Hook scope | `com.android.systemui` only |
| Build language / toolchain | Java 17, Android Gradle Plugin 8.13.2 |
| Android configuration | `compileSdk 36`, `minSdk 28`, `targetSdk 35` |
| Current source version | `0.8.1-fluid-wave` (`versionCode 11`) |
| Validation | Patch reconstruction verified; full Android build and device behavior **not independently validated** during import |

The `minSdk` value is a build setting, **not a claim of compatibility** with Android 9+ devices. This integration depends on specific InfinityBar class names and ROM implementation details. In particular:

- `com.android.systemui.axdynamicbar.ui.AxDynamicBarKeyguardExpansion.expand()` must be present for the hook.
- The compact InfinityBar may be a node inside a fullscreen Compose root. The module deliberately **does not hide the entire Compose root**; the bar may remain visible.
- Android may restrict output-mix audio capture. If `Visualizer` cannot supply real data, the waves rest instead of faking an animation.
- There is **no verified release APK, automated test suite, or CI build** included in this repository.

## How it works

```text
SystemUI / InfinityBar media expansion
    |
    v
G41Hook.java                 Xposed API 82 entry point
    |                         checks the opt-in setting,
    |                         keyguard and selected media event
    v
GlassOverlay.java            creates a custom lock-screen window
    |                         reads active media sessions,
    |                         displays artwork and playback controls
    |                         captures Visualizer FFT / waveform RMS
    v
GlassSurface                 smooths audio levels and draws
                              filled, album-colored waves

CompactBarVisibility.java    optionally hides only a safely identified
                              independent InfinityBar View; restores it on close
```

Source: [`G41Hook.java`](app/src/main/java/dev/g41/glassplayer/G41Hook.java) · [`GlassOverlay.java`](app/src/main/java/dev/g41/glassplayer/GlassOverlay.java) · [`CompactBarVisibility.java`](app/src/main/java/dev/g41/glassplayer/CompactBarVisibility.java).

## Build from source (Linux)

### Prerequisites

- **JDK 17**, with `JAVA_HOME` pointing to its installation.
- Android SDK with **Platform 36**, with `ANDROID_HOME` pointing to the SDK.
- A locally installed `gradle` command for the first wrapper bootstrap.
- `curl` and `unzip`.
- Internet access for the Xposed API JAR and Gradle/Android dependencies.
- `adb` and a compatible test device **only when installing/testing**.

`setup.sh` downloads the Xposed API 82 compile-only JAR and, when absent, generates a **Gradle 8.13 wrapper** using the locally installed Gradle. Neither the downloaded JAR nor generated build artifacts are committed.

```bash
git clone https://github.com/Supiidu/g41-player.git
cd g41-player

# Adjust these paths to match your Linux installation:
export ANDROID_HOME=/opt/android-sdk
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk

bash setup.sh
```

If compilation succeeds, the expected APK path is:

```text
app/build/outputs/apk/debug/app-debug.apk
```

After the wrapper has been bootstrapped, rebuild with:

```bash
./gradlew --no-daemon :app:assembleDebug
```

> [!NOTE]
> This is a **documented build procedure, not a verified build result**. The reconstructed source was not compiled with a full Android SDK during repository import. `setup.sh` still contains a historical “diagnostic APK” message even though the current application version is v0.8.1.

## Install and try it

> [!CAUTION]
> Do not install on your primary phone. These instructions assume you already have root, a compatible Infinity X setup, the **Vector** hooking framework configured for `com.android.systemui`, and a recovery method. Installing the APK alone does not enable the hook.

1. With the test device connected through ADB, keep the custom panel disabled while installing.
2. Install the successfully built APK, confirm the module is enabled and scoped to **SystemUI only** in Vector, then reboot.
3. Opt in using the global setting below. With media playing on the lock screen, open the InfinityBar media expansion; touch outside the expanded card to close it.

```bash
adb shell settings put global g41_glass_player_enabled 0
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb reboot
# After the device reconnects:
adb shell settings put global g41_glass_player_enabled 1
```

The hook tries to open the custom overlay only for a recognized **media** event while the **keyguard is locked**. Other InfinityBar events, disabled settings, and failures fall back to the original ROM behavior.

## Configuration

These are actual `Settings.Global` keys read by the source. Some options are read when the overlay opens, so close and reopen the player after changing them.

| Key | Default | Behavior |
| --- | --- | --- |
| `g41_glass_player_enabled` | `0` | `1` enables the custom expansion; `0` preserves the original. |
| `g41_glass_cava_sensitivity` | `1.0` | Audio visual sensitivity, clamped in code to `0.5`–`2.5`. |
| `g41_glass_hide_compact` | `1` | Tries to hide an identified compact InfinityBar View while expanded. |
| `g41_glass_hide_compact_aggressive` | `0` | Experimental additional attempt to hide **one** eligible small ComposeView. May hide an unrelated UI element. |

For example, adjust the wave sensitivity and disable compact-bar hiding:

```bash
adb shell settings put global g41_glass_cava_sensitivity 1.25
adb shell settings put global g41_glass_hide_compact 0
```

Avoid enabling aggressive compact hiding unless you have investigated the candidate views and can recover from a broken UI.

## Troubleshooting and recovery

**Collect the module logs:**

```bash
adb logcat -v time -s G41GlassPlayer:V '*:S'
```

| Symptom | What to check |
| --- | --- |
| Original InfinityBar opens instead | Confirm the module's Vector scope, locked keyguard, active media event, and `g41_glass_player_enabled=1`. If the ROM changed the hooked method, the module preserves the original action. |
| Wave stays flat | Look for `Ondas: modo=REAL_FFT`, `REAL_WAVEFORM_RMS` or `SEM_CAPTURA_REPOUSO` in logcat. Capture availability varies with the device and Android permissions. |
| Compact InfinityBar remains visible | The component may not be an independent View. Check `Compact v0.8` logs; the safe default refuses to hide fullscreen Compose roots. |
| Clock remains visible | Clock discovery is best-effort and ROM-dependent; unknown lock-screen roots are intentionally left unchanged. |

**Disable the custom panel without uninstalling:**

```bash
adb shell settings put global g41_glass_player_enabled 0
```

If SystemUI is unstable, use your previously tested recovery route to disable the module in Vector or its root framework. The opt-in toggle alone may not be sufficient if the hook itself is failing during SystemUI startup. See the [v0.8.1 recovery notes](docs/history/README-v081.md) for historical device-specific procedures; do not blindly apply another device's recovery commands.

## Repository layout

```text
app/src/main/java/dev/g41/glassplayer/
  G41Hook.java                  SystemUI / InfinityBar Xposed entry point
  GlassOverlay.java             Expanded player, media controls and Fluid Wave
  CompactBarVisibility.java     Best-effort compact-bar visibility and restore
app/src/main/assets/xposed_init Legacy Xposed hook registration
app/src/main/AndroidManifest.xml
app/build.gradle                SDK levels, Java compatibility, version
setup.sh                        Local dependency + wrapper bootstrap and build
CHANGELOG.md                    Reconstructed version history
docs/RECONSTRUCTION.md          Import provenance and verification limits
docs/history/                   Historical notes and patch scripts
```

## Development history

The project grew from a **diagnostic Xposed hook** through a custom media card, dynamic waves, CAVA-style bars, compact-bar handling and finally **Fluid Wave v0.8.1**.

The Git history was **reconstructed from a starter ZIP and incremental patch archives**, not imported from original chronological Git commits. Import dates are publication dates, not original development timestamps. See [CHANGELOG.md](CHANGELOG.md) and [reconstruction details](docs/RECONSTRUCTION.md).

An illustration from the earlier v0.4/v0.5 archives is intentionally **not redistributed**, because redistribution rights were not established.

## Contributions and license

This is a personal experimental project. Issues and carefully scoped proposals are welcome; include your ROM/device version, relevant logs (with personal data removed), and reproduction steps. ROM-specific changes should fail safely and preserve native SystemUI behavior.

**No open-source license has been assigned.** Public source visibility does not automatically grant permission to copy, modify, or redistribute it. Third-party components and assets retain their respective terms.
