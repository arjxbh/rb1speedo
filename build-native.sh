#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
SDK="${1:?Pass the Android SDK directory}"
ECJ="${2:?Pass the ECJ 3.38.0 jar path}"
SDK="$(realpath "$SDK")"
ECJ="$(realpath "$ECJ")"
BT="$SDK/build-tools/35.0.0"
JAR="$SDK/platforms/android-35/android.jar"
rm -rf build-native
mkdir -p build-native/classes build-native/tests build-native/dex
java -jar "$ECJ" -8 -warn:none -bootclasspath "$JAR" -d build-native/classes app/src/main/java/club/midlife/rb1speed/*.java
java -jar "$ECJ" -8 -warn:none -d build-native/tests app/src/main/java/club/midlife/rb1speed/SpeedReading.java tests/SpeedReadingTest.java
java -cp build-native/tests SpeedReadingTest
"$BT/aapt2" compile --dir app/src/main/res -o build-native/resources.zip
"$BT/aapt2" link -o build-native/unsigned.apk -I "$JAR" --manifest app/src/main/AndroidManifest.xml --version-code 1 --version-name 1.0 build-native/resources.zip
find build-native/classes -name '*.class' -print0 | xargs -0 "$BT/d8" --lib "$JAR" --min-api 26 --output build-native/dex
(cd build-native/dex && zip -q ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 build-native/unsigned.apk build-native/aligned.apk
"$BT/apksigner" sign --ks development.jks --ks-pass pass:android --out build-native/RB1-Speed.apk build-native/aligned.apk
"$BT/apksigner" verify --verbose build-native/RB1-Speed.apk
