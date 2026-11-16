# IAM-authenticated owner pairing endpoint

- Added a separate Lambda Function URL with AWS_IAM authentication and a profile-aware SigV4 laptop helper (`npm run pair`). No EC2/Caddy/Android changes are required for this feature.
- New tests cover missing/wrong-account IAM context, allowed route/body, code length/verifier/expiry, no secret logging, issuance limits, failure handling, one-time compatibility with the existing phone enrollment API, temporary-session SigV4 signing and the two required caller permissions.
- CDK tests reject public URLs, attachment to existing roles, broad DynamoDB writes, and EC2/SSM/table ownership. The owner execution role can write only PAIR#/PAIR-RATE# records and its own logs. The separate deployment guard preserves the original shared-host and RB1 snapshots and checks all four sites plus the phone API.
- Full local suite: 30 tests passed. Separate owner synth and packaging checks passed. The owner endpoint has not been deployed or invoked in AWS; existing live applications are unchanged by this implementation.

# Empty execution response and post-deployment verification

- Successful `execute-change-set` calls may have no CLI output. The parser now accepts this exact operation without hiding failures or allowing empty responses from unrelated discovery operations.
- Added read-only `--verify` recovery for a previously submitted deployment, including original-template comparison, reviewed shared-host snapshot, exact SSM target/status, and all existing and new API/site checks. Already-submitted changes are not executed twice; future submissions use a deterministic client request token.
- All 21 API/CDK tests passed, including parser and read-only recovery regressions. Following the owner's execution, AWS reported RB1 `CREATE_COMPLETE`, SSM installation `Success`, the API `/health` was healthy, and all four existing sites/redirects and dependent API checks passed. The full read-only verifier also confirmed the deployed template and original shared-host/stack/policy snapshot match the review. No additional deployment was submitted to investigate the CLI error.

# Deployment parser fix after 1.6.0

- A successful AWS CLI `get-stack-policy` can emit an empty response for a stack with no policy. The deployment parser now records this optional absence without swallowing AWS command failures or empty output from unrelated discovery operations.
- Piggy Ledger's exact mandatory protection policy is still checked; all four existing stack snapshots are still preserved. Permission, credential and malformed-response failures stop deployment with their underlying diagnostic.
- All 20 API/CDK tests passed, including regressions for absent policies on the three dependent sites, missing Piggy Ledger protection, and access-denied/expired-token failures. No Android changes, AWS deployment, or production mutations were performed for this fix.

# Version 1.6.0 — dashcam and isolated upload infrastructure

- Native Android compilation, SpeedReading, DrivingFeatures, DisplayBrightness and DashcamPolicy suites passed; APK alignment and existing-certificate v2/v3 signing passed.
- Node API checks cover one-time/concurrent enrollment, access expiry, durable refresh rotation and lost response recovery, immediate API revocation, cross-device isolation, invalid part bounds, idempotent registration/completion, incomplete/corrupt part rejection, remote completion reconciliation, protected copy/expiry recovery, daily quota, HTTP validation and real SDK checksum/length-bound presigning without AWS calls.
- CDK checks cover absence of EC2/network/role ownership, 30-day routine retention, retained stateful resources, protected-prefix exclusion, stale multipart cleanup, exact-change-set removal/replacement/foreign-resource rejection, narrow SSM targets, existing-host role attachment, shared deployment lock, preservation checks and RB1-only rollback operations.
- Placeholder CDK synth passed against a fabricated instance ID and role; no real host ID was embedded. Shell syntax and whitespace checks passed. No existing application's repository or deployed resources were modified.
- API production dependency audit: zero known advisories. CDK dev tooling currently bundles a minimatch moderate advisory; see infra/README.md. API runtime uses its separate production-only lockfile.
- No connected Android device or configured AVD was available. Camera/sensor/thermal behavior, framing, orientation, 15-minute continuity, process-death recovery, low-space cleanup, Keystore persistence and battery/Doze uploads remain unverified on hardware. No live API/S3 upload or AWS deployment was performed. Local protocol tests use fake stores/storage plus real offline SDK URL signing.
- Before deploying, use the prepare/review/execute runbook in infra/README.md. It checks Piggy Ledger, Midlife Motors, Kaedama Cycles and The Cranky Goose, including redirects and existing API endpoints. DNS for the new API hostname must be configured separately; never point the API at an existing site's apex/www name.
- Physical parked acceptance: enable Camera on USB, grant camera/notification access, confirm real recording status, disconnect/reconnect under ten seconds and confirm the same session continues, park longer and play the shorter finalized clip, rotate/open Debug/switch apps without restarting recording, record through at least three 15-minute boundaries and inspect frame continuity. Verify incident protection, export, local deletion/unprotection, quota/free reserve and no overwrite of protected/leased files. Kill the app process while a fragment/assembly/upload is active and verify recovery after reopening.
- Cloud acceptance after separately reviewed deployment: pair once over Wi-Fi, interrupt a multipart upload, reboot/reopen and resume without pairing again, compare downloaded bytes/checksums with exported source, expire/revoke access, and confirm that auth failures never stop local recording. Test ignition-off/screen-off upload, loss of Wi-Fi without cellular/VPN fallback, battery floor, persisted 30-minute parking budget, severe heat, protected copies and routine 30-day retention. Recheck original GPS, greeting, volume, brightness, timeout, skins and all four hosted sites.

# Version 1.5.1 — debug panel

- Added a scrollable Debug dialog accessible alongside Demo. Reads actual STREAM_MUSIC steps, fixed-volume status, pending greeting state, sensor availability/lux, power state, Activity keep-screen-on flag, normal timeout, and app brightness request. Refreshes twice per second while open.
- Manual volume action uses the same launch-volume path. Dim/bright previews apply to both dashboard and dialog windows and reset after five seconds, dismissal, or pause. No new permissions or persisted debug overrides.
- Native build, all three model suites, APK alignment and v2/v3 signing passed. No connected device was available; dialog layout, sensor response, volume readback and preview cleanup still require physical testing.
- Parked checks: open Debug in both orientations; compare volume readback against hardware volume buttons, apply 70%, cover/uncover sensor, unplug/reconnect charger, run each brightness preview and wait five seconds, then repeat closing/backgrounding during preview. Confirm scrolling exposes all controls and normal timeout still works without touching the screen.

# Version 1.5 — mounted-phone behavior

- Native build, SpeedReading, DrivingFeatures, and DisplayBrightness suites passed; APK alignment and v2/v3 signatures passed with the existing development certificate.
- Brightness checks cover sensor bounds/monotonic response, ambient override, missing-sensor fallback, east/west longitude, and polar day/night.
- Reviewed lifecycle wiring: sticky battery plugged state on resume; live battery changes; screen-off rearming; receiver cleanup; sensor unregister and brightness/keep-awake release on pause. No new permissions.
- No Android device or emulator was connected during validation. Hardware and lifecycle behavior have not been exercised on-device.
- While parked, verify: start on battery and hear greeting after about five seconds; connect USB and confirm display stays awake beyond normal timeout; disconnect and confirm normal timeout; reconnect before timeout and confirm no repeat greeting; let screen turn off, then wake/resume and confirm a new greeting. Repeat with a full battery and a charger reported as AC.
- Cover/uncover the light sensor and check brightness; check volume starts near 70%, manual adjustments survive rotation/background return, and brightness returns to system control after leaving the app. Test offline Japanese TTS availability, pause-before-five-seconds cancellation, and both landscape mounting directions.
- Android’s developer “Stay awake while charging” setting must be disabled to isolate app timeout behavior. This implementation does not auto-launch or unlock the phone.

# Validation

## Launch media volume

- Fresh Activity launches set STREAM_MUSIC to the nearest available step to 50% of its maximum. Saved-state recreation and ordinary resumes do not reset volume. The setting persists after exit and affects other media playback.
- Added normal MODIFY_AUDIO_SETTINGS permission; fixed-volume devices are skipped and policy rejection does not prevent launch.
- Native build, both model suites, APK alignment, and signing verification passed. Physical phone/Android Auto volume behavior remains unverified: check launch from a different media volume, adjust it manually, rotate and background/return, then confirm only a fresh launch resets it. Head-unit volume may be controlled separately.

## Chime mixing without music ducking

- Speed chimes now play as media sonification without requesting audio focus. Any greeting speech is stopped and its focus released before the bell starts. Chime completion/cancellation no longer manages greeting focus.
- Foreground-only playback, warning timing, media volume, and spoken greeting focus behavior remain unchanged.
- Native build, SpeedReading and DrivingFeatures checks, APK alignment, and v2/v3 signature verification passed locally.
- Physical Sony/Android Auto verification remains required: while parked, play music, enter Demo, and confirm repeated bells mix without volume dips. Also check exiting demo and backgrounding stop the chime. No head-unit audio test was performed here.

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

## Version 1.2.2 — delay spoken greeting

- Spoken greeting is scheduled no earlier than five seconds after the initial Activity resume. TTS initialization can run during that interval; a slow engine may speak later, with the existing six-second readiness allowance shifted after the delay.
- The main thread is not blocked. Pause, destroy, audio focus loss, or a speed warning cancels pending greeting speech; lifecycle checks also guard a late TTS initialization callback.
- Native compilation, both existing test suites, APK alignment, and signing checks passed locally. No permissions or signing key changes.
- Physical-phone checks: cold-open with Bluetooth/Android Auto connecting, confirm five seconds before speech and no clipped opening; background within five seconds and confirm no delayed speech; confirm normal gauge/GPS operation during the delay. A fixed delay cannot establish that a particular car audio route is ready; clipping needs verification on the affected setup.

## Version 1.4 — responsive dashboard and gauge depth

- Replaced the fixed full-screen canvas with separate native controls and gauge artwork. Portrait stacks the gauge and digital speed; landscape places the gauge beside the speed and controls panel. Insets keep controls clear of system bars and cutouts.
- Added a beveled rim, recessed face lighting, shaded needle, and raised hub across all five skins. Static gauge artwork is cached until the view size or skin changes.
- Native build, SpeedReading and DrivingFeatures suites, alignment, and v2/v3 signing checks passed. Package, signing key, and permissions remain unchanged; release version is 1.4 / code 7.
- Android 15 / API 35 emulator: installed the APK and checked all five skins in portrait and landscape. Automated UI checks verified clickable buttons are at least 48 dp tall and do not overlap each other, exercised demo entry/exit, confirmed picker cancel preserves the selection, and confirmed the saved skin survives a cold Activity start.
- Captured all ten skin/orientation combinations. Visually inspected RB1 and Integra previews for gauge depth and separation from controls. Preview copies are in ignored `build-native/previews/`.
- Penpot MCP responded, but file inspection reported no connected plugin instance. No changes were made to the Penpot document.

Physical-phone layout, accessibility/font-size settings, real GPS, and audio routing still need device verification. The owner reviewed the emulator previews and approved this redesign for release.

## Version 1.4.1 — RB1 center-cover correction

- Compared the RB1 artwork against the owner-provided gauge photo: https://wbandsons.goauction.co.uk/stock/34-22.jpg?v=63815360152553
- RB1 now composites a large opaque black center disc over the needle, hiding its spindle and tail. The blue scale band is broader and minor ticks are white. The other four skins retain their exposed hubs.
- Native build, both existing test suites, APK alignment, and signing verification passed. Captured and visually inspected RB1 demo previews in portrait and landscape on the Android 15 emulator; only the outer needle segment is visible.
- Release version is 1.4.1 / code 8. The owner approved the correction for release; the existing package and signing key are preserved.
