# Sangly - Interactive Android Screen Charm & Gesture Notch

[![Android Release](https://img.shields.io/badge/Release-APK_Ready-brightgreen.svg)](Sangly-release.apk)
[![Platform](https://img.shields.io/badge/Platform-Android_8.0+-blue.svg)](#)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple.svg)](#)
[![License](https://img.shields.io/badge/License-MIT-orange.svg)](#)

**Sangly** brings interactive dangling charms with realistic pendulum physics to your Android camera notch or top screen border. Designed to be lightweight, beautiful, and deeply practical.

---

## ✨ Features

- **Realistic Pendulum Physics**: Tuned to **75% natural gravity** for smooth, playful swaying with dynamic damping and optional gyroscope/accelerometer tilt response.
- **Smart Pull-Down System Gestures**:
  - Pull down from the **Left side** ➔ Expands native Android **Notification Shade**.
  - Pull down from the **Right side** ➔ Expands native Android **Quick Settings Panel**.
  - Uses standard Android Accessibility gestures for zero-lag native integration.
- **Customizable Rope Styles**:
  - 🧶 **Classic Cord**
  - ⛓️ **Metallic Chain** (with interconnected link segments)
  - 🕸️ **Spider Web** (intricate spiderweb strands)
  - 🔮 **Crystal Beads** (segmented glossy beads)
  - 🧵 **Braided Rope** (intertwined 3-strand weave)
- **7 Vibrant Rope & Cord Colors**:
  - 🖤 Classic Black (`#212121`)
  - ❤️ Crimson Red (`#E53935`)
  - 💛 Golden (`#FFD700`)
  - 🤍 Silk White (`#F5F5F5`)
  - 🩵 Cyber Cyan (`#00E5FF`)
  - 💚 **Neon Green** (`#39FF14`)
  - 🩷 **Magenta** (`#FF007F`)
- **Iconic Charms Included**:
  - 🛡️ Captain America Shield
  - 🕷️ Spider-Man
  - 🧿 Evil Eye Talisman
  - 🐱 Lucky Fortune Cat (Maneki-Neko)
  - 👻 Playful Ghost
  - 📸 **Custom Photo Charm**: Select any photo or character with interactive borderless shaped framing (Circle, Heart, Square, Star, Shield) and real-time zoom/pan (*use images with removed background for better experience*).
- **Extreme Battery & CPU Efficiency**:
  - Automatic sleep mode when resting at equilibrium (0% idle CPU).
  - Active physics runs at capped 60 FPS consuming <1.5% CPU.
  - Zero heavy background ML/neural model overhead.

---

## 📱 Installation

### Direct APK Download
You can download the pre-compiled, optimized release APK directly:
- [**Download Sangly Release APK (Sangly-release.apk)**](Sangly-release.apk)

### Install via ADB
```bash
adb install -r Sangly-release.apk
adb shell am start -n com.hangly.app/.MainActivity
```

---

## 🛠️ Required Permissions

1. **Display Over Other Apps (`SYSTEM_ALERT_WINDOW`)**:
   Allows Sangly to display the dangling charm over your apps and home screen.
2. **Accessibility Service (`Sangly Screen Actions`)**:
   Allows Sangly to trigger native system Notification Shade and Quick Settings expansion when pulling the charm.

---

## 💻 Tech Stack & Architecture

- **Language**: Kotlin 1.9+
- **UI & Architecture**: Android Jetpack, Material Design 3, ViewBinding
- **Graphics & Physics**: Custom 2D canvas pendulum physics engine with Verlet integration and velocity damping
- **Web Simulation**: Standalone interactive HTML5/Canvas preview in `/web-preview`

---

## 🚀 Building from Source

```bash
git clone https://github.com/strawhats5897/sangly.git
cd sangly
./gradlew assembleRelease
```
The output APK will be located at `app/build/outputs/apk/release/app-release.apk`.

---

## 📄 License
MIT License. Free to use and customize.
