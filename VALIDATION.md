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
