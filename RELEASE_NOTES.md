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
