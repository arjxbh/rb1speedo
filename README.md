# RB1 Speed

A native Android GPS speedometer with five JDM-inspired skins. A recessed analog dial, beveled rim, and shaded needle sit beside a large seven-segment mph readout in landscape and above it in portrait. A small GPS status beside the mph label opens details or lets you retry a fix. Native buttons stay outside the gauge. Digital speed remains uncapped if the 0–120 mph analog dial reaches its end.

An outer-scale pointer and **Max … mph** label retain the highest accepted GPS speed for the current drive. The first live speed above 1 mph starts a drive on battery or external power. The drive ends after three continuous minutes stopped (≤1 mph) and unplugged. Settings, rotation and reopening preserve the peak within the same phone boot. GPS loss or leaving the dashboard preserves the peak and restarts the stationary confirmation timer; speeds are only observed while the dashboard tracks GPS. Demo has its own maximum. The pointer stops at 120 mph while the label retains the actual peak.

## Dashboard skins

Tap **Settings → Skins** in the header to choose a car-inspired dashboard. The app remembers your selection across restarts; canceling keeps the current skin.

- **Honda Odyssey Absolute RB1** — luminous blue scale, cyan markings, white minor ticks, and a large black center cover hiding the needle spindle and tail.
- **Nissan Skyline GT-R R32** — dark analog face, condensed ivory markings, orange needle.
- **Toyota Supra A80** — amber illumination, broad dial sweep, warm orange needle.
- **Mazda RX-7 FD3S** — compact warm-white scale with labels every 10 mph and a red needle.
- **Honda Integra Type R DC2** — white face, bold dark numerals, red needle and yellow rim.

These are inspired speedometer interpretations. All skins show the same live GPS speed in mph with a 0–120 mph analog scale and uncapped digital readout. Switching skins also works in demo mode and preserves the current reading and warning behavior.

## Install

The default **RB1-Speed.apk** now includes the working Wide camera-access configuration. Its application identity is `org.codeaurora.snapcam`, and its launcher name is **RB1 Speed**. On the OnePlus HD1925, Auto can discover ultrawide camera 3 and select it as the widest compatible exposed rear lens.

**Upgrading from Wide:** install RB1-Speed.apk directly over RB1 Speed · Wide without uninstalling it. Its settings, permissions, pairing and private clips stay with the same installed app; only the launcher name and release filename change. Install v1.6.9 or newer manually once using the link below, since v1.6.8 Wide's Updates button targets the retired variant filename. The older `club.midlife.rb1speed` app remains a separate legacy installation with its own data; this update does not migrate or delete it. Stop recording in that legacy app before using the current app.

1. Download [RB1-Speed.apk](https://github.com/arjxbh/rb1speedo/releases/latest/download/RB1-Speed.apk) to an Android phone (Android 8.0 or newer). [Release notes and older versions](https://github.com/arjxbh/rb1speedo/releases).
2. Open it in Files or Downloads. If Android asks, allow that app to install unknown apps, then choose Install. This is a personally built, development-signed APK, not a Play Store release.
3. Open **RB1 Speed**, tap the GPS status button, and select **Precise** location and **While using the app**.
4. Turn on the phone's Location setting. Use outdoors with a clear view of the sky and allow time for the first satellite fix.

Tap **Demo** beside the camera status to preview the moving dial. The orange “Simulated speed” label identifies the preview; tap **Exit demo** or **Return to live GPS** to return to live GPS. Reopening the app always returns to live mode.

The **Settings** and **Demo** entry buttons hide after 10 continuous seconds of fresh live GPS speed above 1 mph. Stationary or unavailable speed resets that timer; simulated speed does not count. Once hidden, the buttons and their touch targets stay hidden for the rest of that foreground session. Returning to the dashboard restores them. EXIT DEMO remains available while a preview is active.

On first opening, the footer shows **安全運転で、いってらっしゃい** (“Drive safely—have a good trip”) for six seconds. The spoken greeting waits five seconds after opening to give Bluetooth/Android Auto audio time to settle, and plays a bundled Japanese recording offline. Backgrounding the app cancels pending or active greeting playback. The greeting plays once per screen-on session: cycling power, rotating, or returning from settings/browser does not replay it. After the screen turns off, the next resume or power connection while the app is visible can greet again. A fresh app launch can also greet.

The greeting was synthesized with Open JTalk using HTS Voice “Mei” by the MMDAgent Project Team, © 2009–2013 Nagoya Institute of Technology, Department of Computer Science. [Voice license (CC BY 3.0)](https://github.com/r9y9/pyopenjtalk/blob/master/pyopenjtalk/htsvoice/LICENSE_mei_normal.htsvoice)

A locally synthesized bell chimes at **75 mph**, repeating every 1.5 seconds. A soft amber ring expands and fades around the gauge with each chime. In live mode it uses accepted GPS speed; in demo mode it follows the simulated speed so you can preview the sound while stationary. It stops below **73 mph** (a small margin avoids toggling around the threshold), when live GPS speed expires, when exiting demo without a fresh fix, or when the app pauses. On a fresh app launch, phone media volume is set to the nearest available step to 70%; this also affects other media apps and is not restored on exit. Rotation and routine returns to the app do not reset it. Fixed-volume devices or device policy may prevent the adjustment. Both sounds use media volume and do not play in the background. The speed chime mixes with existing music without requesting audio focus, so it does not ask Android or Android Auto to lower the music volume. The spoken greeting still requests temporary audio focus and can briefly lower music volume. Sound quality and Bluetooth routing depend on the phone and require physical testing.

Tap **Settings → Updates** to download the default `RB1-Speed.apk` through your browser, open it, and approve the Android update prompt. The app does not check versions or download updates itself; it does not request installer permission. Returning from the browser resets to live GPS as usual. Install this version manually once to get the button. Keep the existing app installed when updating; Android verifies signing continuity.

## Windshield-mounted use

Leave RB1 Speed open in landscape. Any external power source (including a USB car charger reported as AC) keeps the display on, even at 100% battery. Removing power clears that override and lets Android use its existing timeout. The app does not change the system timeout, auto-launch on ignition, or bypass the lock screen. Disable Android’s developer “Stay awake while charging” option if you want to test normal timeout behavior independently.

The ambient light sensor controls brightness inside the dashboard: approximately 6% in darkness, rising smoothly to 100% at 10,000 lux. Keep the sensor unobstructed by the mount. Without a sensor reading, the app dims to 8% between approximate sunset and sunrise using the latest GPS position; before a position is available it uses 7 pm–7 am local time. Daytime fallback uses Android’s brightness setting. App brightness is released on leaving the dashboard; no system brightness permission is needed.

Tap **Settings → Debug** for live checks while parked. The panel refreshes twice per second and shows external power, the keep-screen-on flag, system timeout, actual media volume and its 70% target step, greeting state, sensor lux, brightness source, and requested app brightness. **Apply 70% media volume** reapplies the launch setting; this changes phone media volume and remains after closing. **Test dim** and **Test bright** override brightness for five seconds, then restore automatic control. Closing the panel or leaving the app also clears the preview. Cover/uncover the sensor or unplug/reconnect the charger to watch real inputs change. Requested brightness is not a measurement of physical panel output. Touching the screen restarts Android’s idle timeout.

## Dashcam

For owner pairing without a server shell, the optional [IAM/SigV4 owner endpoint](docs/OWNER_PAIRING.md) lets you run `npm run pair` on your laptop using your existing AWS profile. Its separate CDK stack does not change the shared EC2 host or require a phone update.

Tap **Settings → Camera & clips** to enable rear-camera recording while externally powered. The app creates approximately 15-minute, 1080p/30 fps MP4 clips at 8 Mbps, plus a shorter last clip after ten seconds unplugged. Boundaries use keyframes, so clips can run slightly over 15 minutes. Recording survives leaving the dashboard and has a foreground notification with Stop. Normal screen timeout still applies on battery.

**Camera lens:** the default is **Automatic (widest)**. RB1 compares estimated horizontal field of view at 16:9 using sensor size and focal length, considers separately exposed rear cameras and physical lenses behind logical cameras, and uses supported zoom-out ranges on Android 11+. Only 1080p/30-compatible recording options are considered. Auto tries the next widest candidate if a camera is unavailable at runtime.

Choose **Settings → Camera & clips → Camera lens** to select a rear camera manually or return to Auto. The choice survives restarts, and the aiming preview follows the same lens. Changing lenses while recording finishes the current clip and starts a new one, so a brief recording gap is expected. Camera setup shows the selected camera and approximate field of view. If a saved camera disappears, RB1 falls back to Auto and reports it. Firmware can hide auxiliary lenses from third-party apps; RB1 can only use cameras exposed through Android, so a phone with three rear lenses may expose fewer choices.

The active dashboard has **Start recording**, **Flip camera** and **Stop recording** buttons. Flip camera switches between the rear and front camera, finishing the current clip before restarting capture; a brief gap is expected. While stopped it selects the camera for the next manual start without enabling recording. The indicator identifies Front or Rear. Rear Auto still chooses the widest compatible exposed lens, and automatic power-based starts default to rear. Stop recording disables automatic restart until recording is enabled again. Tap the recording indicator for camera status.

The dashboard shows a red **● REC Front/Rear** only after frames have been written to video, and clears it if no frame is written for three seconds. The second camera status line shows the selected lens while running and the last status/error while stopped. Tap either camera status line for readable details or Camera & clips. Start recording is disabled while the service is running or closing; Stop recording is disabled while it is off. All three camera controls remain visible.

GPS status includes accuracy when connected. Tapping a healthy connection opens GPS details without clearing its live speed reading; tapping while GPS is unavailable still enables/retries it. The connected footer shows external power versus battery status. Gauge artwork and skin layouts remain unchanged; long skin titles fit the available header width.

Open **Settings → Aim camera · live preview** (or **Show aiming preview** in Camera & clips) to aim the mounted phone while parked. The rear-camera preview includes thirds and center guides, updates at up to four frames per second, and works without enabling recording. Preview alone saves no video. While recording, it shares the camera and encoder session; closing the preview removes its capture target without stopping the recording. Leaving camera setup releases preview frames, and preview-only camera access ends when the screen is no longer visible.

**Google Photos playback (Android 10+):** completed clips are automatically published to **Movies/RB1 Speed**, including existing finalized clips when you open the updated app. In Google Photos, open **Collections → On this device → RB1 Speed**. Only finished MP4s appear; live recording fragments remain private. Publishing can wait if the loop quota or 5 GiB reserve leaves insufficient space; Camera & clips shows the gallery status. Android 8–9 retains in-app Play/Share MP4 without a new storage permission.

The upload queue keeps a private original so deleting or editing a gallery video cannot corrupt resumable S3 uploads. Both originals and published copies count toward the loop quota. Loop overwrite and **Delete local copy** remove both copies; saved incidents protect both from automatic overwrite. Deleting a published copy in Photos leaves its upload original intact and does not cause the app to republish it. This folder is still loop storage: copy clips elsewhere to keep them independently. Shared gallery videos survive uninstalling the app. Google Photos backup is controlled by your Photos settings; this app does not enable it or change S3 retention. See [Google’s device-folder instructions](https://support.google.com/photos/answer/9343965?co=GENIE.Platform%3DAndroid&hl=en).

The default local loop is 32 GiB, configurable to 8/16/32/64 GiB, with a 5 GiB free-space reserve. It deletes uploaded ordinary clips first, then oldest unprotected unsynced clips when necessary. **Save incident** protects the latest two clips and the next clip; protection can fill the loop and pause recording. **View local clips** supports playback, sharing, deleting local files, and allowing local overwrite. Protected S3 copies remain protected if local protection is removed. Uninstalling or clearing app data removes private local footage and pairing credentials; published gallery copies remain in shared storage.

Cloud copies require deploying the included API and pairing the phone once. Uploads use validated, unmetered Wi-Fi directly (no cellular or VPN fallback), even after ignition-off with the screen off. On battery they stop at 30% or after the 30-minute parking budget; charging removes that budget. Android can defer background jobs. S3 routine copies expire after 30 days from upload; protected copies do not. The phone has no AWS keys. It automatically renews its API credentials and resumes acknowledged upload parts.

Power, camera and thermal failures are visible in Camera status. Recording stops at severe OS thermal status or battery temperature of 45°C; test the mounted phone before relying on it. Clips are silent by default. In **Camera & clips**, tap **Enable microphone for next recording** and grant microphone permission to include AAC audio in new clips, including shared and uploaded copies. The setting takes effect the next time recording starts. A phone reboot schedules uploads and, when notifications are permitted, offers a reminder to open the dashboard. It does not start a camera from a boot receiver or guarantee that the phone powers itself on after battery exhaustion. Open RB1 once after reboot; an unattended start from a locked/background device is not promised.

See [the design and authentication mechanism](docs/DASHCAM_DESIGN.md) and [the guarded EC2 deployment runbook](infra/README.md). The initial owner-run AWS deployment has completed and passed template, shared-host, SSM and all four existing-site checks. The API is live at `https://dashcam.midlifemotor.club`. Physical camera/thermal testing and end-to-end phone/S3 upload testing remain pending.

## Behavior and privacy

- Uses Android's GPS-reported speed, converted from meters per second to mph.
- Uses location while the dashboard Activity is visible and stops GPS updates when backgrounded. Keeps the screen awake while open and externally powered; unplugging restores the phone’s normal screen timeout.
- No ads, analytics, or GPS route storage. Optional device pairing authorizes cloud video copies. Camera recording uses a foreground service; background jobs upload on eligible Wi-Fi. All remote access uses HTTPS.
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
2. Replace `RELEASE_NOTES.md` with a short, current-release summary, run `./build.sh`, and commit the changes to `main`.
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

### Ultrawide lens debugging

While parked, open **Settings → Debug → Ultrawide lens debug**. **Scan rear lenses** reports rear camera IDs, physical-camera groups, sensor/focal data, estimated field of view, zoom-out ranges, supported recording sizes, and frame-rate ranges. **Probe camera IDs 0–9** additionally queries metadata for unlisted numeric IDs. Metadata alone does not establish access. **Preview a lens** offers discovered rear routes, widest estimated field first, for an eight-second live preview. Compare the view or cover individual lenses to identify ultrawide. **Stop probe** closes the camera; **Copy results** copies a saved report. Nothing is recorded, uploaded, or added to the normal lens preferences. Normal recording pauses while the debug screen is open.

**Package access info** explains vendor package filtering and attempts read-only access to camera allowlist properties; empty or denied property values are inconclusive. The default RB1 app now uses the snapcam identity that exposed the HD1925 ultrawide lens. Earlier v1.6.7 lens helpers and v1.6.8 Wide builds remain archived in their releases; separate helper/variant builds are no longer produced.

Run Scan rear lenses and preview camera 3 to check the ultrawide view, then select Auto or camera 3 in Camera & clips and test recording. The debug scan saves no footage or preferences. Existing front/rear flipping and power-based automatic-start conditions remain unchanged.

### Application identity and upgrade checks

Both native and Gradle builds use `org.codeaurora.snapcam` as the application ID and retain `club.midlife.rb1speed` as the Java namespace. Fully qualified manifest components and `org.codeaurora.snapcam.clips` support the existing service, receiver and clip-sharing paths. The default APK uses the existing signing certificate so it updates prior Wide/probe installations in place.

Run `./scripts/test-android.sh FullAppInstrumentation` to check the full app and its private clip provider. When an earlier app with this identity is already installed on the emulator, the runner seeds a test preference and private file before updating and verifies their preservation. Existing camera-choice, lens-debug and gallery runners now target the default app directly; no variant argument is needed.
