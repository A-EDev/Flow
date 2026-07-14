# Discord Rich Presence Design

## Goal

Expose an opt-in Discord Rich Presence setting and publish current Flow playback through a Kizzy-style Discord Gateway transport in GitHub builds, while keeping FOSS builds network-free and honest about unavailability.

## Architecture

- `DiscordPreferences` owns only the enabled flag and the last non-sensitive account label.
- `DiscordPlaybackSource` converts the existing video, Shorts, and music managers into immutable snapshots. Active playback priority is Shorts, then regular video/live, then music.
- `DiscordPresenceRuntime` owns transport connection, token storage, coordinator lifetime, Activity attachment, enable/disable, link, retry, unlink, and shutdown.
- Common code depends on `DiscordPresenceTransport`. Each flavor supplies `DiscordPresenceTransportFactory`: FOSS returns the fail-closed adapter; GitHub returns the Gateway adapter.
- The GitHub adapter implements Gateway identify, heartbeat, presence update, clearing, account state, and external-image resolution behind the transport seam. It is inspired by Kizzy without vendoring Kizzy source files.

## User experience

The row is indexed in Settings search and appears in Content & Playback for both flavors. Its subtitle is derived from availability, enabled state, connection state, account label, and errors. The dedicated screen contains the opt-in switch, status, connect/retry or disconnect action, account label, playback-data privacy copy, and an explicit unsupported-client warning. Enabling is rejected when the transport is unavailable.

## Playback and lifecycle

Snapshots are sampled at five-second intervals and also react immediately to player and metadata changes. This retains the existing policy's deduplication and seek correction without high-frequency Gateway calls. Pause, stop, media switches, disable, unlink, and shutdown clear presence. `MainActivity` is held weakly by the runtime and detached on destruction.

## Gateway requirements and risk

The public Discord application ID is compiled into the GitHub flavor; no client secret or private artifact is used. Linking uses an embedded Discord login and a Discord user-session token encrypted with Android Keystore. This is not OAuth or an official Discord-supported mobile integration. Discord prohibits self-bots and may restrict or terminate accounts, so the risk is disclosed in the UI.

## Failure behavior

The FOSS adapter produces `Unavailable in this build`; it never presents an enabled state. Transport failures preserve the opt-in preference when retry is meaningful, expose a concise error, and clear stale presence. Unlink deletes the encrypted session and non-sensitive account label.

## Verification

Focused JVM tests cover preferences, settings derivation, snapshot selection/mapping, policy, coordinator clearing/failures, FOSS behavior, and Gateway payload behavior. GitHub Actions runs unit tests before lint and packaging, uploads the exact nightly APK with missing-file errors enabled, and records the APK digest.
