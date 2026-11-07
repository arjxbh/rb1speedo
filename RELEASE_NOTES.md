Implements the first three tracked tasks:

- #1: Keep demo mode, but hide the DEMO entry button and touch target after 10 continuous seconds of fresh live movement above 1 mph. Returning to the app restores it; EXIT DEMO stays visible during previews.
- #2: Add an original Japanese-style bell at 75 mph, repeated every 1.5 seconds. It stops below 73 mph, with stale GPS, or when backgrounded. Live GPS only; no warning sound in demo mode.
- #3: Show 安全運転で、いってらっしゃい (“Drive safely—have a good trip”) at startup, and speak it when an installed offline Japanese text-to-speech voice is available. Text remains available without a voice. Uses media volume; no network voice or download.

The app retains its foreground-only GPS behavior and has no Internet permission. Updates use the existing prototype development signing key so they can install over previous builds signed with that key.

Build checks cover compilation, existing speed-model tests, new movement/warning timing and chime waveform checks, APK alignment, and signature verification. Physical-phone checks remain for button visibility, chime audibility/volume/Bluetooth routing, Japanese rendering/pronunciation, lifecycle/audio interruption handling, and installation over the existing app. Automated tests validate warning logic without requiring a high-speed road test.

Android 8.0 or newer. Personally built development-signed APK, not a Play Store release.
