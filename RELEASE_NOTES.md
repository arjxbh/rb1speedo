Updates speed-warning audio and startup media volume.

- The 75 mph chime now mixes with music without requesting audio focus or asking Android Auto to duck the music. The spoken greeting retains temporary audio focus.
- Fresh app launches set phone media volume to the nearest available step to 50%. Rotation and routine returns do not reset it. This affects other media apps and is not restored on exit; head-unit volume may be separate.

Native build, both test suites, APK alignment, and signing verification passed. Physical phone and Sony/Android Auto playback still need verification; use Demo while parked to check chime mixing.

Android 8.0 or newer. Uses the existing signing key: install RB1-Speed.apk over the current app without uninstalling it.
