<p align="center"><img src="docs/logo.png" alt="OpenOSRS" width="128"></p>

<p align="center"><a href="https://github.com/OOSRS/OOSRS-Launcher/releases/latest">Download</a> · <a href="https://github.com/OOSRS/OOSRS">Client source</a> · <a href="#build">Build</a></p>

# OpenOSRS Launcher

A small desktop launcher for OpenOSRS. Separate updates for the launcher and client. Verified downloads. A reusable local cache. Clear startup errors.

A compact 480 × 264 window with a slate-and-blue finish, a draggable header, and one main launch action. Updates and logs stay within reach. Keyboard focus and wrapped error messages remain visible.

## Start

Install **Java 11 or newer** to open the launcher JAR, then download and open the release. The launcher runs the client with its own verified Java 11 runtime.

```sh
java -jar openosrs-launcher-1.0.4.jar
```

Select **Launch OpenOSRS**. No GitHub account is needed to download public releases.

The launcher closes automatically once the client confirms successful initialization. The client keeps running. If startup fails or times out, the launcher stays open with the error and access to logs.

On the first launch, OpenOSRS downloads a pinned Eclipse Temurin Java 11 runtime (about 40 MB). It checks the archive checksum and starts a compatibility probe, including `java.applet.AppletStub`, before launching the client. Later launches reuse this private cache. No administrator access or system Java changes are needed. A failed download can be retried; existing client sessions keep their runtime.

Runtime downloads cover Windows x64, Linux x64/ARM64, macOS Intel/Apple Silicon, and Alpine Linux x64. Windows 11 ARM uses its x64 emulation. A first-time runtime download needs internet access; a verified cached runtime can be reused offline. Other platforms report an unsupported-runtime error.

## Updates and caching

- Client updates come from `OOSRS/OOSRS` releases.
- Launcher updates come from `OOSRS/OOSRS-Launcher` releases.
- Each channel publishes a small `update.properties` file naming the versioned JAR, SHA-256, and required Java version.
- `minimumJava` is the minimum supported runtime. The legacy `java=21` field remains so launcher 1.0.0 can still self-update; current launchers use `minimumJava=11`.
- Files download into temporary storage. The launcher verifies the digest and JAR before making it available.
- Verified versions remain under `~/.openosrs/launcher/`. Repeated launches reuse them.
- When an update check fails, a verified cached client can still be selected. A known incompatible game revision is not selected as fallback.
- A startup marker records that client initialization completed before updating the last-working pointer. It does not prove a successful game login.

SHA-256 is checked against metadata delivered over HTTPS from the release repository. This release does not use a separate artifact-signing key.

## Launcher self-update

The launcher checks its own channel on startup and when you select **Check updates**. **Update launcher** appears when a newer release exists.

The new JAR runs a small helper, waits for the current launcher to exit, saves the old file as `.previous`, replaces the installed JAR, and restarts it. A failed replacement preserves the old copy; immediate restart failure restores it. The install folder must be writable. Existing game sessions are left running.

## Errors and logs

Failed downloads leave verified cached files intact. Missing releases, unavailable downloads, incompatible Java versions, and early client exits are reported in the launcher. The **Logs** button opens `~/.openosrs/launcher/logs/`.

If startup takes more than 90 seconds, the client is left running for inspection. Check its window and logs before launching again. If a game update makes an older client incompatible, wait for a compatible client release.

## Build

Use JDK 11:

```sh
./gradlew jar
java -jar build/libs/openosrs-launcher-1.0.4.jar
```

Windows: use `gradlew.bat`. This is a JAR distribution, so Java must already be installed to open the launcher itself. The client runtime is downloaded separately.

For a download-only check without opening the client:

```sh
java -jar build/libs/openosrs-launcher-1.0.4.jar --prepare
```

This fetches release metadata, verifies and caches both artifacts and the client runtime, and exits. Use `--prepare-runtime` to download and check only Java. `--version` prints the launcher version.

## Release

Increase `version` in `build.gradle.kts`, commit, and push a matching `vX.Y.Z` tag. The release workflow builds the JAR, checks runtime installation and cache reuse on Windows, Linux and macOS, and publishes its update metadata only after those checks pass. Runtime pins live in `src/main/resources/net/openosrs/launcher/runtimes.properties`. Client and launcher version numbers are independent.

See [LICENSE](LICENSE).
