# Validation

- Compiled against Android SDK 35 with Java 8 bytecode; D8 minimum API 26.
- Built resource table and Android manifest with AAPT2.
- Passed independent speed-model checks for conversion, freshness, accuracy filtering, invalid values, sample ordering, and reset.
- Verified APK alignment and APK Signature Scheme v2/v3 signatures.
- Inspected final APK manifest: foreground fine/coarse location only; no Internet or background location permission.

The owner installed the initial app on a physical phone and confirmed that it works great. Prior emulator graphics were corrupted, so emulator visual QA was inconclusive. This does not establish road-tested GPS accuracy.

## Version 1.1 — build and update workflow

- Local native build passed using Temurin 17.0.21, ECJ 3.38.0, Android SDK 35, and build-tools 35.0.0.
- Existing SpeedReading checks passed; location processing was unchanged.
- APK alignment and v2/v3 signature verification passed.
- APK certificate matches the existing development key: SHA-256 `fab19551cf2b0c19e5c751fce524ac13a417b5bbf4b9e602978dd6a1c36a5449`.
- Version is 1.1 / code 2; package remains `club.midlife.rb1speed`.
- UPDATES opens the fixed HTTPS latest-release URL through an external browser; no network or installer permission was added.
- Android Studio/Gradle build was not exercised; the laptop and CI use the native build path.

Physical-phone checks still required for this version: install over the existing app, inspect UPDATES in portrait and landscape, tap it to open the release, download/install an APK through the browser, and confirm returning to the app resets to live GPS. Live GPS reception and accuracy still require phone testing. The speedometer displays unavailable speed as a dash after four seconds without an accepted GPS speed fix.

## Version 1.2 — demo visibility, speed chime, and Japanese greeting

The owner confirmed that version 1.1 worked before these changes.

- Native compilation, existing SpeedReading tests, and new DrivingFeatures tests passed locally.
- New checks cover the ten-second movement interval, jitter/stops, unreliable GPS and timer gaps, session reset, warning start at 75 mph / stop below 73 mph, 1.5-second repeat cadence, invalid samples, four-second expiry, and chime PCM duration/envelope.
- APK alignment and v2/v3 signatures passed with the unchanged development certificate.
- Package is unchanged; version 1.2 / code 3; minimum API 26 and target API 35.
- Greeting uses Android TTS voice discovery restricted to installed offline Japanese voices. Text remains visible without speech. The manifest adds TTS service visibility, with no new permissions.
- Audio is paused/stopped on Activity pause and released on destroy. The greeting is not replayed on configuration recreation or routine Activity resumes. Audio focus interruptions stop playback; media volume is left under user control.

Still requires a physical phone: install over 1.1, greeting text/pronunciation with an offline Japanese voice and text-only fallback without one, media volume and Bluetooth audio routing, audio focus interruptions, portrait/landscape appearance, demo button hiding during real movement (and no hidden touch target), foreground return resetting the button, live GPS warning audibility, and audio stopping when backgrounded. Automated threshold tests avoid needing a high-speed road test. No emulator/audio playback or phone test was performed in this environment.

## Version 1.2.1 — preview speed chime in demo mode

- Demo and live mode now share the displayed-speed calculation with warning processing. Only live GPS movement counts toward hiding the DEMO entry button.
- Demo uses the existing 75 mph start, 73 mph stop margin, and 1.5-second cadence. Live mode still requires a fresh GPS reading. Mode changes clear simulated warning state.
- Verify on the phone while stationary: enter demo, wait about five seconds for 75 mph, listen for repeated bells, confirm they stop below 73 mph, exit demo without a GPS fix and confirm silence, and background the app during playback to confirm it stops.
