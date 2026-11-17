#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
APP_VARIANT="${2:-standard}"
[[ "$APP_VARIANT" == standard || "$APP_VARIANT" == snapcam ]] || { echo 'Variant must be standard or snapcam' >&2; exit 2; }
case "${1:-GalleryInstrumentation}" in
    GalleryInstrumentation) RUNNER=GalleryInstrumentation; SUCCESS='Gallery integration: all checks passed' ;;
    CameraChoiceInstrumentation) RUNNER=CameraChoiceInstrumentation; SUCCESS='Camera selection integration: all checks passed' ;;
    CameraDiagnosticsInstrumentation) RUNNER=CameraDiagnosticsInstrumentation; SUCCESS='Camera diagnostics integration: all checks passed' ;;
    FullAppInstrumentation) RUNNER=FullAppInstrumentation; SUCCESS='Full app integration: all checks passed' ;;
    LensProbeInstrumentation) RUNNER=LensProbeInstrumentation; SUCCESS='Camera diagnostics integration: all checks passed' ;;
    *) echo 'Choose GalleryInstrumentation, CameraChoiceInstrumentation, CameraDiagnosticsInstrumentation LensProbeInstrumentation or FullAppInstrumentation' >&2; exit 2 ;;
esac
./build.sh
if [[ "$APP_VARIANT" == snapcam ]]; then ./build-snapcam.sh
elif [[ "$RUNNER" == LensProbeInstrumentation ]]; then ./build-lens-probes.sh; fi
export JAVA_HOME="$PWD/.tools/jdk"
export PATH="$JAVA_HOME/bin:$PATH"
SDK="$PWD/.tools/android-sdk"
BT="$SDK/build-tools/35.0.0"
JAR="$SDK/platforms/android-35/android.jar"
OUT="$PWD/build-native/android-tests"
mkdir -p "$OUT/classes" "$OUT/dex"
java -jar .tools/ecj-3.38.0.jar -8 -warn:none -bootclasspath "$JAR" -classpath build-native/classes -d "$OUT/classes" tests/android/*.java
python3 - "$APP_VARIANT" "$OUT/AndroidManifest.xml" <<'PYTEST'
from pathlib import Path
import sys
manifest=Path('tests/android/AndroidManifest.xml').read_text()
if sys.argv[1]=='snapcam':
    manifest=manifest.replace('android:targetPackage="club.midlife.rb1speed"','android:targetPackage="org.codeaurora.snapcam"')
Path(sys.argv[2]).write_text(manifest)
PYTEST
"$BT/aapt2" link -o "$OUT/test-unsigned.apk" -I "$JAR" --manifest "$OUT/AndroidManifest.xml"
find "$OUT/classes" -name '*.class' -print0 | xargs -0 "$BT/d8" --lib "$JAR" --classpath build-native/classes --min-api 29 --output "$OUT/dex"
(cd "$OUT/dex" && zip -q ../test-unsigned.apk classes.dex)
"$BT/zipalign" -f 4 "$OUT/test-unsigned.apk" "$OUT/test-aligned.apk"
"$BT/apksigner" sign --ks development.jks --ks-key-alias rb1 --ks-pass pass:android --out "$OUT/test.apk" "$OUT/test-aligned.apk"
if [[ "$APP_VARIANT" == snapcam ]]; then
    "$SDK/platform-tools/adb" -e install -r -g build-native/RB1-Speed.apk
    "$SDK/platform-tools/adb" -e install -r -g build-native/RB1-Speed-snapcam.apk
elif [[ "$RUNNER" == LensProbeInstrumentation ]]; then
    "$SDK/platform-tools/adb" -e install -r -g build-native/RB1-LensProbe-snapcam.apk
else
    "$SDK/platform-tools/adb" -e install -r -g build-native/RB1-Speed.apk
fi
"$SDK/platform-tools/adb" -e install -r "$OUT/test.apk"
"$SDK/platform-tools/adb" -e shell am instrument -w "club.midlife.rb1speed.tests/club.midlife.rb1speed.$RUNNER" | tee "$OUT/result.txt"
grep -q "$SUCCESS" "$OUT/result.txt"
