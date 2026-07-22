# Discord Rich Presence

## Behavior and privacy

Discord Rich Presence is an opt-in feature under **Settings → Content & Playback → Discord Rich Presence**. It is disabled by default. When enabled and connected, Flow shares the active media title, creator or artist, thumbnail, activity type, and playback timestamps. Live streams omit finite timestamps. Presence is cleared on pause, stop, disable, unlink, and shutdown.

The GitHub flavor uses a Kizzy-style direct Discord Gateway connection. This is not Discord's official Social SDK or OAuth. Connecting signs in through a private in-app WebView and stores the resulting Discord user-session token in an AES-GCM file whose key is held by Android Keystore. Tokens are never placed in DataStore, BuildConfig, source control, or logs.

Discord prohibits automating normal user accounts and warns that self-bot behavior may lead to account termination. Users must accept this risk before using the feature. See [Discord's automated user account policy](https://support.discord.com/hc/en-us/articles/115002192352-Automated-User-Accounts-Self-Bots) and the [Kizzy repository](https://github.com/dead8309/Kizzy).

## Build flavors and configuration

- `github`: includes the real Gateway transport and account connection screen. The public Discord application ID is `1526515771021328514`.
- `foss`: contains no Gateway transport and performs no Discord network operations. The settings row remains visible and reports **Unavailable in this build**.

No Discord client secret, OAuth redirect, private AAR, Gradle property, or GitHub Actions secret is required for the Kizzy-style transport. Release signing still uses the repository's existing optional `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, and `RELEASE_KEYSTORE_BASE64` secrets.

## Build and test

Use JDK 17 and run:

```bash
./gradlew :app:testGithubNightlyUnitTest
./gradlew :app:lintGithubNightly
./gradlew :app:assembleGithubNightly
./gradlew :app:testFossReleaseUnitTest
./gradlew :app:assembleFossRelease
```

The nightly APK is generated at `app/build/outputs/apk/github/nightly/app-github-nightly.apk`. GitHub Actions uploads it as `flow-discord-rpc-nightly-apk` with a SHA-256 checksum file.

## Manual verification

1. Install the GitHub nightly APK on Android 8.0 or newer.
2. Open the Discord Rich Presence screen and confirm the switch is off.
3. Select **Connect Discord account**, complete sign-in, and confirm the account name appears.
4. Enable sharing and play a regular video, Short, live stream, and music track.
5. Test pause, seek, resume, media switching, stop, disable, and unlink. Confirm Discord does not retain stale media.
6. Install the FOSS build and confirm the row reports unavailable and cannot be enabled.

## Troubleshooting and limitations

- **Sign-in never completes:** Discord may block or change embedded WebView login. Update Android System WebView, cancel, and retry.
- **Connection error:** Retry from the settings screen. If Discord invalidated the session, unlink and connect again.
- **Artwork missing:** Discord's external-asset endpoint may reject or delay a YouTube thumbnail; text and timestamps still update.
- **Presence disappears in the background:** Android may stop the process or network connection. Flow reconnects from the encrypted saved session only while the feature remains enabled.
- **Account risk:** The Gateway user-session method is unsupported by Discord. The only policy-safe direct Android alternative is Discord's private official Social SDK distribution.
- Discord can change Gateway payloads, WebView login, or external assets without notice.

Kizzy and Flow are licensed under GPL-3.0. The transport is a Flow-specific implementation inspired by Kizzy's Gateway design; Kizzy source files are not vendored.
