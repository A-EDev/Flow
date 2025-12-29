# 🎬 Flow - Modern YouTube Client

![Status](https://img.shields.io/badge/Status-Under%20Development-orange?style=for-the-badge)
![Platform](https://img.shields.io/badge/Platform-Android-green?style=for-the-badge&logo=android)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-purple?style=for-the-badge&logo=kotlin)
![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)
![GitHub stars](https://img.shields.io/github/stars/A-EDev/Flow?style=for-the-badge)
![GitHub forks](https://img.shields.io/github/forks/A-EDev/Flow?style=for-the-badge)
![GitHub last commit](https://img.shields.io/github/last-commit/A-EDev/Flow/main?style=for-the-badge)

**A modern, feature-rich YouTube client for Android built with Jetpack Compose**

📖 [Features](#features) • 💾 [Installation](#installation) • 🤝 [Contributing](#contributing) • 🗺️ [Roadmap](#roadmap)

---

## ⚠️ Development Status

**Flow is currently under active development.** Please note:

- 🐛 Some features may be incomplete or unstable
- 📊 Still optimizing performance and user experience

---

## 📊 Stats
![GitHub stars](https://img.shields.io/github/stars/A-EDev/Flow?style=for-the-badge)
![GitHub downloads](https://img.shields.io/github/downloads/A-EDev/Flow/total?style=for-the-badge)
![GitHub issues](https://img.shields.io/github/issues/A-EDev/Flow?style=for-the-badge)
![GitHub repo size](https://img.shields.io/github/repo-size/A-EDev/Flow?style=for-the-badge)
![GitHub contributors](https://img.shields.io/github/contributors/A-EDev/Flow?style=for-the-badge)

---

## ✨ Features

### 🎥 Video Streaming

- [x] High-quality video playback with ExoPlayer
- [x] Multiple quality options (Auto, 1080p, 720p, 480p, 360p)
- [x] Fullscreen support with landscape mode
- [x] Picture-in-Picture (PiP) mode
- [x] Background playback
- [x] Video chapters support
- [x] Playback speed control (0.25x - 2x)
- [x] Auto-play next video
- [x] **Gesture Controls:** Left swipe for brightness, right swipe for volume
- [x] **Subtitle Support:** SRT/VTT formats with animated display
- [x] **Subtitle Customization:** Font size, color, background
- [x] **Enhanced Controls:** Quality and subtitle badges at top
- [x] **Professional UI:** Loading states, smooth animations, and visual feedback

### 🎵 Music Player

- [x] Dedicated music player with YouTube Music integration
- [x] Enhanced UI with album art and visualizations
- [x] Queue management (add, remove, reorder)
- [x] Shuffle and repeat modes
- [x] Persistent mini player across screens
- [x] Lyrics display support
- [x] Background audio playback
- [x] Smart track loading from popular artists

### 🔍 Search & Discovery

- [x] Fast and responsive search
- [x] Search suggestions and auto-complete
- [x] Trending videos by region
- [x] Category-based browsing
- [x] Search history management

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

### 📱 User Interface

- [x] Modern Material Design 3
- [x] Smooth animations and transitions
- [x] Shimmer loading effects
- [x] Bottom navigation with smooth transitions
- [x] Swipe gestures support
- [x] Pull-to-refresh

### 📚 Library Management

- [x] Watch history tracking
- [x] Favorites/Bookmarks
- [x] Playlists creation and management
- [x] Continue watching section
- [x] Watch later queue
- [x] Shorts Bookmarks

### 🔐 Privacy & Data

- [x] Clear watch history
- [x] Clear search history
- [ ] Export data backup
- [ ] Import data restore
- [x] No Google account required
- [x] No ads or tracking

---

## 🏗️ Architecture

**Flow** follows clean architecture principles with a modular, component-based structure built entirely with **Jetpack Compose** and **Kotlin**.

### Tech Stack

- **Language:** Kotlin 100%
- **UI Framework:** Jetpack Compose (Material Design 3)
- **Architecture Pattern:** MVVM (Model-View-ViewModel)
- **Video Player:** ExoPlayer (Media3)
- **Data Extraction:** NewPipeExtractor
- **Async Operations:** Kotlin Coroutines & StateFlow
- **Local Storage:** DataStore Preferences
- **Image Loading:** Coil
- **Navigation:** Jetpack Navigation Compose

### Project Structure

The app is organized into clear, focused modules for maintainability and scalability

---

## 🚀 Getting Started

### Prerequisites

- Android Studio Hedgehog or later
- JDK 17
- Android SDK 24+

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

## 🗺️ Roadmap

### 🔥 High Priority

- [x] **YouTube Algorithm Integration**
  - [x] Implement recommendation system
  - [x] Personalized home feed
  - [x] Related videos suggestions
  - [x] Watch history-based recommendations

- [x] **Shorts Support**
  - [x] Shorts feed UI
  - [x] Vertical swipe navigation
  - [x] Shorts player optimization
  - [ ] Shorts creation date/view count

- [] **Channel Screen**
  - [x] Complete channel page layout
  - [x] Channel videos grid
  - [ ] Channel playlists
  - [x] Channel about section
  - [x] Subscribe functionality

- [x] **Remove Placeholder Data**
  - [x] Replace dummy trending data
  - [x] Remove mock search results
  - [x] Fix static playlist data
  - [x] Real-time data fetching everywhere

### 📱 Core Features

- [ ] **Notification System**
  - [x] Push notifications setup
  - [x] New video notifications
  - [x] Download complete notifications
  - [ ] Playback controls in notification

- [x] **Download Manager**
  - [x] Video download functionality
  - [x] Download queue management
  - [x] Quality selection for downloads
  - [x] Downloaded videos library

- [ ] **Comments System**
  - [x] Display video comments
  - [ ] Comment threads/replies
  - [x] Sort comments (top/newest)
  - [x] Comment engagement (likes display)

- [ ] **Social Features**
  - [x] Share videos functionality
  - [ ] Create shareable playlists
  - [ ] Export watch statistics
  - [x] Follow favorite channels and artists

### 🎯 User Experience

- [ ] **Search Enhancements**
  - [x] Search filters (date, duration, quality)
  - [x] Advanced search operators
  - [ ] Voice search support

- [ ] **Playback Features**
  - [x] Resume playback from last position
  - [ ] Watch together (sync viewing)
  - [ ] Live stream support
  - [ ] DVR controls for live streams

- [ ] **Accessibility**
  - [ ] Screen reader optimization
  - [ ] High contrast mode
  - [ ] Voice commands

### ⚙️ Technical Improvements

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

- [ ] **Architecture**
  - [ ] Migrate to Hilt/Dagger for DI
  - [ ] Implement Room for local database
  - [ ] Add offline mode support
  - [ ] Implement proper error handling

### 🌟 Future Ideas

- [ ] Chromecast support
- [ ] Android TV version
- [ ] Tablet-optimized UI
- [ ] Wear OS companion app
- [ ] Community playlists
- [ ] Import YouTube subscriptions
- [ ] Sponsorblock integration
- [ ] Picture-in-Picture enhancements

---

## 💖 Support & Donations

If you find Flow useful and would like to support its development, you can donate via the following addresses:

- **Bitcoin (BTC):** `bc1qgmkkxxvzvsymtpfazqfl93jw6k4jgy0xmrtnv8`
- **USDT :** `0xfbac6f464fec7fe458e318971a42ba45b305b70e` (Ethereum Network)
- **Ethereum (ETH):** `0xfbac6f464fec7fe458e318971a42ba45b305b70e`
- **Solana (SOL):** `7b3SLgiVPb8qQUvERSPGRWoFoiGEDvkFuY98M1GEngug`

Your support helps keep the project alive and motivates me to add more features!

---

## 🤝 Contributing

Contributions are welcome! Here's how you can help:

### Ways to Contribute

- 🐛 Report bugs via [Issues](https://github.com/A-EDev/Flow/issues)
- 💡 Suggest features or improvements
- 📝 Improve documentation
- 🔧 Submit pull requests

### Development Guidelines

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

### Code Style

- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Add comments for complex logic
- Write unit tests for new features
- Ensure all tests pass before submitting PR

---

## 🙏 Acknowledgments

- **[NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor)** - Powerful library for YouTube data extraction
- **[ExoPlayer](https://github.com/google/ExoPlayer)** - Professional-grade media player
- **[Jetpack Compose](https://developer.android.com/jetpack/compose)** - Modern Android UI toolkit
- **Material Design 3** - Beautiful design system by Google

---

## 📞 Contact & Support

- **Developer:** A-EDev
- **GitHub:** [@A-EDev](https://github.com/A-EDev)
- **Issues:** [Report a bug](https://github.com/A-EDev/Flow/issues)

---

## 🔧 Troubleshooting

### Installation Issues

**"App not installed" or "Package conflict" error**

If you see an error like "Installing the new update has conflict of package may be due to....signing key", it means the signature of the new version doesn't match the one currently installed on your device.

**Solution:**
1. Uninstall the existing version of the app.
2. Install the new version.

*Note: This will clear your app data (settings, history, etc.). We are working on a backup/restore feature to mitigate this in the future.*

---

## ⭐ Star History

If you find this project useful, please consider giving it a star! ⭐

---
<div align="center">

Made with ❤️ by A-EDev

</div>
