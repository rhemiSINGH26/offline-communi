# IndicMesh Voice: 100% Offline Multi-Hop Voice & Text Walkie-Talkie

An offline Android mobile application designed for **zero-internet, disaster zone, and remote environments**. It pairs **On-Device Indic Speech Recognition (STT)** & **Speech Synthesis (TTS)** with an **ad-hoc P2P Wi-Fi & Bluetooth Multi-Hop Mesh Network**.

---

## 🚀 Key Features

1. **100% Zero-Internet Operation**:
   - Zero cloud dependencies, zero external router requirement, zero SIM data usage.
   - Operates completely antenna-to-antenna across phones using **Wi-Fi Direct, Wi-Fi Aware (NAN), and Bluetooth Low Energy (BLE)**.

2. **10 Supported Indic Languages**:
   - **Hindi (`hi`)** • **Gujarati (`gu`)** • **Marathi (`mr`)**
   - **Kannada (`kn`)** • **Malayalam (`ml`)** • **Tamil (`ta`)**
   - **Telugu (`te`)** • **Odia (`or`)** • **Bengali (`bn`)** • **English (`en`)**

3. **Multi-Hop Mesh Networking**:
   - Intermediate devices act as **autonomous relays/repeaters**.
   - If **Node A** is out of direct range of **Node C**, **Node B** forwards packets with route tracing and TTL decrement.
   - Built-in **LRU seen-packet deduplication** prevents broadcast storms and infinite routing loops.

4. **Modern Jetpack Compose Cyber UI**:
   - Push-to-Talk (PTT) Walkie-Talkie Button.
   - Live dynamic Audio Waveform visualizer.
   - Real-time Topological Node Graph showing connected peers and active hops.
   - Persistent Android Foreground Service keeping mesh connections alive when the phone is locked.

---

## 🛠️ Project Structure

```
d:/Smart India Hackathon/demo app/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml          # BLE, Wi-Fi Direct, Audio & Foreground Service permissions
│   │   └── java/com/indic/meshvoice/
│   │       ├── IndicMeshApp.kt          # Application base class
│   │       ├── MainActivity.kt          # Permissions lifecycle & Compose entry
│   │       ├── model/
│   │       │   ├── MeshMessage.kt       # Mesh packet, headers, serialization, route trace
│   │       │   ├── MeshNode.kt          # Connected peer tracking
│   │       │   └── SupportedLanguages.kt# 10 Indic languages definitions
│   │       ├── speech/
│   │       │   ├── OfflineSpeechRecognizer.kt # Offline Indic STT pipeline
│   │       │   └── OfflineTextToSpeech.kt     # Offline Indic TTS engine
│   │       ├── mesh/
│   │       │   ├── MeshRouter.kt        # Multi-hop routing engine & loop prevention
│   │       │   ├── NearbyMeshTransport.kt# Google Nearby Connections P2P_CLUSTER
│   │       │   ├── BleMeshTransport.kt  # Direct BLE advertisement & scan fallback
│   │       │   └── MeshEngine.kt        # Central mesh coordinator
│   │       ├── service/
│   │       │   └── MeshForegroundService.kt # Background relaying service
│   │       └── ui/
│   │           ├── components/
│   │           │   ├── AudioWaveform.kt       # Live microphone audio visualizer
│   │           │   └── NodeGraphVisualizer.kt # Canvas-based multi-hop graph
│   │           ├── theme/                     # Dark cyber aesthetic
│   │           └── MainScreen.kt              # Main UI with PTT, Node Graph, & Live Feed
│   └── build.gradle.kts
├── settings.gradle.kts
└── build.gradle.kts
```

---

## 🧪 How to Test

### Testing on Physical Devices:
1. Open this project folder (`d:\Smart India Hackathon\demo app`) in **Android Studio**.
2. Connect **2 or more Android devices** via USB / Wi-Fi Debugging.
3. Turn **Wi-Fi** and **Bluetooth ON** on all devices (No SIM card or Internet needed; you can put them in **Airplane Mode with Wi-Fi & BT on**).
4. Run the app on both devices.
5. Choose your Indic language (e.g. Hindi, Bengali, Tamil).
6. Press and hold the **PTT Walkie-Talkie Button**, speak, and release:
   - Your speech is recognized locally on-device.
   - The packet is transmitted across the local mesh.
   - The receiving phone speaks it out using its local TTS in the selected Indic language!
