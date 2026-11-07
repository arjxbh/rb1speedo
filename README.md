# RB1 Speed

A native Android GPS speedometer inspired by the blue illuminated Honda Odyssey RB1 cluster: cyan scale, blue ring, orange needle, 0–120 mph dial, and a large digital mph readout. Digital speed remains uncapped if the analog dial reaches its end.

## Install

1. Download `RB1-Speed.apk` to an Android phone (Android 8.0 or newer).
2. Open it in Files or Downloads. If Android asks, allow that app to install unknown apps, then choose Install. This is a personally built, development-signed APK, not a Play Store release.
3. Open **RB1 Speed**, tap the bottom GPS panel, and select **Precise** location and **While using the app**.
4. Turn on the phone's Location setting. Use outdoors with a clear view of the sky and allow time for the first satellite fix.

Tap **DEMO** at the top right to preview the moving dial. The orange DEMO label identifies simulated speed; tap **EXIT DEMO** or the bottom panel to return to live GPS. Reopening the app always returns to live mode.

## Behavior and privacy

- Uses Android's GPS-reported speed, converted from meters per second to mph.
- Uses location while the Activity is visible and stops updates when backgrounded. Keeps the screen awake while open.
- No account, network permission, ads, analytics, location storage, or background service.
- Missing speed, positional uncertainty over 35 meters, or reported speed uncertainty over 3 m/s is rejected. Readings expire after 4 seconds: the readout becomes a dash and the needle disappears, rather than implying the vehicle stopped.
- Main number shows whole mph without smoothing; the needle animates between updates. Refresh rate depends on the device.
- Supports portrait and landscape. Grant precise location for usable speed results.

Mount the phone before driving. GPS can lag or fail in tunnels and around obstructions; use the vehicle's instruments as your primary reference.

## Build from source

Open this folder in Android Studio with JDK 17, Android SDK 35 and Gradle 8.9. Sync, then build the debug APK. No runtime dependencies beyond Android's framework.

With Gradle 8.9 installed: `gradle :app:assembleDebug`

The included development signing key is for this personal prototype, not a production credential. Store/key password: `android`; alias: `rb1`. Keep this key so later builds can update the supplied APK without uninstalling it.

`build-native.sh` is the dependency-light alternative used for the supplied APK. Requires SDK 35, build-tools 35.0.0, Java 17, zip, and Eclipse ECJ 3.38.0. Pass the SDK directory and ECJ jar as arguments.

`tests/SpeedReadingTest.java` checks mph conversion, zero speed, missing speed, accuracy rejection, invalid numbers, stale and out-of-order timestamps, and reset independently of Android. Real-world GPS behavior still needs verification on your phone.
