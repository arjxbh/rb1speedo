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
