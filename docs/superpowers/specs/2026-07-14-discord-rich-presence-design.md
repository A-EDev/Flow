# Discord Rich Presence Design

## Goal

Expose an opt-in Discord Rich Presence setting and publish current Flow playback through Discord's official Social SDK on supported GitHub builds, while keeping FOSS builds SDK-free and honest about unavailability.

## Architecture

- `DiscordPreferences` owns only the enabled flag and the last non-sensitive account label.
- `DiscordPlaybackSource` converts the existing video, Shorts, and music managers into immutable snapshots. Active playback priority is Shorts, then regular video/live, then music.
- `DiscordPresenceService` owns transport connection, token storage, coordinator lifetime, Activity attachment, enable/disable, link, retry, unlink, and shutdown.
- Common code depends on `DiscordPresenceTransport`. Each flavor supplies `DiscordPresenceTransportFactory`: FOSS always returns the fail-closed adapter; GitHub returns the official adapter only when both an application ID and the private Social SDK AAR were supplied at build time.
- The GitHub adapter calls a small C++20 JNI bridge linked through the official AAR Prefab package. The bridge uses only documented Social SDK APIs and runs `discordpp::RunCallbacks` on a bounded callback thread.

## User experience

The row is indexed in Settings search and appears in Content & Playback for both flavors. Its subtitle is derived from availability, enabled state, connection state, account label, and errors. The dedicated screen contains the opt-in switch, status, connect/retry or disconnect action, account label, and playback-data privacy copy. Enabling is rejected when the SDK is unavailable or no account is linked.

## Playback and lifecycle

Snapshots are sampled at five-second intervals while enabled and also react immediately to player and metadata changes. This retains the existing policy's deduplication and seek correction without high-frequency SDK calls. Pause, stop, media switches, disable, unlink, and shutdown clear presence. `MainActivity` is held weakly by the service and attached to the SDK only between `onStart` and `onStop`.

## Official SDK requirements

Android 7.0+ is supported. Mobile Rich Presence requires account linking. Linking uses SDK 1.5+ OAuth2 PKCE with `openid sdk.social_layer_presence`, a public-client Discord application, and the `discord-<APP_ID>:/authorize/callback` deep link. The SDK is a private Developer Portal download named `discord_partner_sdk.aar`, not a public Maven dependency. The adapter uses `Authorize`, `GetToken`, `RefreshToken`, `UpdateToken`, `Connect`, `FetchCurrentUser`, `RevokeToken`, `UpdateRichPresence`, `ClearRichPresence`, `Disconnect`, and `RunCallbacks`.

## Failure behavior

Missing AAR or application ID produces `Unavailable in this build`; it never presents an enabled state. Transport failures preserve the opt-in preference only when retry is meaningful, expose a concise error, and clear stale presence. Invalid stored tokens are deleted and return the UI to Not connected.

## Verification

Focused JVM tests cover preferences, settings derivation, snapshot selection/mapping, policy, coordinator clearing/failures, FOSS behavior, and the SDK adapter through a fake bridge. GitHub Actions runs unit tests before lint and packaging, uploads the exact nightly APK with missing-file errors enabled, and records the APK digest.
