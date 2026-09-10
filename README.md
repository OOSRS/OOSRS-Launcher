<p align="center"><img src="docs/openosrs-launcher.svg" alt="OpenOSRS Launcher" width="920"></p>

<p align="center"><a href="https://github.com/OOSRS/OOSRS-Launcher/releases/latest">Download</a> · <a href="https://github.com/OOSRS/OOSRS">Client source</a> · <a href="#build">Build</a></p>

# OpenOSRS Launcher

A small desktop launcher for OpenOSRS. Separate updates for the launcher and client. Verified downloads. A reusable local cache. Clear startup errors.

## Start

Install **Java 11 or newer**, download the release JAR, then open it with Java. Both the launcher and client run directly on Java 11.

```sh
java -jar openosrs-launcher-1.0.1.jar
```

Select **Launch OpenOSRS**. No GitHub account is needed to download public releases.

The launcher uses the same Java installation to start the client. Java is not downloaded or switched automatically.

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

Use JDK 21:

```sh
./gradlew jar
java -jar build/libs/openosrs-launcher-1.0.1.jar
```

Windows: use `gradlew.bat`. Native installers and a bundled Java runtime are outside this initial JAR distribution.

For a download-only check without opening the client:

```sh
java -jar build/libs/openosrs-launcher-1.0.1.jar --prepare
```

This fetches release metadata, verifies and caches both artifacts, and exits. `--version` prints the launcher version.

## Release

Increase `version` in `build.gradle.kts`, commit, and push a matching `vX.Y.Z` tag. The release workflow builds the JAR and publishes its update metadata. Client and launcher version numbers are independent.

See [LICENSE](LICENSE).
