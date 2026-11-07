#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
SDK="${1:?Pass the Android SDK directory}"
ECJ="${2:?Pass the ECJ 3.38.0 jar path}"
SDK="$(realpath "$SDK")"
ECJ="$(realpath "$ECJ")"
BT="$SDK/build-tools/35.0.0"
JAR="$SDK/platforms/android-35/android.jar"
VERSION_CODE="$(sed -n 's/^versionCode=//p' version.properties)"
VERSION_NAME="$(sed -n 's/^versionName=//p' version.properties)"
[[ "$VERSION_CODE" =~ ^[1-9][0-9]*$ && "$VERSION_NAME" =~ ^[0-9]+\.[0-9]+(\.[0-9]+)?$ ]] || { echo 'Invalid version.properties' >&2; exit 1; }
rm -rf build-native
mkdir -p build-native/classes build-native/tests build-native/dex
java -jar "$ECJ" -8 -warn:none -bootclasspath "$JAR" -d build-native/classes app/src/main/java/club/midlife/rb1speed/*.java
java -jar "$ECJ" -8 -warn:none -d build-native/tests app/src/main/java/club/midlife/rb1speed/SpeedReading.java app/src/main/java/club/midlife/rb1speed/DriveSession.java app/src/main/java/club/midlife/rb1speed/SpeedWarning.java app/src/main/java/club/midlife/rb1speed/ChimeSound.java tests/*.java
java -cp build-native/tests SpeedReadingTest
java -cp build-native/tests DrivingFeaturesTest
"$BT/aapt2" compile --dir app/src/main/res -o build-native/resources.zip
"$BT/aapt2" link -o build-native/unsigned.apk -I "$JAR" --manifest app/src/main/AndroidManifest.xml --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" build-native/resources.zip
find build-native/classes -name '*.class' -print0 | xargs -0 "$BT/d8" --lib "$JAR" --min-api 26 --output build-native/dex
(cd build-native/dex && zip -q ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 build-native/unsigned.apk build-native/aligned.apk
"$BT/apksigner" sign --ks development.jks --ks-key-alias rb1 --ks-pass pass:android --out build-native/RB1-Speed.apk build-native/aligned.apk
"$BT/zipalign" -c -v 4 build-native/RB1-Speed.apk
"$BT/apksigner" verify --verbose --print-certs build-native/RB1-Speed.apk
(cd build-native && sha256sum RB1-Speed.apk > RB1-Speed.apk.sha256)
