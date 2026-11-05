# Import provenance and validation

This repository's source history was recreated from one **starter project** archive and ten **incremental patch** archives (`v0.2` → `v0.8.1`). The owner supplied an additional second copy of the v0.6 patch; both archives were byte-for-byte identical.

For each version, the original `apply-v*.sh` script was executed against the preceding staged source. A disposable no-op `gradlew` stub was used **only to skip compilation during patch reconstruction**; it is not included in the repository. The scripts' file operations and guards succeeded, including the v0.8.1 check that the v0.8 source is the expected base. Generated backups, built APKs, signing material, and dependency JARs were excluded.

Because this environment lacks Android SDK Platform 36 and its matching build tooling, **no actual Gradle build or device test is claimed**. The code has not been independently verified to install or work on an arbitrary ROM.

Each imported version is represented by a distinct Git commit containing the source as it existed after applying that patch. These commits do **not** preserve original author timestamps, messages, or any earlier Git graph that was not included in the supplied ZIPs. The original per-version notes and patch scripts are stored under `docs/history/` for reference; the patch scripts alone are not independently runnable from there because source patch files were applied into the commit history.

The archived v0.4 illustration was excluded from the public repository because its redistribution rights were unclear. As coded, v0.4/v0.5 fall back to a wallpaper provided by the device if that optional asset is not available.
