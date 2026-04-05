<div align="center">
  <img src="Assets/logo.png" alt="Flow Logo" width="140" height="140">
  <br><br>
  
  <div align="center">
<img src="https://img.shields.io/badge/Status-Active_Development-success?style=for-the-badge&logo=github-actions">
<br>
<!-- Downloads & Version -->
<a href="https://github.com/A-EDev/Flow/releases">
  <img src="https://img.shields.io/github/downloads/A-EDev/Flow/total?style=for-the-badge&color=orange&logo=github&label=Downloads">
</a>
<a href="https://github.com/A-EDev/Flow/releases">
  <img src="https://img.shields.io/github/v/release/A-EDev/Flow?style=for-the-badge&color=crimson&label=Latest%20Version">
</a>

<br>

<!-- Tech Stack -->
<img src="https://img.shields.io/badge/Platform-Android_8.0+-3DDC84?style=for-the-badge&logo=android&logoColor=white">
<img src="https://img.shields.io/badge/Kotlin-100%25-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white">
<img src="https://img.shields.io/badge/Compose-Material_3-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white">

<br>

<!-- Community & License -->
<a href="https://www.reddit.com/r/Flow_Official/">
  <img src="https://img.shields.io/badge/Reddit-r%2FFlow__Official-FF4500?style=for-the-badge&logo=reddit&logoColor=white">
</a>
<img src="https://img.shields.io/github/stars/A-EDev/Flow?style=for-the-badge&logo=star&color=gold">
<img src="https://img.shields.io/github/forks/A-EDev/Flow?style=for-the-badge&logo=git&color=silver">
<a href="LICENSE">
  <img src="https://img.shields.io/badge/License-GPL_v3.0-blue?style=for-the-badge&logo=gnu-bash&logoColor=white">
</a>
<img src="https://img.shields.io/github/last-commit/A-EDev/Flow?style=for-the-badge&color=red">

</div>

  <br><br>
  
  <h3>A privacy-respecting YouTube and YouTube Music client for Android with a local recommendation engine.</h3>
  <p>
    Flow is a YouTube client built with Jetpack Compose and Material 3.<br>
    It includes FlowNeuro, a recommendation engine that runs entirely on your device — no accounts, no tracking, no data leaves your phone.
  </p>
  
  <p>
    <a href="https://github.com/A-EDev/Flow/releases"><b>Download APK</b></a> · 
    <a href="https://github.com/A-EDev/Flow/wiki"><b>Documentation</b></a> · 
    <a href="https://www.reddit.com/r/Flow_Official/"><b>Subreddit</b></a> · 
    <a href="#support--donations"><b>Donate</b></a> .
    <a href="#translate"><b>Translations</b></a>
  </p>
</div>

---

## Why Flow?

Most open-source YouTube clients give you playback but no way to discover new content. You either use the official app and get tracked, or you use an alternative and lose recommendations entirely.

Flow gives you both. The recommendation engine learns what you like by analyzing your watch behavior locally. It never leaves your devices. You can inspect everything it knows about you, adjust it, or wipe it at any time.

---

## Features

### Video
- High-quality playback via ExoPlayer (Media3) with resolution switching (1080p, 720p, 480p, 360p)
- SponsorBlock — automatically skips sponsors, intros, outros, and filler
- DeArrow — replaces clickbait thumbnails and titles with community-sourced alternatives
- Return Youtube Dislike (RYD)
- Background playback — listen to audio with the screen off
- Picture-in-Picture — keep watching while using other apps
- Casting to smart TVs and streaming devices
- Playback speed control (0.25x to 2x)
- Video chapters with seek jumping
- Gesture controls for brightness, volume, and seeking
- Subtitles with customizable font size, color, and background
- Downloads with VP9, AV1, and standard format support
- Resume playback from where you left off

### Music
- Dedicated music player with album art and audio visualizations
- Queue management with add, remove, and reorder
- Shuffle and repeat (single/all)
- Persistent mini player across the app
- Synchronized lyrics display
- Fetches tracks from YouTube Music

### Recommendations (FlowNeuro Engine)
- Runs 100% on-device — no server, no telemetry, no account needed
- Learns from what you watch, skip, like, dislike, search for, and how long you watch
- Distinguishes weekday and weekend patterns, morning and night preferences
- Detects when you're getting bored of a topic and mixes in new content
- Prevents your feed from collapsing into the same 2-3 topics
- Surfaces related videos from your recent watches to create natural topic transitions
- Uses engagement signals (like-to-view ratios) to filter out low-quality content
- Full transparency dashboard — see what the algorithm knows and why it recommended something
- Export/import your entire recommendation profile as a file

### Library
- Local watch history
- Favorites and custom playlists
- Shorts feed with bookmarking
- Continue watching shelf
- Subscription management with cached feeds

### Privacy
- No Google account required
- No ads, analytics, or tracking
- All data stored locally on your device
- Import subscriptions and history from NewPipe
- Export or delete everything at any time

### Appearance
- 11 themes: Light, Dark, OLED Black, Ocean Blue, Forest Green, Sunset Orange, Purple Nebula, Midnight Black, Rose Gold, Arctic Ice, Crimson Red
- Built entirely with Jetpack Compose and Material 3

---

## Screenshots

<div align="center">
  <table>
    <tr>
      <td align="center"><b>Home Feed</b><br><img src="Assets/Home.jpeg" width="240"></td>
      <td align="center"><b>Video Player</b><br><img src="Assets/VideoPlayer.jpeg" width="240"></td>
      <td align="center"><b>Personality Screen</b><br><img src="Assets/Personality.jpeg" width="240"></td>
    </tr>
    <tr>
      <td align="center"><b>Music Player</b><br><img src="Assets/MusicPlayer.jpeg" width="240"></td>
      <td align="center"><b>Music Hub</b><br><img src="Assets/Music.jpeg" width="240"></td>
      <td align="center"><b>Your Library</b><br><img src="Assets/Library.jpeg" width="240"></td>
    </tr>
    <tr>
      <td align="center"><b>Shorts</b><br><img src="Assets/Shorts.jpeg" width="240"></td>
      <td align="center"><b>Subscriptions</b><br><img src="Assets/Subscriptions.jpeg" width="240"></td>
      <td align="center"><b>Channel View</b><br><img src="Assets/Channel.jpeg" width="240"></td>
    </tr>
    <tr>
      <td align="center"><b>Artist Page</b><br><img src="Assets/Artist.jpeg" width="240"></td>
      <td align="center"></td>
      <td align="center"></td>
    </tr>
  </table>
</div>

---
## Download

### Stable Release

<div align="center">
  <table border="0">
    <tr>
      <td align="center" style="vertical-align: middle; padding: 10px;">
        <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/A-EDev/Flow/">
          <img src="https://github.com/ImranR98/Obtainium/blob/main/assets/graphics/badge_obtainium.png" alt="Obtainium" height="60">
        </a>
      </td>
      <td align="center" style="vertical-align: middle; padding: 10px;">
        <a href="https://github.com/A-EDev/Flow/releases/latest">
          <img src="https://github.com/machiav3lli/oandbackupx/blob/034b226cea5c1b30eb4f6a6f313e4dadcbb0ece4/badge_github.png" alt="GitHub" height="90">
        </a>
      </td>
      <td align="center" style="vertical-align: middle; padding: 10px;">
        <a href="https://apt.izzysoft.de/packages/io.github.aedev.flow">
          <img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" alt="IzzyOnDroid" height="90">
        </a>
      </td>
    </tr>
  </table>
</div>

### Nightly / Debug Build
> ⚠️ Nightly builds are unstable and may contain bugs. Use at your own risk.

<div align="center">
  <a href="https://nightly.link/A-EDev/Flow/workflows/build/main/flow-nightly-apk.zip">
    <img src="https://github.com/machiav3lli/oandbackupx/blob/034b226cea5c1b30eb4f6a6f313e4dadcbb0ece4/badge_github.png" alt="Download from GitHub" height="75">
  </a>
  <p><b>No GitHub account required</b> — powered by <a href="https://nightly.link">nightly.link</a></p>
</div>

### Requirements 
**Minimum Requirement:** Android 8.0+

---

<a id="support--donations"></a>
## 💰 Support Development

Flow is free and open-source. I am an independent developer without banking access, so I rely on **Crypto** to keep the lights on.

**If you enjoy the app, please consider supporting:**

| Coin | Network | Address |
| :--- | :--- | :--- |
| **USDT** | TRC20 (Tron) | `TRz7VDrTWwCLCfQmYBEJakqcZgbFNWfUMP` |
| **Bitcoin** | BTC | `bc1qgmkkxxvzvsymtpfazqfl93jw6k4jgy0xmrtnv8` |
| **Ethereum** | ERC-20 | `0xfbac6f464fec7fe458e318971a42ba45b305b70e` |
| **Solana** | SOL | `7b3SLgiVPb8qQUvERSPGRWoFoiGEDvkFuY98M1GEngug` |

*Your support helps me maintain the project and add new features!*

---

## 🙏 Acknowledgments

Flow stands on the shoulders of giants. Special thanks to:

*   **[NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor):** The backbone of our data extraction.
*   **[NewPipe](https://github.com/TeamNewPipe/NewPipe):** For inspiration from their solid foundation for YouTube data handling.
*   **[MetroList](https://github.com/MetrolistGroup/Metrolist):** Inspiration for the Hybrid Music fetching approach, Lyrics handling and some icons design references.
*   **[LibreTube](https://github.com/LibreTube/LibreTube):** Inspiration for SponsorBlock and DeArrow handling and some icons design references.
*   **[ExoPlayer](https://github.com/google/ExoPlayer):** The gold standard for Android media playback.
*   **[Jetpack Compose](https://developer.android.com/jetpack/compose):** For enabling the beautiful, modern UI.
*   **[Material Design 3](https://m3.material.io/):** For the design system and guidelines.

---

<div align="center">

<a id="translate"></a>
## Translations
Help translate Flow into your language! 

[![Translation status](https://hosted.weblate.org/widget/flow/287x66-grey.png?threshold=0)](https://hosted.weblate.org/engage/flow/)

[![Translation status](https://hosted.weblate.org/widget/flow/horizontal-auto.svg)](https://hosted.weblate.org/engage/flow/)
</div>

---

## 📄 License & Copyright

**Flow** is Free Software: You can use, study, share, and improve it at your will.
It is distributed under the **GNU General Public License v3 (GPLv3)**.

**Copyright © 2025-2026 A-EDev**

> 🚨 **For Developers:**
> This license requires that any project using Flow's source code (including the `FlowNeuroEngine` algorithm) must also be **Open Source** under the GPLv3 license. You may not use this code in a proprietary or closed-source application.

---

<div align="center">
  <sub>Made with ❤️ by A-EDev</sub>
</div>
