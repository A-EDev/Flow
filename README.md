# Flow - Modern YouTube Client

<div align="center">
<img src="Assets/logo.png" alt="Flow Logo" width="120" height="120">
</div>

![Status](https://img.shields.io/badge/Status-Under%20Development-orange?style=for-the-badge)
![Platform](https://img.shields.io/badge/Platform-Android-green?style=for-the-badge&logo=android)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-purple?style=for-the-badge&logo=kotlin)
![License](https://img.shields.io/badge/License-GPL%20v3.0-blue?style=for-the-badge)
![GitHub stars](https://img.shields.io/github/stars/A-EDev/Flow?style=for-the-badge)
![GitHub downloads](https://img.shields.io/github/downloads/A-EDev/Flow/total?style=for-the-badge)
![GitHub forks](https://img.shields.io/github/forks/A-EDev/Flow?style=for-the-badge)
![GitHub issues](https://img.shields.io/github/issues/A-EDev/Flow?style=for-the-badge)
![GitHub last commit](https://img.shields.io/github/last-commit/A-EDev/Flow/main?style=for-the-badge)
![GitHub contributors](https://img.shields.io/github/contributors/A-EDev/Flow?style=for-the-badge)

**A modern, feature-rich YouTube client for Android built with Jetpack Compose**

## Table of Contents

- [Development Status](#development-status)
- [Features](#features)
  - [Video Streaming](#video-streaming)
  - [Music Player](#music-player)
  - [Search & Discovery](#search--discovery)
  - [Themes & Customization](#themes--customization)
  - [User Interface](#user-interface)
  - [Library Management](#library-management)
  - [Privacy & Data](#privacy--data)
- [Architecture](#architecture)
  - [Tech Stack](#tech-stack)
  - [Project Structure](#project-structure)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Installation](#installation)
- [Releasing](#releasing)
- [Support & Donations](#support--donations)
- [Roadmap](#roadmap)
  - [High Priority](#high-priority)
  - [Core Features](#core-features)
  - [User Experience](#user-experience)
  - [Technical Improvements](#technical-improvements)
  - [Future Ideas](#future-ideas)
- [Contributing](#contributing)
- [Acknowledgments](#acknowledgments)
- [License](#license)
- [Contact & Support](#contact--support)
- [Troubleshooting](#troubleshooting)
- [Star History](#star-history)

---

<a id="development-status"></a>
## ⚠️ Development Status

**Flow is currently under active development.** Please note:

- 🐛 Some features may be incomplete or unstable
- 📊 Still optimizing performance and user experience

---

<a id="features"></a>
## ✨ Features

<a id="video-streaming"></a>
### 🎥 Video Streaming

- [x] High-quality video playback with ExoPlayer
- [x] Multiple quality options (Auto, 1080p, 720p, 480p, 360p)
- [x] Fullscreen support with landscape mode
- [x] Picture-in-Picture (PiP) mode
- [x] Background playback
- [x] Video chapters support
- [x] Playback speed control (0.25x - 2x)
- [x] Auto-play next video
- [ ] **Gesture Controls:** Left swipe for brightness, right swipe for volume
- [x] **Subtitles Support:** SRT/VTT formats with animated display
- [x] **Subtitles Customization:** Font size, color, background
- [x] **Enhanced Controls:** Quality and subtitles badges at top
- [x] **Professional UI:** Loading states, smooth animations, and visual feedback

<a id="music-player"></a>
### 🎵 Music Player

- [x] Dedicated music player with YouTube Music integration
- [x] Enhanced UI with album art and visualizations
- [x] Queue management (add, remove, reorder)
- [x] Shuffle and repeat modes
- [x] Persistent mini player across screens
- [x] Lyrics display support
- [x] Background audio playback
- [x] Smart track loading from popular artists

<a id="search--discovery"></a>
### 🔍 Search & Discovery

- [x] Fast and responsive search
- [x] Search suggestions and auto-complete
- [x] Trending videos by region
- [x] Category-based browsing
- [x] Search history management

<a id="themes--customization"></a>
### 🎨 Themes & Customization

- [x] **11+ Beautiful Color Themes:**
  - Light (Default light mode)
  - Dark (Standard dark mode)
  - OLED Black (Pure black for AMOLED)
  - Ocean Blue (Deep sea vibes)
  - Forest Green (Nature-inspired)
  - Sunset Orange (Warm tones)
  - Purple Nebula (Cosmic purple)
  - Midnight Black (Dark with cyan)
  - Rose Gold (Elegant pink)
  - Arctic Ice (Cool cyan)
  - Crimson Red (Bold red)
  - And more...
- [x] Smooth theme transitions
- [x] Persistent theme selection

<a id="user-interface"></a>
### 👤 User Interface

- [x] Modern Material Design 3
- [x] Smooth animations and transitions
- [x] Shimmer loading effects
- [x] Bottom navigation with smooth transitions
- [x] Swipe gestures support
- [x] Pull-to-refresh

<a id="library-management"></a>
### 📚 Library Management

- [x] Watch history tracking
- [x] Favorites/Bookmarks
- [x] Playlists creation and management
- [x] Continue watching section
- [x] Watch later queue
- [x] Shorts Bookmarks

<a id="privacy--data"></a>
### 🔒 Privacy & Data

- [x] Clear watch history
- [x] Clear search history
- [x] Export data backup
- [x] Import data restore
- [x] No Google account required
- [x] No ads or tracking

---

<a id="architecture"></a>
## 🏗️ Architecture

**Flow** follows clean architecture principles with a modular, component-based structure built entirely with **Jetpack Compose** and **Kotlin**.

<a id="tech-stack"></a>
### Tech Stack

- **Language:** Kotlin 100%
- **UI Framework:** Jetpack Compose (Material Design 3)
- **Architecture Pattern:** MVVM (Model-View-ViewModel)
- **Video Player:** ExoPlayer (Media3)
- **Data Extraction:** NewPipeExtractor
- **Async Operations:** Kotlin Coroutines & StateFlow
- **Local Storage:** DataStore Preferences
- **Image Loading:** Coil
- **Navigation:** Jetpack Compose Navigation Components

<a id="project-structure"></a>
### Project Structure

The app is organized into clear, focused modules for maintainability and scalability

---

<a id="getting-started"></a>
## 🚀 Getting Started

<a id="prerequisites"></a>
### Prerequisites

- Android Studio Hedgehog or later
- JDK 17+
- Android SDK 24+

<a id="installation"></a>
### Installation

1. **Clone the repository**

   ```bash
   git clone https://github.com/A-EDev/Flow.git
   cd Flow
   ```

2. **Open in Android Studio**
   - Open Android Studio
   - Select "Open an existing project"
   - Navigate to the project directory

3. **Sync Gradle**
   - Android Studio will automatically sync Gradle
   - Wait for dependencies to download

4. **Run the app**
   - Connect an Android device or start an emulator
   - Click the "Run" button or press Shift+F10

---

<a id="releasing"></a>
## 📦 Releasing

*Release instructions will be added soon.*

---

<a id="support--donations"></a>
## 💰 Support & Donations

If you find Flow useful and would like to support its development, you can donate via the following addresses:
- **Bitcoin (BTC):** `bc1qgkmtxvzvysmtpfazqfls93jw6k4gjx0xmrtvn8`
- **USDT:** `0xFbac6f464fec7fe458e31897a142ba45b305b7e` (Ethereum Network)
- **Ethereum (ETH):** `0xFbac6f464fec7fe458e31897a142ba45b305b7e`

Your support helps keep the project alive and motivates me to add more features!

---

<a id="roadmap"></a>
## 🗺️ Roadmap

<a id="high-priority"></a>
### 🔴 High Priority

- [x] **YouTube Algorithm Integration**
  - [x] Implement recommendation system
  - [x] Personalized home feed
  - [x] Related videos suggestions
  - [x] Watch history-based recommendations
  - [x] Trending videos integration
  - [x] Improve algorithm accuracy
  - [x] Implement Neural Engine that improves recommendations over time based on user behavior, it's privacy-focused and stores data locally.
  - [x] User controls for algorithm tuning (reset, feedback)
  - [ ] Add interest metrics visualization for users to see their moods, interests and how they evolve over time. (Bubble chart, line graph, etc.)

- [x] **Shorts Support**
  - [x] Shorts feed UI
  - [x] Vertical swipe navigation
  - [x] Shorts player optimization
  - [x] Shorts creation date/view count
  - [x] Shorts bookmarking
  - [x] Shorts Gesture Controls: Double-tap to like, Press and hold to speed

- [x] **Channel Screen**
  - [x] Complete channel page layout
  - [x] Channel videos grid
  - [x] Channel playlists
  - [x] Channel Shorts
  - [x] Channel about section
  - [x] Subscribe functionality

- [x] **Remove Placeholder Data**
  - [x] Replace dummy trending data
  - [x] Remove mock search results
  - [x] Fix static playlist data
  - [x] Real-time data fetching everywhere

<a id="core-features"></a>
### 🟡 Core Features

- [x] **Notification System**
  - [x] Push notifications setup
  - [x] New video notifications
  - [x] Download complete notifications
  - [x] Playback controls in notification.(Music)
  - [x] Notification settings

- [x] **Download Manager**
  - [x] Video download functionality
  - [x] Download queue management
  - [x] Quality selection for downloads
  - [x] Downloaded videos library

- [x] **Comments System**
  - [x] Display video comments
  - [x] Comment threads/replies
  - [x] Sort comments (top/newest)
  - [x] Comment engagement (likes display)
  - [x] Comment posting (future)

- [x] **Social Features**
  - [x] Share videos functionality
  - [x] Create shareable playlists
  - [x] Export watch statistics
  - [x] Follow favorite channels and artists

<a id="user-experience"></a>
### 🟢 User Experience

- [x] **Search Enhancements**
  - [x] Search filters (date, duration, quality)
  - [x] Advanced search operators
  - [x] Voice search support

- [x] **Playback Features**
  - [x] Resume playback from last position
  - [x] Watch together (sync viewing)
  - [x] Live stream support
  - [x] DVR controls for live streams

- [ ] **Accessibility**
  - [ ] Screen reader optimization
  - [ ] High contrast mode
  - [ ] Voice commands

<a id="technical-improvements"></a>
### 🔵 Technical Improvements

- [x] **Performance Optimization**
  - [x] Implement pagination properly
  - [x] Optimize image loading
  - [x] Reduce memory usage
  - [x] Background task optimization

- [ ] **Testing**
  - [ ] Unit tests coverage (80%+)
  - [ ] Integration tests
  - [ ] UI tests with Compose Testing
  - [ ] Performance benchmarks

- [x] **Architecture**
  - [x] Migrate to Hilt/Dagger for DI
  - [x] Implement Room for local database
  - [x] Add offline mode support
  - [x] Implement proper error handling

<a id="future-ideas"></a>
### 🟣 Future Ideas

- [ ] Chromecast support
- [ ] Android TV version
- [ ] Tablet-optimized UI
- [ ] Wear OS companion app
- [ ] Community playlists
- [ ] Import YouTube subscriptions
- [ ] Sponsorblock integration
- [ ] Picture-in-Picture enhancements

---

<a id="contributing"></a>
## 🤝 Contributing

We welcome contributions from everyone! Please read our [Contributing Guidelines](CONTRIBUTING.md) for detailed information on how to contribute to Flow.

### Ways to Contribute

- 🐛 Report bugs via [Issues](https://github.com/A-EDev/Flow/issues)
- 💡 Suggest features or improvements
- 📚 Improve documentation
- 🚀 Submit pull requests

### Quick Start

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

For more details, see [CONTRIBUTING.md](CONTRIBUTING.md).

---

<a id="acknowledgments"></a>
## 🙏 Acknowledgments

- **[NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor)** - Powerful library for YouTube data extraction
- **[MetroList](https://github.com/mostafafalahagamy/Metrolist)** - Inspiration for Hybrid InnerTube/NewPipeExtractor approach for Music fetching
- **[ExoPlayer](https://github.com/google/ExoPlayer)** - Professional-grade media player
- **[Jetpack Compose](https://developer.android.com/jetpack/compose)** - Modern Android UI toolkit
- **[Material Design 3](https://material.io/design)** - Beautiful design system by Google

---

<a id="license"></a>
## 📄 License

**Flow** is Free Software: You can use, study share and improve it at your will. 
It is distributed under the **GNU General Public License v3 (GPLv3)**.

Copyright (c) 2025-2026 A-EDev

> 🚨 **For Developers:**
> This license requires that any project using Flow's source code (including the `FlowNeuroEngine` algorithm) must also be **Open Source** under the GPLv3 license. You may not use this code in a proprietary or closed-source application.

<a id="contact--support"></a>
## 📞 Contact & Support

- **Developer:** A-EDev
- **GitHub:** [@A-EDev](https://github.com/A-EDev)
- **Issues:** [Report a bug](https://github.com/A-EDev/Flow/issues)

---

<a id="troubleshooting"></a>
## 🛠️ Troubleshooting

### Installation Issues

**"App not installed" or "Package conflict" error**

If you see an error like "Installing the new update has conflict of package may be due to...signing key", it means the signature of the new version doesn't match the one currently installed on your device.

**Solution:**
1. Uninstall the existing version of the app.
2. Install the new version.

*Note: This will clear your app data (settings, history, etc.). We are working on a backup/restore feature to mitigate this in the future.*

---

<a id="star-history"></a>
## 🌟 Star History

If you find this project useful, please consider giving it a star! 🌟

---

## 📚 Additional Resources

- [Code of Conduct](CODE_OF_CONDUCT.md)
- [Security Policy](SECURITY.md)
- [License](LICENSE)

---

<div align="center">

Made with ❤️ by A-EDev

</div>
