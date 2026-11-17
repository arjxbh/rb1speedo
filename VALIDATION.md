# Version 1.6.11 — current-drive peak marker

- Native build, seven model suites, alignment and existing-certificate signing passed. DrivePeak tests cover power arming, first movement, monotonic peak, invalid readings, powered stops, unplugged movement, three-minute stationary completion, rearming, GPS gaps and restored state.
- Dashboard instrumentation passed marker pixel checks for all five skins with unavailable current GPS and with peaks above the analog scale. Checked accessible peak descriptions, accepted/rejected live fixes, separate Demo peaks and persistence after reopening.
- Peak persistence is scoped to the Android boot count. Pending stationary confirmation restarts on activity recreation, backgrounding or unavailable GPS; the app does not infer that the vehicle remained stopped without fresh observations. GPS speeds while the dashboard is paused are not recorded.
- Visually inspected the portrait RB1 dashboard with a retained 95 mph peak and current speed at zero; pointer and caption sit clear of the scale and existing controls.
- Physical-phone acceptance: check marker readability and power-based drive boundaries during normal use. Existing gauge dimensions, current-speed behavior, camera implementation and permissions are unchanged.

# Version 1.6.10 — clearer dashboard status

- Native build and six existing model suites passed. Added DashboardInstrumentation for stopped-camera Start/Stop availability, visible camera error text, full camera-status inspection, GPS accuracy details, repeated healthy GPS inspection without discarding the fix, and leaving while a status dialog is open.
- Dashboard and full-app upgrade/provider integration suites passed. A post-instrumentation emulator capture was incomplete; a clean process restart rendered the complete dashboard correctly.
- Portrait and landscape RB1 screenshots inspected for status-line fit, readable disabled controls, header and gauge alignment. Existing gauge artwork and layout weights are unchanged; shared title text now auto-sizes within 12–18 sp.
- Device acceptance remains: verify active recording disables Start and enables Stop, the active ultrawide lens appears in the second line, Stop shows camera closing until release, and a real frame interruption clears REC and shows waiting for video. No camera-service, recording-format, permission or AWS changes.

# Version 1.6.9 — Wide is the default app

- Native and Gradle application IDs are now `org.codeaurora.snapcam`; the Java namespace and fully qualified component names remain unchanged. The default native output is the sole `RB1-Speed.apk`, labelled RB1 Speed. Separate Wide/helper packaging and variant routing have been removed.
- Emulator upgrade acceptance seeded a test preference and private file in the installed Wide v1.6.8 app, then installed the default v1.6.9 APK without uninstalling. Both markers survived, and full-app integration passed the new label, full dashboard launch, service/receiver registration, private clip-provider read/query, diagnostics ownership and legacy provider independence checks.
- Native six model suites, APK alignment and existing-certificate signing passed. Owner reports the preceding Wide build works on HD1925_11_F.05; camera selection and recording implementation are unchanged by this packaging consolidation.
- The older `club.midlife.rb1speed` app is a separate legacy installation. Its private data is neither migrated nor removed. New default updates preserve the existing Wide app's data and permissions. Old Wide v1.6.8's Updates button targets the retired asset filename; install v1.6.9 manually once using RB1-Speed.apk.
- Default-target camera-choice, lens-debug and gallery integration suites passed rear/front preview handoffs, candidate metadata/ID probes, live rear previews, cancellation and saved reports, plus generated-H.264 MediaStore publishing/ownership/cleanup.
- No new Android permissions or AWS changes. Long-running thermal/power acceptance remains device work.

# Version 1.6.8 — full snapcam application

- Native build, six model suites, alignment and existing-certificate signing passed for the normal app and full `RB1-Speed-snapcam.apk`. The generated Wide manifest retains all activities/services/receiver, changes the application identity to `org.codeaurora.snapcam`, and uses a unique clip-provider authority. The launcher opens MainActivity instead of the old probe screen.
- Emulator installation upgraded the existing snapcam lens probe to the full Wide app alongside the original. Full-app integration passed manifest component/metadata checks, original and Wide private-provider coexistence, reading/querying a temporary finalized clip through the Wide provider, dashboard Start/Flip/Stop controls, and full-app diagnostics ownership cleanup. Cross-package private provider registration is inspected through the test shell because Android intentionally hides it from the other app’s PackageManager.
- Wide-target emulator suites also passed real rear/front preview handoffs, metadata/ID/preview diagnostics with cancellation and saved reports, and the existing generated-H.264 MediaStore publish/playback/ownership/cleanup checks.
- Device acceptance remains: stop recording in the original app; install Wide over the snapcam probe; grant permissions and start recording; confirm Auto selects rear camera 3, inspect the finalized ultrawide clip in Photos, flip front/back and inspect orientation, then check power reconnection and a longer thermal run. Camera 3 metadata and visible preview were supplied from HD1925_11_F.05; continuous full-app ultrawide recording is not yet validated.
- Original settings/pairing/private footage remain in the original app. Wide settings, pairing and private footage are separate. No data migration or AWS deployment.

# Version 1.6.7 — single-camera flipping and ultrawide discovery

- Native build, six remaining model suites, APK alignment, and existing-certificate signatures passed. Removed dual-camera recorder orchestration, dual/Camera1/split-screen diagnostic paths, and obsolete split-screen pixel tests.
- Android 15 emulator: rewritten lens-debug integration passed scan metadata (package, field of view, zoom, physical routes), bounded IDs 0–9 queries, read-only package allowlist information, actual eight-second rear preview, cancellation, leaving during asynchronous opening, preserved recording/lens/retry settings, and saved reports after reopening.
- Camera-choice integration passed saved rear-lens selection and real rear → front → rear preview/service handoffs without enabling recording. Physical front/rear recording transitions, clip playback/orientation, and recording gaps still need handset acceptance; the emulator's rear recording route does not expose 1080p.
- Standalone snapcam APK emulator integration passed the same metadata, numeric-ID, preview, cancellation, and report-persistence checks under `org.codeaurora.snapcam`, installed alongside RB1 without a declared recording service.
- Optional standalone snapcam/aweme/meitu helpers reuse the lens-debug screen with only camera permission and no declared services or network access. Package identities and signing are verified during their build. Device-side auxiliary exposure on HD1925_11_F.05 remains unverified; a visible preview does not establish 1080p/30 recording support.
- No new main-app permissions or AWS deployment.

# Version 1.6.6 — manual camera diagnostics

- Native build, seven model suites, alignment, and existing-certificate APK signatures passed. Added deterministic tests for planar/semiplanar split-screen chroma layout, multiple chroma rows, input validation, and source preservation.
- Android 15 emulator with front/back emulated cameras: `./scripts/test-android.sh CameraDiagnosticsInstrumentation` passed capability/USB inventory, open-only probes, single-camera H.264 MP4 output, and terminal outcomes for the full dual-YUV, independent-encoder, split-screen-encoder, and Camera1 matrices. Unsupported combinations may correctly report FAIL/SKIP; integration success verifies the diagnostic runner, not that every combination works.
- Integration checks also cover Stop, temporary MP4 deletion, preserving enabled/lens/retry preferences, leaving during asynchronous camera opening, diagnostic ownership release, and report persistence after reopening. Camera1 textures are consumed on an EGL context so an unconsumed preview queue does not cause false frame-stall reports.
- OnePlus HD1925_11_F.05 acceptance: run Scan capabilities, Single-camera baseline, Open cameras only, Dual YUV frames, Two encoders, One split-screen encoder, then Camera1 preview. Copy the saved report. Physical dual-camera support, encoder throughput, long-run temperature, and firmware behavior remain unverified. USB recording and vendor/root/ROM changes are not implemented probes.
- No new Android permissions or AWS deployment.

# Version 1.6.3 — automatic widest camera and manual lens choice

- Native build, six model suites, APK alignment and existing-certificate v2/v3 signing passed. Version 1.6.3 / code 15; no new permissions or AWS changes.
- Camera-lens model checks cover different sensor sizes, 16:9 cropping, zoom-out, invalid/missing metadata, Auto widest choice, manual override, missing saved lenses, failed-route exclusions, deterministic fallback and exhaustion.
- Android 15 emulator: Settings/camera setup navigation, lens selector empty state when no 1080p/30 route is exposed, live aiming with the selected rear camera, hide/close, portrait/landscape layout and dashboard return passed. Screenshots were inspected. Both basic and virtual-scene emulator cameras lack the required 1920×1080 output, so the selector correctly offers only Auto there while preview still works.
- `./scripts/test-camera.sh` passed real Camera2/service integration: saved manual preference follows the available preview camera, survives Activity/service teardown, and explicit camera reconfiguration returns to Auto with fresh frames and without enabling recording. A test-selected preview route is used because the emulator cannot supply a recording-compatible route; this does not validate a manual recording-row UI choice or physical multi-camera hardware routing.
- Shared test-runner shell syntax passed, and the existing gallery integration passed from the same instrumentation APK.
- OnePlus acceptance remains: confirm all exposed rear lenses appear, compare Auto’s view with the widest available lens, choose each compatible manual camera, verify preview and recorded MP4 match, switch during recording and play both finalized clips around the brief handoff gap, then restart the app and check preference persistence. Also check actual ultrawide 1080p/30, physical stream routing and fallback behavior on that firmware. Some manufacturers restrict third-party auxiliary cameras; no unsupported camera is claimed available.

# Version 1.6.2 — Google Photos playback

- Native build, all five model suites, alignment and existing-key v2/v3 signing passed. Android version 1.6.2 / code 14, with no permission or AWS changes.
- Added `./scripts/test-gallery.sh` for real Android 10+ MediaStore integration checks. On the Android 15 emulator, a generated valid H.264 MP4 passed schema-1 migration/backfill, visible completed row and readable encoded frames, private-plus-public quota accounting, duplicate prevention, abandoned partial-row recovery, expired pending-row retry, published owner-deletion handling, leased/protected retention, loop overwrite and manual two-copy deletion.
- The test uses an isolated database, directory and preferences and removes its media rows. It does not require a hardware camera or change real clip metadata. The final source build and integration rerun passed.
- Photos itself and an Android 10/12 handset still require acceptance: update over 1.6.1, open RB1 to backfill existing READY clips, check Movies/RB1 Speed in Photos device folders, play a complete clip, verify the gallery status if space is insufficient, and confirm user folder-backup preferences. Android 8–9 keeps the existing in-app playback/share path without new storage permissions.

# Version 1.6.1 — settings, recording status and aiming preview

- Native Android build, five model suites (speed, driving features, brightness, dashcam policy and preview pixels), APK alignment and existing-key v2/v3 signatures passed. Version 1.6.1 / code 13; no new permissions.
- Movement checks cover continuous real movement, stops, unreliable/stale GPS, gaps and simulated speed. Settings uses the same tested session gate as Demo. Recording freshness checks distinguish startup, recent written video, a three-second stall and invalid timestamps. Pixel checks cover row padding, plane buffer offsets, planar/interleaved chroma and preserved input positions.
- Android 15 emulator: verified the dashboard has Settings in place of the four utility controls, all five Settings entries are present, preview receives rear-camera frames with aiming guides, preview hide/close and dashboard return, portrait/landscape layout, and preview-only service release/rebind across background/resume. Live GPS at 20 knots retained Settings before ten seconds, then hid Settings and Demo while the camera status stayed visible; resuming restored Settings. A 12-second Demo capture retained Settings and Exit demo. Preview screenshots were inspected for framing, guides and system-bar clearance.
- Emulator recording-startup failure (less than the existing 5 GiB reserve) stayed stopped without a false REC, returned to a working aiming preview, and allowed stop/disable, preview close and dashboard return without an app crash.
- A physical OnePlus check remains necessary: enable recording while powered, confirm red REC appears only once video is written, open/close aiming preview during recording, play the resulting clip to confirm continuous video and framing/orientation, unplug for ten seconds and check REC clears, reconnect, test both landscape directions, and check heat during a longer drive. The emulator does not validate this phone's hardware 1080p encoder or concurrent recording/preview stream support.
- No server or AWS changes were made.

# Deployment waiter correction

- Owner-issued enrollment records now include the existing API's optimistic-lock version (1). The integration test consumes the actual Lambda record without injecting that field; omission would prevent phone enrollment despite successful code issuance.
- Owner updates permit CloudFormation's conditional URL dependency only when the deployed URL definition is identical, its sole dependency change references the same function ARN, and the function has a non-replacing code-only change. Actual target changes, function configuration changes, replacements and direct URL edits remain blocked. Regression tests exercise these rejection cases; the full suite now has 33 tests.
- The guarded Lambda code update completed with UPDATE_COMPLETE using the correct update waiter. Post-deployment exact-template/dependency checks, all four sites, phone health, IAM URL configuration and unsigned-request rejection passed. A real signed issuance succeeded; a consistent DynamoDB read confirmed version 1, unused state and matching expiry without printing the code or AWS credentials. The unused test code was left to expire. All 33 tests passed during preparation.
- CloudFormation `DescribeChangeSet` does not return the `ChangeSetType` field previously used by both wrappers. A new stack therefore incorrectly selected the update-completion waiter and could appear stuck after successful creation. Both wrappers now select the waiter from pre-execution stack state: REVIEW_IN_PROGRESS uses create-complete; existing completed stacks use update-complete. Unsupported/in-progress states remain blocked.
- Added read-only `deploy:owner -- --verify` recovery, including exact deployed-template comparison, original shared/RB1 dependency snapshots, all existing site/API health, Lambda URL AWS_IAM configuration, and rejection of unsigned requests. It never resubmits a change set or creates a pairing code.
- All 32 API/CDK tests passed, including initial-create/update waiter regressions and owner verification checks. Following the owner's execution, AWS reported RB1OwnerStack CREATE_COMPLETE and the owner endpoint rejected unsigned POST with HTTP 403. The new full read-only owner verifier also passed exact-template/dependency comparisons and all four existing-site plus phone API checks. No deployment was submitted to diagnose the waiter issue.

# IAM-authenticated owner pairing endpoint

- Added a separate Lambda Function URL with AWS_IAM authentication and a profile-aware SigV4 laptop helper (`npm run pair`). No EC2/Caddy/Android changes are required for this feature.
- New tests cover missing/wrong-account IAM context, allowed route/body, code length/verifier/expiry, no secret logging, issuance limits, failure handling, one-time compatibility with the existing phone enrollment API, temporary-session SigV4 signing and the two required caller permissions.
- CDK tests reject public URLs, attachment to existing roles, broad DynamoDB writes, and EC2/SSM/table ownership. The owner execution role can write only PAIR#/PAIR-RATE# records and its own logs. The separate deployment guard preserves the original shared-host and RB1 snapshots and checks all four sites plus the phone API.
- Full local suite at initial implementation: 30 tests passed. Separate owner synth and packaging checks passed. Subsequent deployment, waiter recovery, enrollment-format correction and signed live issuance are recorded above. Existing live applications remain unchanged.

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
