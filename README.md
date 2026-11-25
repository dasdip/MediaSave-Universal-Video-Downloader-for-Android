# MediaSave

**MediaSave** is a clean, open-source **Material You (Material Design 3)** Android app powered by **yt-dlp** and **FFmpeg**, built with **Kotlin** and **Jetpack Compose**.

Download videos and audio from **YouTube, Facebook, and 1000+ other supported sites** with a modern Android interface—choose video resolutions up to 4K, remux containers, transcode audio bitrates, and manage your saved media files.

---

<img width="1440" height="1080" alt="photo-collage png" src="https://github.com/user-attachments/assets/44f118f9-934f-4a6d-a11a-f92bb3c5915c" />


## Key Features

- **Material You (Material 3) Design**:
  - Automatic wallpaper dynamic color extraction on Android 12+ (`dynamicLightColorScheme` / `dynamicDarkColorScheme`).
  - System, Light, and Dark modes plus curated seed color palettes inside Settings.
- **Engine Management & Auto-Updates**:
  - Automatically checks and updates the `yt-dlp` runtime on app launch (`Stable` or `Nightly` channel selectable in Settings).
  - Bundles `FFmpeg` native libraries (`youtubedl-android`) and checks upstream module release status on startup.
- **Stream Inspection & Format Selection**:
  - Video + Audio mode with resolution selection (up to 4K when available) and container options (`MP4`, `MKV`, `WebM`).
  - Audio Only extraction with selectable bitrates (`Best`, `320 kbps`, `256 kbps`, `192 kbps`, `128 kbps`, `96 kbps`) and formats (`MP3`, `M4A`, `Opus`, `FLAC`).
  - Playlist selection, thumbnail embedding, and metadata tagging.
- **Background Service & Storage-Synced History**:
  - Foreground notification service with real-time percentage progress for downloading and FFmpeg post-processing.
  - Saves output files to `Downloads/MediaSave` via Android `MediaStore`.
  - Automatically prunes missing/externally deleted files from history on launch, with one-tap file opening, **Clear History**, and per-item **Delete from Storage**.
- **Per-ABI Split Release Builds**:
  - R8 code shrinking and ABI splits (`arm64-v8a`, `armeabi-v7a`, `x86_64`) keep release APKs compact (~50–60 MB).

---

## Tech Stack

- **Language & UI**: Kotlin, Jetpack Compose, Material 3
- **Media Engines**: `io.github.junkfood02.youtubedl-android` (`library:0.18.1`, `ffmpeg:0.18.1`)
- **Storage & Preferences**: Jetpack DataStore Preferences, Android MediaStore
- **Image Loading**: Coil Compose

---

## Building from Source

### Prerequisites

- **JDK 17**
- **Android SDK 35** (`compileSdk = 35`, `minSdk = 24`)

### Build Commands

```bash
# Run unit tests
./gradlew testDebugUnitTest

# Build per-ABI release APKs
./gradlew assembleRelease
```

Release APKs are output to `app/build/outputs/apk/release/`.

---

## Disclaimer & Responsible Use

MediaSave is an open-source graphical interface for `yt-dlp` and `FFmpeg`. This project does **not** host, distribute, or endorse copyrighted material. Users are solely responsible for ensuring they have the legal right, license, or copyright owner's permission before archiving or converting any online media stream. Intended for personal backups, open-licensed / Creative Commons content, public-domain media, and content you own.

---

## Author

Developed by **Dipendu Das** — licensed under the [MIT License](LICENSE).
