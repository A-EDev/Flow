# 🎬 Flow - Modern YouTube Client

![Status](https://img.shields.io/badge/Status-Under%20Development-orange?style=for-the-badge)
![Platform](https://img.shields.io/badge/Platform-Android-green?style=for-the-badge&logo=android)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-purple?style=for-the-badge&logo=kotlin)
![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)

**A modern, feature-rich YouTube client for Android built with Jetpack Compose**

📖 [Features](#features) • 💾 [Installation](#installation) • 🤝 [Contributing](#contributing) • 🗺️ [Roadmap](#roadmap)

---

## ⚠️ Development Status

**Flow is currently under active development.** Please note:

- 🚧 Contains placeholder data in various sections
- 🐛 Some features may be incomplete or unstable
- 📊 Still optimizing performance and user experience

---

## ✨ Features

### 🎥 Video Streaming

- [x] High-quality video playback with ExoPlayer
- [x] Multiple quality options (Auto, 1080p, 720p, 480p, 360p)
- [x] Fullscreen support with landscape mode
- [x] Picture-in-Picture (PiP) mode
- [x] Background playback
- [ ] Video chapters support
- [ ] Playback speed control (0.25x - 2x)
- [ ] Auto-play next video
- [x] **Gesture Controls:** Left swipe for brightness, right swipe for volume
- [ ] **Subtitle Support:** SRT/VTT formats with animated display
- [x] **Enhanced Controls:** Quality and subtitle badges at top
- [x] **Professional UI:** Loading states, smooth animations, and visual feedback

### 🎵 Music Player

- [x] Dedicated music player with YouTube Music integration
- [x] Enhanced UI with album art and visualizations
- [x] Queue management (add, remove, reorder)
- [x] Shuffle and repeat modes
- [x] Persistent mini player across screens
- [ ] Lyrics display support
- [x] Background audio playback
- [x] Smart track loading from popular artists

### 🔍 Search & Discovery

- [x] Fast and responsive search
- [x] Search suggestions and auto-complete
- [x] Trending videos by region
- [x] Category-based browsing
- [x] Search history management

### 🎨 Themes & Customization

- [x] **11 Beautiful Color Themes:**
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

## 🎨 Customization

### Changing Theme

Navigate to Settings → Appearance → Theme and choose from:

- **Light**: Soft off-white background with vibrant accents
- **Dark**: Deep charcoal background for comfortable viewing
- **OLED Black**: Pure black background for maximum battery savings

### Accent Colors

The app uses a beautiful indigo/purple gradient as the primary accent. You can modify colors in:

```kotlin
app/src/main/java/com/flow/youtube/ui/theme/Color.kt
```

---

## 🗺️ Roadmap

### 🔥 High Priority

- [x] **YouTube Algorithm Integration**
  - [x] Implement recommendation system
  - [x] Personalized home feed
  - [x] Related videos suggestions
  - [x] Watch history-based recommendations

- [ ] **Shorts Support**
  - [ ] Shorts feed UI
  - [ ] Vertical swipe navigation
  - [ ] Shorts player optimization
  - [ ] Shorts creation date/view count

- [ ] **Channel Screen**
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
  - [ ] Push notifications setup
  - [ ] New video notifications
  - [ ] Download complete notifications
  - [ ] Playback controls in notification

- [ ] **Download Manager**
  - [ ] Video download functionality
  - [ ] Download queue management
  - [ ] Quality selection for downloads
  - [ ] Downloaded videos library

- [ ] **Comments System**
  - [ ] Display video comments
  - [ ] Comment threads/replies
  - [ ] Sort comments (top/newest)
  - [ ] Comment engagement (likes/dislikes display)

- [ ] **Social Features**
  - [ ] Share videos functionality
  - [ ] Create shareable playlists
  - [ ] Export watch statistics
  - [ ] Social media integration

### 🎯 User Experience

- [ ] **Search Enhancements**
  - [x] Search filters (date, duration, quality)
  - [x] Advanced search operators
  - [ ] Voice search support
  - [ ] Image/QR code search

- [ ] **Playback Features**
  - [x] Resume playback from last position
  - [ ] Watch together (sync viewing)
  - [ ] Live stream support
  - [ ] DVR controls for live streams
  - [ ] Subtitle/closed caption support

- [ ] **Accessibility**
  - [ ] Screen reader optimization
  - [ ] High contrast mode
  - [ ] Font size options
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

## 📄 License

This project is licensed under the MIT License - see below for details:

```text
MIT License

Copyright (c) 2025 A-EDev

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

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

## ⭐ Star History

If you find this project useful, please consider giving it a star! ⭐

---
<div align="center">

Made with ❤️ by A-EDev

</div>
