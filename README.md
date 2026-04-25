# 📷 GridCam

> An AI-powered Android camera app that analyzes your scene in real-time and suggests the best photography composition grid — Rule of Thirds, Golden Spiral, Leading Lines, or Symmetry.

---

## ✨ Features

| Feature | Description |
|---|---|
| 🤖 **Smart Grid AI** | On-device MobileNetV2 model classifies composition in real time (~1.5s intervals) |
| 🎨 **4 Grid Overlays** | Rule of Thirds, Golden Spiral, Leading Lines, Symmetry |
| 🏷️ **Grid Name Pill** | Animated badge fades in/out with the active grid name |
| 📊 **Confidence Indicator** | Color-coded: 🔴 < 40% · 🟡 40–74% · 🟢 ≥ 75% |
| 📸 **Photo Capture** | Saves JPEG directly to `Pictures/GridCam/` in the gallery |
| 🔄 **Front & Back Camera** | Flip camera with animation; AI works on both |
| 🔍 **Zoom** | Pinch-to-zoom + `1×` / `2×` quick-switch pills |
| ⚡ **Flash Toggle** | Tap to cycle OFF ↔ ON |
| 🖼️ **Last Photo Thumbnail** | Tap to open that photo directly in your gallery |
| 🌑 **Immersive Mode** | Full-screen, no status/nav bars; notch-safe top bar |

---

## 🏗️ Architecture

```
gridCam/
├── MLCode/
│   ├── train.ipynb        # Model training (Google Colab)
│   └── test.ipynb         # Model inference test (Google Colab)
├── dataset/
│   ├── train/             # 321 training images across 4 classes
│   │   ├── 1_RuleOfThirds/   (81 images)
│   │   ├── 2_GoldenSpiral/   (80 images)
│   │   ├── 3_LeadingLines/   (80 images)
│   │   └── 4_Symmetry/       (80 images)
│   └── test/              # 91 test images
├── grid_model.tflite      # Trained & converted model (~9.5 MB)
└── gridcamapp/            # Android app (Java)
    └── app/src/main/
        ├── java/com/dark/gridcam/
        │   └── MainActivity.java   # All camera + AI + UI logic
        └── res/
            ├── layout/activity_main.xml
            ├── drawable/           # Grid overlays, icons, backgrounds
            ├── anim/               # Fade in/out for grid label
            └── values/             # Colors, themes, strings
```

---

## 🤖 ML Model

### Architecture
- **Base**: MobileNetV2 (pre-trained on ImageNet, frozen)
- **Head**: `GlobalAveragePooling2D → Dense(128, relu) → Dense(4, softmax)`
- **Input**: 224×224 RGB image, pixel values rescaled `[0,255] → [-1, 1]` internally
- **Output**: 4-class softmax probabilities

### Classes
| Index | Class | Description |
|---|---|---|
| 0 | Rule of Thirds | Subject aligned to 1/3 grid intersections |
| 1 | Golden Spiral | Fibonacci spiral composition |
| 2 | Leading Lines | Converging lines draw the eye |
| 3 | Symmetry | Reflective or bilateral symmetry |

### Training
- **Framework**: TensorFlow / Keras (Google Colab)
- **Optimizer**: Adam
- **Loss**: Sparse Categorical Cross-Entropy
- **Epochs**: 10
- **Batch size**: 32
- **Dataset**: 321 train / 91 test images
- **Export**: Converted to TFLite with `TFLiteConverter`

### On-Device Inference
- Runtime: **LiteRT (Google AI Edge)** `1.3.0` — rebranded TFLite with 16 KB page-size alignment
- Analysis runs on a background `ExecutorService` every **1.5 seconds** to avoid UI jank
- Grid is displayed only when confidence **≥ 60%**

---

## 📱 App — Tech Stack

| Component | Library / Version |
|---|---|
| Language | Java |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 35 (Android 15) |
| Camera | CameraX `1.3.0` |
| ML Runtime | LiteRT (`com.google.ai.edge.litert`) `1.3.0` |
| UI | ConstraintLayout `2.2.1`, Material3, ViewBinding |
| Build | AGP `8.13.2`, Gradle `8.13`, JDK 25 |

---

## 🔑 Key Camera Use Cases

```
ProcessCameraProvider
 ├── Preview          → PreviewView (live viewfinder)
 ├── ImageCapture     → MediaStore JPEG save (Pictures/GridCam/)
 └── ImageAnalysis    → MobileNetV2 composition classifier
```

All three are bound simultaneously. `ImageAnalysis` uses `STRATEGY_KEEP_ONLY_LATEST` to drop frames and avoid backpressure.

---

## 🎨 UI Layout

```
Screen (edge-to-edge, immersive)
 ├── PreviewView               ← full screen viewfinder
 ├── overlayImageView          ← grid SVG overlay (fitXY, full screen)
 ├── [TOP BAR] (notch-safe)
 │    ├── flashButton          ← ⚡ flash OFF/ON toggle
 │    ├── "GridCam"            ← app title
 │    └── aiConfidenceText     ← ● 87% (color-coded)
 ├── gridNameLabel             ← "Rule of Thirds" pill (fade in/out)
 ├── zoom1x / zoom2x           ← tap zoom pills (amber = active)
 └── [BOTTOM BAR] (gradient vignette, 200dp)
      ├── smartGridToggle      ← amber AI on/off switch (top row)
      ├── thumbnailView        ← last photo preview (bottom-left)
      ├── captureButton        ← shutter (scale-pulse on press)
      ├── "PHOTO" label        ← amber label under shutter
      └── flipCameraButton     ← 180° flip animation
```

---

## 🛠️ Build & Run

### Prerequisites
- Android Studio (or command-line Gradle)
- JDK 25 at `/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home`
- Android SDK 35

### Build
```bash
cd gridcamapp
./gradlew assembleDebug
```
APK output: `app/build/outputs/apk/debug/app-debug.apk`

### JDK Note
`gradle.properties` pins `org.gradle.java.home` to JDK 25. This is required because AGP 8.13.2's `JdkImageTransform` needs `jlink`, which is absent from JRE-only runtimes (e.g., VS Code's Red Hat Java extension JRE).

---

## 🔒 Permissions

| Permission | Purpose | API scope |
|---|---|---|
| `CAMERA` | Live viewfinder & capture | All |
| `WRITE_EXTERNAL_STORAGE` | Save photos to gallery | API ≤ 28 |
| `READ_EXTERNAL_STORAGE` | Load last-photo thumbnail | API 29–32 |
| `READ_MEDIA_IMAGES` | Load last-photo thumbnail | API 33+ |

---

## 📁 Key Files

| File | Purpose |
|---|---|
| `MainActivity.java` | All logic: camera, AI, zoom, flash, flip, thumbnail, UI |
| `activity_main.xml` | Full camera-style layout |
| `grid_model.tflite` | On-device composition classifier |
| `MLCode/train.ipynb` | Model training notebook (run on Colab) |
| `MLCode/test.ipynb` | Model inference testing notebook |
| `drawable/grid_*.xml` | SVG grid overlays (strokeWidth 0.5, viewport 100×100) |
| `gradle.properties` | JDK path + Gradle JVM args |
| `libs.versions.toml` | Centralized dependency versions |

---

## 🗺️ Roadmap

- [ ] Portrait / Landscape orientation lock
- [ ] Video recording mode
- [ ] Manual grid selection (override AI)
- [ ] Confidence threshold user setting
- [ ] Retrain model with larger dataset for higher accuracy

---

## 📄 License

This project is for personal / educational use.
