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
