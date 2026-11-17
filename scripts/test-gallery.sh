#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
./build.sh
export JAVA_HOME="$PWD/.tools/jdk"
export PATH="$JAVA_HOME/bin:$PATH"
SDK="$PWD/.tools/android-sdk"
BT="$SDK/build-tools/35.0.0"
JAR="$SDK/platforms/android-35/android.jar"
OUT="$PWD/build-native/gallery-tests"
mkdir -p "$OUT/classes" "$OUT/dex"
java -jar .tools/ecj-3.38.0.jar -8 -warn:none -bootclasspath "$JAR" -classpath build-native/classes -d "$OUT/classes" tests/android/GalleryInstrumentation.java
"$BT/aapt2" link -o "$OUT/test-unsigned.apk" -I "$JAR" --manifest tests/android/AndroidManifest.xml
find "$OUT/classes" -name '*.class' -print0 | xargs -0 "$BT/d8" --lib "$JAR" --classpath build-native/classes --min-api 29 --output "$OUT/dex"
(cd "$OUT/dex" && zip -q ../test-unsigned.apk classes.dex)
"$BT/zipalign" -f 4 "$OUT/test-unsigned.apk" "$OUT/test-aligned.apk"
"$BT/apksigner" sign --ks development.jks --ks-key-alias rb1 --ks-pass pass:android --out "$OUT/test.apk" "$OUT/test-aligned.apk"
"$SDK/platform-tools/adb" -e install -r build-native/RB1-Speed.apk
"$SDK/platform-tools/adb" -e install -r "$OUT/test.apk"
"$SDK/platform-tools/adb" -e shell am instrument -w club.midlife.rb1speed.tests/club.midlife.rb1speed.GalleryInstrumentation | tee "$OUT/result.txt"
grep -q 'Gallery integration: all checks passed' "$OUT/result.txt"
