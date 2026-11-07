# RB1 Speed

A native Android GPS speedometer inspired by the blue illuminated Honda Odyssey RB1 cluster: cyan scale, blue ring, orange needle, 0–120 mph dial, and a large digital mph readout. Digital speed remains uncapped if the analog dial reaches its end.

## Install

1. Download [RB1-Speed.apk](https://github.com/arjxbh/rb1speedo/releases/latest/download/RB1-Speed.apk) to an Android phone (Android 8.0 or newer). [Release notes and older versions](https://github.com/arjxbh/rb1speedo/releases).
2. Open it in Files or Downloads. If Android asks, allow that app to install unknown apps, then choose Install. This is a personally built, development-signed APK, not a Play Store release.
3. Open **RB1 Speed**, tap the bottom GPS panel, and select **Precise** location and **While using the app**.
4. Turn on the phone's Location setting. Use outdoors with a clear view of the sky and allow time for the first satellite fix.

Tap **DEMO** at the top right to preview the moving dial. The orange DEMO label identifies simulated speed; tap **EXIT DEMO** or the bottom panel to return to live GPS. Reopening the app always returns to live mode.

The DEMO entry button hides after 10 continuous seconds of fresh live GPS speed above 1 mph. Stationary or unavailable speed resets that timer; simulated speed does not count. Once hidden, the button and its touch target stay hidden for the rest of that foreground session. Returning to the app restores it. EXIT DEMO remains available while a preview is active.

On first opening, the footer shows **安全運転で、いってらっしゃい** (“Drive safely—have a good trip”) for six seconds. The spoken greeting waits five seconds after opening to give Bluetooth/Android Auto audio time to settle, and plays if the phone's default text-to-speech engine has an installed offline Japanese voice. Backgrounding the app cancels any pending speech. With no such voice, the text still appears; the app does not download voice data or use a network voice. The greeting does not repeat on rotation or routine returns from settings/browser; a new app launch after the Activity has been closed can greet again.

A locally synthesized bell chimes at **75 mph**, repeating every 1.5 seconds. In live mode it uses accepted GPS speed; in demo mode it follows the simulated speed so you can preview the sound while stationary. It stops below **73 mph** (a small margin avoids toggling around the threshold), when live GPS speed expires, when exiting demo without a fresh fix, or when the app pauses. Both sounds use media volume and respect Android audio focus; they do not force volume changes or play in the background. Sound quality, Bluetooth routing, and Japanese speech depend on the phone and require physical testing.

Tap **UPDATES** at the top left to open the latest release in your browser. Download `RB1-Speed.apk`, open it, and approve the Android update prompt. The app does not check versions or download updates itself; it needs no Internet or installer permission. Returning from the browser resets to live GPS as usual. Install this version manually once to get the button. Keep the existing app installed when updating; Android verifies signing continuity.

## Behavior and privacy

- Uses Android's GPS-reported speed, converted from meters per second to mph.
- Uses location while the Activity is visible and stops updates when backgrounded. Keeps the screen awake while open.
- No account, network permission, ads, analytics, location storage, or background service.
- Missing speed, positional uncertainty over 35 meters, or reported speed uncertainty over 3 m/s is rejected. Readings expire after 4 seconds: the readout becomes a dash and the needle disappears, rather than implying the vehicle stopped.
- Main number shows whole mph without smoothing; the needle animates between updates. Refresh rate depends on the device.
- Supports portrait and landscape. Grant precise location for usable speed results.

Mount the phone before driving. GPS can lag or fail in tunnels and around obstructions; use the vehicle's instruments as your primary reference.

## Build from source

### This Linux laptop / Linux x86-64

Run once from this folder:

```sh
./setup-build.sh
```

This installs Temurin JDK 17, Android SDK 35, build-tools 35.0.0, and ECJ 3.38.0 into ignored `.tools/`. It needs curl, unzip, tar, zip, and sha256sum, and presents the Android SDK license on first setup. No sudo, Android Studio, global Java installation, or shell profile changes are needed.

For every build:

```sh
./build.sh
```

Output: `build-native/RB1-Speed.apk` and `build-native/RB1-Speed.apk.sha256`. The command runs speed-model checks, compiles the app, and verifies APK alignment and signing. It uses the included development key. The entire `.tools/` directory is disposable; rerun setup if it is removed.

### GitHub builds and releases

The **Android APK** workflow runs for pushes to `main`, pull requests, and manual runs. It uses the same setup/build scripts as this laptop and keeps APK artifacts for 30 days. Only version-tag pushes publish a GitHub Release; ordinary builds do not change the latest download.

To publish an update:

1. Increase `versionCode` and `versionName` in `version.properties` (both build paths read this file).
2. Update `RELEASE_NOTES.md`, run `./build.sh`, and commit the changes to `main`.
3. Push `main`, then push a matching version tag. For example, for version 1.2:

```sh
git push origin main
git tag v1.2
git push origin v1.2
```

Watch [Actions](https://github.com/arjxbh/rb1speedo/actions) for the build result. A successful tag build publishes the signed APK and SHA-256 checksum to [Releases](https://github.com/arjxbh/rb1speedo/releases). The latest download link and in-app UPDATES button then point to that release. No extra signing secret is required for this prototype because its development key is already included. Do not replace it with a new key for updates, and do not use this publicly included key as a production credential.

### Android Studio / Gradle alternative

Open this folder in Android Studio with JDK 17, Android SDK 35 and Gradle 8.9. Sync, then build the debug APK. No runtime dependencies beyond Android's framework.

With Gradle 8.9 installed: `gradle :app:assembleDebug`

The included development signing key is for this personal prototype, not a production credential. Store/key password: `android`; alias: `rb1`. Keep this key so later builds can update the supplied APK without uninstalling it.

`build-native.sh` is the dependency-light alternative used for the supplied APK. Requires SDK 35, build-tools 35.0.0, Java 17, zip, and Eclipse ECJ 3.38.0. Pass the SDK directory and ECJ jar as arguments.

`tests/SpeedReadingTest.java` checks mph conversion, zero speed, missing speed, accuracy rejection, invalid numbers, stale and out-of-order timestamps, and reset independently of Android. Real-world GPS behavior still needs verification on your phone.

`tests/DrivingFeaturesTest.java` checks the demo visibility timer, stops and GPS/timer gaps, warning threshold/hysteresis/repeat timing, expiry and session reset, and synthesized chime duration/envelope. Both suites run in local and GitHub native builds. Future tasks are tracked in [GitHub Issues](https://github.com/arjxbh/rb1speedo/issues).
