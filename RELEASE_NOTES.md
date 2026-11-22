Dashcam audio and dashboard alerts (1.6.12).

- Opt in to microphone audio in **Settings → Camera & clips**. Grant microphone permission and start a new recording to include cabin audio in new MP4s, including shared and uploaded copies. Audio is off by default.
- Play the bundled Japanese startup greeting after five seconds, even on phones without an installed Japanese speech voice. **Settings → Debug** shows greeting playback status.
- Start tracking the drive's maximum speed on battery or external power after the first valid GPS speed above 1 mph. The three-minute stopped-and-unplugged reset still applies.
- Show a soft amber ring that expands and fades with each overspeed chime. When Android animations are disabled, the ring fades in place. Preview the effect while parked in **Demo**.

Current-drive maximum speed (1.6.11).

- Add a small outer-scale maximum-speed pointer and Max mph caption across all five skins. Preserve gauge size, artwork, scale and current-speed needle. Use amber on dark faces and blue on the white Integra face.
- Start a drive at the first accepted live GPS movement above 1 mph after external power connects. Clear its maximum after three continuous minutes stopped and unplugged. Retain it across Settings, rotation and reopening within the same phone boot. GPS loss/backgrounding restarts stationary confirmation; unobserved movement is not counted.
- Keep Demo maximum separate from the live drive. Ignore rejected or stale GPS readings. Peaks above 120 mph keep their actual number while the pointer stays at the end of the scale.
- No new permissions, camera behavior changes or AWS deployment.

Clearer driving-screen status (1.6.10).

- Disable Start recording while the camera service is running or closing, and disable Stop recording while recording is off. Give unavailable controls a distinct muted style while keeping all three camera buttons visible.
- Show the active lens below the recording indicator, or the stopped/error message when the camera is off. Tap either camera status line for readable details and direct access to Camera & clips. Distinguish initial startup from waiting for video after frames stop arriving; only fresh written video shows REC.
- Show GPS accuracy alongside the connected state. Tapping a healthy GPS connection opens its status instead of clearing the reading/restarting GPS; enabling/retrying GPS and exiting Demo retain their existing actions.
- Show external-power/battery state in the footer instead of repeating connected GPS accuracy, and fit long skin titles to the header. Status dialogs close when leaving the dashboard.
- Keep existing gauge artwork, skin layouts, units, automatic recording and camera selection. No new permissions or AWS deployment.

Wide camera access is now the default (1.6.9).

- Install RB1-Speed.apk. It now uses the working org.codeaurora.snapcam identity, is named RB1 Speed, and upgrades RB1 Speed · Wide in place using the existing signing certificate. Keep Wide installed while updating to retain its settings, pairing and private recordings.
- Use the same ultrawide discovery and widest compatible rear-lens selection that worked in Wide; preserve front/rear flipping, power-based recording, uploads, playback/sharing and lens debugging. Camera IDs are not hardcoded.
- Publish one APK and checksum. Remove separate Wide/helper packaging scripts and variant-specific download routing. Native and Gradle builds now share the same default application identity; the Java namespace stays unchanged.
- The legacy club.midlife.rb1speed installation remains separate and untouched. Its private settings and footage are not automatically migrated; stop its recording before using the current app. Install this release manually once if upgrading from Wide v1.6.8, whose Updates button targets the previous variant filename.
- No new Android permissions or AWS deployment. Long-duration power/thermal behavior still needs normal device acceptance.

Full snapcam app for OnePlus auxiliary lenses (1.6.8).

- On OnePlus HD1925, install RB1-Speed-snapcam.apk and open RB1 Speed · Wide. This is the full speedometer/dashcam app under org.codeaurora.snapcam, the package identity confirmed to expose the phone’s rear ultrawide camera 3. Auto should choose camera 3 using the existing widest compatible rear-lens ranking; camera IDs are not hardcoded.
- The Wide APK upgrades the old snapcam lens probe and coexists with the original RB1-Speed.apk. Stop recording in the original first. Preferences, permissions, pairing and private footage are separate; enable recording in Wide. Original app data is not migrated or removed.
- Retain all activities, recording/upload services, power receiver, camera flip, clip playback/sharing and lens debugging. Use a distinct clip-provider authority and explicit full-app metadata so Debug pauses Wide’s own recorder. Updates downloads the APK matching the installed variant.
- Release the normal and Wide APKs with checksums. Standalone lens helpers remain available in v1.6.7/source but are not included in this release to avoid confusing them with the full Wide app.
- Camera 3 metadata advertises 1080p and 30 fps, and its ultrawide preview has been confirmed on HD1925_11_F.05. Actual full-app ultrawide recording, saved clip playback, orientation and a longer thermal/power test still require phone acceptance. No AWS deployment.

Camera flipping and ultrawide lens debugging (1.6.7).

- Replace Start 360 recording with Flip camera. One camera records at a time; switching closes the current clip before opening the other camera. While stopped, Flip selects the next manual camera without starting recording. Automatic starts default to rear.
- Remove concurrent front/rear recording, the dual-camera diagnostic matrix, Camera1 dual previews, split-screen encoding, and their obsolete model tests.
- Add Debug → Ultrawide lens debug: exposed/physical camera metadata, sensor/focal field estimates, zoom-out routes, a bounded numeric-ID metadata probe, and an eight-second visible preview for each discovered rear candidate. Copyable reports persist separately from the previous dual-camera logs. Debug probes save no footage and do not change lens preferences.
- Add read-only package-access diagnostics and optional standalone lens helpers using snapcam, aweme, and meitu package identities. Stop RB1 recording before running a helper. Helpers have camera permission only, do not replace the main app, and do not modify firmware or require root. Package conflicts and firmware restrictions may prevent a helper from installing or accessing additional lenses.
- Auxiliary-lens access and 1080p/30 recording remain unverified on HD1925_11_F.05; metadata/preview access is not a guarantee of recording support. No AWS deployment.

Manual camera diagnostics (1.6.6).

- Add Settings → Debug → Camera diagnostics with individually selectable capability scan, single-camera recording baseline, open-only pairs, dual YUV capture, two independent encoders, one split-screen encoder, and legacy Camera1 preview tests.
- Exercise every directly exposed rear/front pair in both opening orders. Frame and independent-encoder tests try supported 1080p, 720p and 480p configurations; split-screen and Camera1 tests use 480p inputs. Capture tests run for three seconds per configuration with bounded Camera2 setup timeouts and cancellation.
- Report camera/build metadata, logical physical-camera IDs, supported sizes/FPS, advertised concurrent pairs, attached USB interfaces, exact failure stages/codes, frame/sample counts and temporary MP4 sizes. Results persist across screen visits and can be copied.
- Pause regular dashcam recording while diagnostics is visible, preserve enabled/lens/retry preferences, and release diagnostic cameras when leaving. Test footage is private cache data, deleted after each probe and never uploaded.
- USB recording, CameraX/NDK rewrites, hidden vendor APIs, root and ROM changes are not implemented diagnostic methods; the screen states these limits explicitly. A three-second PASS does not establish long-term reliability or OnePlus firmware compatibility.

Dual-camera startup compatibility (1.6.5).

- Open both cameras before configuring either recording session, following the Camera2 concurrent startup order.
- When firmware advertises no concurrent camera pairs, try one directly exposed rear/front 1080p pair rather than rejecting the request before opening cameras. This is an experimental compatibility attempt, not a guarantee of dual-camera support on OnePlus firmware.
- Use encoder-only streams for dual recording to reduce camera resource pressure; aiming preview is unavailable in this mode.
- Show an immediate start acknowledgement, visible preflight errors and runtime camera failures. Abort stalled dual startup after 15 seconds. Include device/build details in unsupported-pair and timeout messages.
- Preflight rejection leaves an active rear recording running; runtime rejection after switching can stop both streams. Rear-only recording remains available through Start recording.
- Native build, six existing test suites, APK alignment and signature checks pass. Actual dual recording on HD1925_11_F.05 remains unverified and requires phone testing. No AWS deployment or new permissions.

Dashboard recording controls (1.6.4).

- Add Start recording, Start 360 recording and Stop recording buttons to the active dashboard.
- Start recording selects the widest compatible rear camera. Existing enabled/external-power automatic-start logic continues to start rear-only recording.
- Start 360 recording saves separate rear and front 1080p/H.264 streams on Android 11+ devices exposing a compatible concurrent camera pair. Unsupported configurations stop with a camera status message; tap the recording indicator for details.
- Show REC 360 only while both streams are writing fresh video. Stop recording closes both streams and disables automatic restart until recording is enabled again.
- Preserve saved manual lens selection for settings and preview; dashboard start buttons return to Auto.
- Native build, six test suites, APK alignment and signature verification passed. Concurrent recording and dashboard layout still require physical-device acceptance. No AWS deployment or new permissions are required.

Automatic widest rear camera (1.6.3).

- Default to the widest Android-exposed rear camera compatible with 1080p/30 recording, using sensor size and focal length rather than camera ID order. Support physical lenses behind logical cameras and supported logical zoom-out ranges.
- Add a persistent camera selector under Settings → Camera & clips → Camera lens; aiming preview uses the selected lens too.
- Finish the current clip before changing lenses. Auto skips unavailable cameras; missing saved choices fall back to Auto.
- Show selected lens, approximate field of view and fallback status. Available lenses depend on device firmware. No new permissions or AWS deployment are required.

Google Photos playback (1.6.2).

- Publish completed MP4 clips to Movies/RB1 Speed on Android 10+, visible in Google Photos under Collections → On this device. Backfill existing completed clips when the app opens.
- Hide incomplete copies until publication finishes and recover interrupted publication without duplicate videos.
- Preserve private upload originals; include both copies in the loop quota and remove both during loop overwrite or manual local deletion. Protected incidents remain protected from automatic overwrite.
- Respect gallery copies deleted by the owner. Google Photos backup stays under the owner’s Photos settings. No new permissions or AWS deployment are required. Android 8–9 retains in-app playback and sharing.

Settings and camera aiming (1.6.1).

- Group Skins, Camera & clips, Debug and Updates under Settings, with a direct Aim camera preview entry. Settings and Demo hide after ten continuous seconds of reliable live GPS movement above 1 mph; simulation does not count. Returning to the dashboard restores them.
- Show a persistent red REC indicator only while video frames are being written, with a three-second freshness limit. Stopped/starting states remain distinct.
- Add a rear-camera aiming preview with center/thirds guides. Preview works without saving video, shares an active recording session, and releases its frame capture when closed.
- Correct camera setup insets on Android 15. No new Android permissions or AWS deployment are required.

Dashcam and private Wi-Fi cloud copies (1.6.0, pending deployment/device acceptance).

- Optional silent rear-camera recording alongside the speedometer: 1080p/30 fps, 15-minute MP4s with shorter last-trip clips, one-minute recoverable recording fragments, a foreground notification and ten-second power-disconnect debounce.
- Configurable local loop, free-space reserve, incident protection, playback/share and local cleanup controls. Enabling recording explains possible overwrite before upload.
- Durable Wi-Fi-only, resumable multipart uploads after parking, with battery/time/thermal limits, signed part checksums and verified remote completion.
- One-time device pairing, Android Keystore-protected refresh credentials, automatic 15-minute API access renewal, 90-day inactivity expiry and owner revocation. No AWS keys on the phone.
- Additive CDK stack/API for the existing shared EC2 host, with private S3 storage, 30-day routine retention, separate metadata, audit and budget monitoring. Guarded deployment requires exact change-set review and preserves/verifies Piggy Ledger, Midlife Motors, Kaedama and The Cranky Goose.
- Boot schedules uploads and offers an open-dashboard reminder when notifications are allowed; the app does not launch a camera from boot or guarantee automatic power-on after battery exhaustion.

Local native build/model checks, API and CDK safety checks, synth, alignment and existing-key signing passed. No live AWS deployment, real S3 upload or physical camera/thermal soak has been performed. Review docs/DASHCAM_DESIGN.md and infra/README.md before deployment. New permissions support camera, foreground recording, network/upload scheduling, notifications and a bounded recording wake lock; no microphone permission is requested.

Debug controls for mounted-phone setup (1.5.1).

- Open Debug alongside Demo for live power, screen timeout, actual media volume, light-sensor, greeting and requested brightness status.
- Reapply 70% volume or preview dim/bright output for five seconds. Brightness previews reset automatically and when closing or backgrounding.
- Volume adjustments persist; debug brightness overrides do not. No additional permissions.

Windshield-mounted dashboard support (1.5).

- Keeps the screen on while the dashboard is visible and external power is connected, including when the battery is full. Unplugging restores Android’s normal screen timeout.
- Schedules the drive-safely greeting about five seconds after opening or the first eligible power connection. Power cycling during the same screen-on session does not repeat it. Screen-off rearms the greeting; backgrounding still cancels pending speech. Requires an installed offline Japanese TTS voice.
- Sets media volume to the nearest step to 70% on fresh launches. The setting affects other media apps and remains after exit.
- Uses the phone’s ambient light sensor to dim in darkness and brighten in daylight. Without sensor data, uses approximate GPS-based sunset/sunrise; without a GPS position, uses 7 pm–7 am local time. Brightness changes apply only to the app.
- Retains portrait and landscape layouts. Open the app before driving; automatic launch/unlock on ignition is not included.

Native build, all three test suites, APK alignment and signing passed. Physical power, sensor, timeout and speech behavior still require verification on the mounted phone.

Android 8.0 or newer. Uses the existing signing key: install RB1-Speed.apk over the current app without uninstalling it.
