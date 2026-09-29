# Upstream merge playbook

Follow this when syncing the fork `seshuthota/Flow` with `A-EDev/Flow`. The 2026-09-29 sync is the reference: 192 upstream commits, the player modularisation, fork features ported rather than dropped, a device pass, and a push to `origin/main` at `0064e7e9`.

## Remotes

- `origin` is `https://github.com/seshuthota/Flow.git`. Push here only when the user asks.
- `upstream` is `https://github.com/A-EDev/Flow.git`. Never push here.
- Debug installs use `io.github.aedev.flow.debug` (`applicationIdSuffix = ".debug"`). They do not replace the release app.

## Before changing anything

1. `git fetch upstream`, then `git rev-list --left-right --count HEAD...upstream/main`.
2. Read `git status`. Do not start a merge on top of unrelated edits. Leave `next-plan.md` untracked and out of every sync commit.
3. Do not move the existing safety refs. `pre-upstream-merge`, `pushed-before-merge`, and `backup/pre-upstream-merge` are `2c052e58`, the fork tip from immediately before the 2026-09-29 merge. For a new sync, add a dated tag at the current tip, such as `pre-upstream-merge-YYYYMMDD`. `git merge --abort` only works before the merge commit exists.
4. Confirm the checked-out branch. The published sync is `origin/main`. Do not assume the current branch is `main`.
5. Do not bump the app version, edit locale `strings.xml` files, or regenerate the baseline profile for a sync. Do not block the sync on graphify. Run `graphify update .` after the merge lands only when the tool is installed and the user has not deferred it.

## How to resolve

Upstream rewrites the player and deletes the files fork features were written in. Choosing "ours" or "theirs" either drops a feature or leaves code that cannot compile. Port the fork behavior into the upstream file, then delete the old shell.

Compile after the conflict markers are gone. The 2026-09-29 merge had compile errors outside any conflict hunk.

Fork behavior that must still be reachable after the sync. Search for the symbol. Do not assume the old file survived.

| Behavior | Keep this |
| --- | --- |
| Clipboard-open | `clipboardLinkOpenEnabled`, the playback-settings row, `MainActivity.openClipboardVideoLinkIfPresent` |
| Timestamp links | `YouTubeLinkParser.startPositionMs`, `MainActivity.openLink`, route `player/{videoId}?startMs=` |
| Continue-watching dismissal | `shouldRestoreContinueWatching`, called from `PlaybackPresenceController.restoreLastWatchedSession`. Upstream's restore has no dismissal check. Removing the call brings the bug back and the build stays green. |
| PiP next and background audio | PiP params with actions set, and auto-PiP when leaving the app |
| On-device sponsor detection | `data/sponsordetection/` (upstream has none), `SponsorModelSettingsSection` on the Integrations screen, `overlaySponsorTimelineSegments` on the phone and TV players, `findActiveManualSponsorSegment`, `SegmentAwareLoadControl` |
| SponsorBlock category API | Upstream names (`SELF_PROMO`, `MUSIC_OFF_TOPIC`, `all`, `actionTypes`) plus the fork helpers `isWholeVideoAction` and `isMuteAction` |
| Category labels | Only `utils` `sponsorCategoryLabelRes`, using `SponsorBlockCategories` constants. Do not bring back `sponsorBlockCategoryLabelRes` in the theme file. |
| SponsorBlock fetch | The fork's typed fetch seam (`SponsorDetectionCoordinator` needs it) and upstream parse/serialize for the download store |
| Strings | One `name=` per string in `values/strings.xml`. A duplicate fails the resource merge. |

These files were deleted on purpose after their behavior was ported. Do not resurrect them to finish a conflict: `GlobalPlayerOverlay`, `PremiumControlsOverlay`, `VideoPlayerOverlays`, `SeekbarWithPreview`, `PlayerSettingsScreen`, `SponsorBlockSettingsScreen`.

`EnhancedPlayerManager` keeps a single `createTrackSelector(context, videoSizeCap)` init. Do not restore `AudioEffectsController.initialize`. Keep sponsor fields, consent observers, and `release()` teardown beside `buildScope.close()`. `createPipActionReceiver` takes `onPlay`, `onPause`, `onNext`, and `onBackgroundAudio`. It has no `onClose`.

## Validate, then commit

Run these, and keep going until they pass:

```bash
./gradlew ktlintCheck
./gradlew :app:testGithubDebugUnitTest :app:testFossDebugUnitTest
./gradlew :app:assembleGithubDebug :app:assembleFossDebug
./gradlew :app:compileGithubDebugAndroidTestKotlin
```

Use flavor-prefixed tasks. `--offline` is fine when the dependency cache is warm.

Commit the merge as `merge(upstream): bring in <span> of A-EDev/Flow main`, and name the fork features that were ported. A cleanup such as the label helper is its own commit, not a second merge commit. Do not commit `next-plan.md` or a scratch status note.

## Device pass

Install `app/build/outputs/apk/github/debug/app-github-arm64-v8a-debug.apk` with `adb install -r` on a physical arm64 device.

1. Launch. The home feed must render, with no fatal exception in logcat.
2. Resume a video. `dumpsys media_session` for `flow_video_session` must show `PLAYING` and a position that advances.
3. Sponsor UI: the **Review detection** chip and colored seek-bar marks.
4. Press Home. The Flow task must be `mode=pinned`, playback still `PLAYING`, and the PiP window must show background-audio, pause, and next. Do not tap next or background-audio unless the user asked. Those change the queue and the audio mode.
5. Timestamp link, from a cold start. Quote the whole remote command. Prefer a URL with `?t=` and no `&`, because the device shell backgrounds the command at `&`. Pin the package, or the YouTube app takes `ACTION_VIEW`:

```bash
adb shell am force-stop io.github.aedev.flow.debug
adb shell 'am start -a android.intent.action.VIEW -d "https://youtu.be/jNQXAC9IVRw?t=8" -p io.github.aedev.flow.debug'
```

Confirm the opened video and that playback is not still at 0. On a short clip at 1.5×, an 8-second start is near the end within a few seconds.
6. Open the Music tab and confirm the feed renders.
7. `adb shell input keyevent KEYCODE_MEDIA_PAUSE` before leaving. Do not tap Continue Watching's dismiss control unless the user asked. That writes their dismissal preference.

A phone pass does not cover the TV player or a manual sponsor skip. Say what was not exercised.

## Done

No `MERGE_HEAD`, no conflict markers, both flavors assembled, both unit-test suites green, and the device pass above. Update `origin` only after the user asks, and only `origin`. Report the commit range and what was not exercised.
