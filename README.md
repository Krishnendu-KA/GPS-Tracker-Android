# GPS Tracker - Native Android Tracking App

A high-performance, native Android GPS tracking application built using Kotlin, Google Maps SDK, and Google Play Services Location. It provides real-time GPS tracking, path visualization on Google Maps, distance calculation with jitter and accuracy filtering, and CSV-based local persistence across app sessions.

---

## Features

- **Google Maps Integration**: Full-screen interactive map with zoom controls, compass, and my-location button.
- **High-Accuracy GPS Tracking**: Powered by `FusedLocationProviderClient` requesting updates at a 2-second interval (1-second minimum interval).
- **Intelligent Filtering**:
  - **Accuracy Filter**: Discards GPS readings with accuracy worse than 30 metres to avoid inaccurate indoor/network fixes.
  - **Jitter Filter**: Ignores movements smaller than 3 metres while standing still to eliminate false distance accumulation.
- **WGS84 Distance Calculation**: Accurate cumulative distance calculation using Android's native `Location.distanceTo()`.
- **Segment-Aware Route Recording**: Stopping and resuming tracking creates independent segments, ensuring no false straight-line connecting jumps are drawn or added to distance.
- **Persistent History**: Automatically writes timestamped coordinates (`latitude, longitude, timestamp, speed, accuracy, segmentId`) to a private CSV file (`location_history.csv`). History is automatically reloaded and redrawn upon app restart.
- **Control & Action Bar**:
  - **Start**: Initiates location tracking and begins a new route segment.
  - **Stop**: Pauses tracking and releases location callbacks to conserve battery.
  - **Share**: Shares current coordinates and a Google Maps URL via Android's `ACTION_SEND` intent.
  - **Clear**: Prompts confirmation and safely wipes tracking history, resetting stats and map polylines.
- **Graceful Fallbacks**:
  - Handles denied or approximate-only location permissions cleanly.
  - Detects disabled location services and prompts the user to enable GPS.
  - Validates Google Play Services availability.
  - If a Google Maps API key is missing, displays an informative banner while allowing full GPS tracking, logging, and statistics to operate normally.
- **Lifecycle & Battery Conscious**: Subscribes to GPS updates exclusively in the foreground (`onPause()` automatically unsubscribes).

---

## Technology Stack

- **Language**: Kotlin 2.0.21
- **Android Gradle Plugin (AGP)**: 8.7.3
- **Gradle**: 8.11.1
- **Target SDK**: 35 (Android 15)
- **Compile SDK**: 35
- **Min SDK**: 24 (Android 7.0 Nougat)
- **Libraries**:
  - Google Maps SDK for Android (`play-services-maps:19.0.0`)
  - Google Play Services Location (`play-services-location:21.3.0`)
  - AndroidX AppCompat (`appcompat:1.7.0`)
  - Android Material Components (`material:1.12.0`)
  - AndroidX Activity KTX (`activity-ktx:1.9.3`)

---

## Project Structure

```
gps/
├── app/
│   ├── build.gradle.kts                      # App-level build configuration & dependencies
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml           # Manifest with location permissions & Maps metadata
│           ├── java/com/example/gpstracker/
│           │   ├── MainActivity.kt           # Primary UI, map handling, FusedLocationClient & state
│           │   └── LocationHistoryStore.kt   # CSV persistence, segment grouping, point parsing
│           └── res/
│               ├── drawable/                 # Background shapes and launcher icons
│               ├── layout/
│               │   └── activity_main.xml     # Clean, modern UI with map, stats, and 4 buttons
│               ├── values/
│               │   ├── colors.xml            # Palette (Material design colors)
│               │   ├── strings.xml           # Localized strings
│               │   └── themes.xml            # MaterialComponents DayNight NoActionBar theme
│               └── mipmap-anydpi-v26/        # Adaptive application icons
├── gradle/wrapper/                           # Gradle wrapper configuration
├── build.gradle.kts                          # Root build configuration
├── gradle.properties                         # JVM arguments and AndroidX flags
├── settings.gradle.kts                       # Repository management & project module setup
├── local.properties.example                  # Template for local SDK path and Maps API key
├── local.properties                          # Private local configuration (git-ignored)
└── README.md                                 # Documentation, setup & viva guide
```

---

## Quickstart & Setup Guide

### 1. Clone the Repository
```bash
git clone https://github.com/<your-username>/gps-tracker.git
cd gps-tracker
```

### 2. Configure `local.properties`
Copy `local.properties.example` to `local.properties` in the project root:
```bash
cp local.properties.example local.properties
```
Edit `local.properties` to specify your Android SDK path and your Google Maps API key:
```properties
sdk.dir=C:\\Users\\<YourUsername>\\AppData\\Local\\Android\\Sdk
MAPS_API_KEY=YOUR_GOOGLE_MAPS_API_KEY_HERE
```
> **Security Note**: `local.properties` is included in `.gitignore` and is never committed to GitHub.

---

## Google Maps API Key Setup Guide

To display Google Maps tiles inside the application:

1. **Visit the Google Cloud Console**:
   Go to [https://console.cloud.google.com/](https://console.cloud.google.com/).
2. **Create or Select a Project**:
   Create a new project or select an existing one. Ensure a billing account is linked to the project.
3. **Enable Maps SDK for Android**:
   - In the sidebar, navigate to **APIs & Services > Library**.
   - Search for **Maps SDK for Android** and click **Enable**.
4. **Create & Restrict API Credentials**:
   - Navigate to **APIs & Services > Credentials**.
   - Click **Create Credentials > API Key**.
   - Under **Application restrictions**, select **Android apps** and click **Add**:
     - **Package name**: `com.example.gpstracker`
     - **SHA-1 certificate fingerprint**: Run `./gradlew signingReport` to view your SHA-1 fingerprint.
   - Under **API restrictions**, select **Restrict key** and check **Maps SDK for Android**.
   - Save the key.
5. **Add Key to `local.properties`**:
   Add the API key to your local `local.properties` file:
   ```properties
   MAPS_API_KEY=AIzaSyYourActualKeyHere
   ```

---

## Building and Installing the APK

### Prerequisites
- JDK 17 or JDK 21 installed.
- Android SDK with platform 35 and build-tools installed.

### 1. Build via Command Line
Run the debug assembly task using the Gradle wrapper:
```bash
# On Linux / macOS:
./gradlew :app:assembleDebug

# On Windows (PowerShell / Command Prompt):
.\gradlew.bat :app:assembleDebug
```

The compiled APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### 2. Install on a Physical Android Phone

#### Option A: Via ADB (USB Debugging)
1. Enable **Developer Options** on your phone (Settings > About Phone > tap **Build Number** 7 times).
2. Enable **USB Debugging** under Settings > System > Developer Options.
3. Connect your phone via USB cable and authorize your computer.
4. Run:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

#### Option B: Direct File Transfer
1. Copy `app-debug.apk` to your phone via USB cable, Google Drive, WhatsApp, or email.
2. On your phone, tap the APK file.
3. If prompted, allow "Install from unknown sources" for your file manager or browser.
4. Complete the installation and open **GPS Tracker**.

---

## Phone Testing & Verification Guide

1. **Permission Handling**:
   - Upon tapping **Start**, the app requests `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION`.
   - If granted "Precise" location, high-accuracy GPS tracking begins immediately.
   - If granted "Approximate", the app alerts the user that accuracy filtering requires fine location, but tracking continues gracefully without crashing.
2. **GPS Fix & Live Statistics**:
   - Step outdoors with a clear line of sight to the sky.
   - The coordinates, speed (km/h), accuracy (± m), distance (km), and saved points update in real time.
3. **Filtering in Action**:
   - **Accuracy Filter (> 30 m)**: Weak signals with accuracy worse than 30 metres are filtered out from the polyline and distance calculation.
   - **Stationary Jitter Filter (< 3 m)**: When standing still, GPS reading fluctuations under 3 metres are filtered out so that total distance does not artificially inflate.
4. **Segment Separation (Stop & Start)**:
   - Walk 50 metres, tap **Stop**. Note the distance.
   - Walk another 50 metres with tracking paused.
   - Tap **Start** again. Observe that the app does NOT draw a connecting line across the paused gap and does NOT add the 50-metre paused distance.
5. **Share Intent**:
   - Tap **Share**. The Android system share sheet opens with your coordinates and a clickable Google Maps link.
6. **Data Persistence & Restart**:
   - Record several points, then close the app completely (swipe it away from Recent Apps).
   - Reopen the app. Notice the route points, total distance, and previous points count are restored from `location_history.csv`.
7. **Clear History**:
   - Tap **Clear** and confirm the dialog. The CSV file is deleted, polylines and markers are cleared from the map, and distance resets to `0.00 km`.

---

## Architecture & Implementation Highlights

- **`MainActivity.kt`**:
  - Implements `OnMapReadyCallback` to configure Google Maps.
  - Manages Android lifecycle (`onResume`, `onPause`, `onDestroy`) to ensure callbacks are cleaned up, avoiding battery drain and memory leaks.
  - Employs `ActivityResultContracts.RequestMultiplePermissions` for Android runtime permissions.
- **`LocationHistoryStore.kt`**:
  - Manages CSV operations in private internal storage (`context.filesDir/location_history.csv`).
  - Implements thread-safe writing (`append`) and deserialization (`loadAll`).
  - Supports segment tracking so distinct recording sessions remain isolated.
- **`activity_main.xml`**:
  - Uses `CoordinatorLayout` with a fullscreen `SupportMapFragment`.
  - Floating status pill indicating "Tracking Active" or "Tracking Idle".
  - Elevated bottom card featuring a 2x2 statistics grid and 4 clearly labeled Material buttons (`Start`, `Stop`, `Share`, `Clear`).

---

## Technical Viva / Interview Questions & Answers

### Q1: Why use `FusedLocationProviderClient` instead of the legacy `LocationManager`?
**A:** `FusedLocationProviderClient` is part of Google Play Services. It intelligently combines GPS, Wi-Fi, cell towers, and device sensors (accelerometer, gyroscope) to calculate coordinates. This provides:
- Faster Time-To-First-Fix (TTFF).
- Lower battery consumption through hardware batching and sensor fusion.
- Simpler API compared to manually switching between `GPS_PROVIDER` and `NETWORK_PROVIDER`.

### Q2: Why are both `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` requested together?
**A:** Starting in Android 12 (API 31), users can grant "Approximate" (coarse) location even when the app requests "Precise" (fine) location. If an app only requests `ACCESS_FINE_LOCATION`, the system throws an `IllegalArgumentException` on Android 12+. Requesting both permissions in a single array allows the user to choose their preferred level of privacy and ensures the app never crashes.

### Q3: Why is tracking paused in `onPause()` / `onStop()`?
**A:** Continuous GPS polling at 1-2 second intervals consumes significant battery power. Android guidelines dictate that foreground apps should remove location updates when hidden unless a dedicated foreground service with an ongoing notification is running. Unregistering updates in `onPause()` prevents memory leaks and battery depletion.

### Q4: How is distance calculated between GPS coordinates?
**A:** Android's `Location.distanceTo()` uses Vincenty's inverse formula or the Great-Circle distance formula over the WGS84 reference ellipsoid. It calculates the ellipsoidal distance in metres between two pairs of latitude and longitude coordinates, providing millimetre-accurate geographical distance.

### Q5: Why is an accuracy threshold (30 m) and jitter threshold (3 m) necessary?
**A:** GPS fixes indoors or in urban canyons often have high multipath errors (accuracy > 30–50 metres). Including these points causes erratic route lines. Additionally, when a user is standing still, random GPS drift (0.5 to 2 metres per second) would continuously accumulate fake distance. Ignoring movements < 3 metres ensures the distance counter accurately reflects physical movement.

### Q6: How does the application prevent a false connecting line when tracking is stopped and resumed?
**A:** Each recording session is tagged with a unique `currentSegmentId` (epoch timestamp). When tracking stops, the active segment is finalized and `lastAcceptedLocation` is reset to null. When tracking starts again, a new segment is initialized. On the map, each segment is drawn as an independent `Polyline` instance, preventing a false connecting line between the stop and resume locations.

### Q7: Why is `MAPS_API_KEY` stored in `local.properties` instead of `AndroidManifest.xml`?
**A:** Storing credentials in version control is a major security vulnerability. `local.properties` is excluded in `.gitignore`. Gradle injects the key into `AndroidManifest.xml` at build time via `manifestPlaceholders["MAPS_API_KEY"]`, keeping sensitive keys confidential and developer-specific.
