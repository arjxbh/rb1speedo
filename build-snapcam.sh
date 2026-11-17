#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
SDK="$PWD/.tools/android-sdk"
BT="$SDK/build-tools/35.0.0"
JAR="$SDK/platforms/android-35/android.jar"
export JAVA_HOME="$PWD/.tools/jdk"
export PATH="$JAVA_HOME/bin:$PATH"
[[ -f build-native/dex/classes.dex && -f build-native/resources.zip ]] || { echo 'Run ./build.sh first.' >&2; exit 1; }
VERSION_CODE="$(sed -n 's/^versionCode=//p' version.properties)"
VERSION_NAME="$(sed -n 's/^versionName=//p' version.properties)"
mkdir -p build-native/snapcam
python3 - <<'PY'
import xml.etree.ElementTree as ET
ET.register_namespace('android', 'http://schemas.android.com/apk/res/android')
android = '{http://schemas.android.com/apk/res/android}'
tree = ET.parse('app/src/main/AndroidManifest.xml')
root = tree.getroot()
original = root.get('package')
root.set('package', 'org.codeaurora.snapcam')
application = root.find('application')
application.set(android + 'label', 'RB1 Speed · Wide')
# Keep Java component names in their original namespace; only application identity changes.
for element in application:
    name = element.get(android + 'name', '')
    if name.startswith('.'):
        element.set(android + 'name', original + name)
    if element.tag == 'provider':
        element.set(android + 'authorities', 'org.codeaurora.snapcam.clips')
tree.write('build-native/snapcam/AndroidManifest.xml', encoding='utf-8', xml_declaration=True)
PY
"$BT/aapt2" link -o build-native/snapcam/unsigned.apk -I "$JAR" --manifest build-native/snapcam/AndroidManifest.xml --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" build-native/resources.zip
(cd build-native/dex && zip -q ../snapcam/unsigned.apk classes.dex)
"$BT/zipalign" -f 4 build-native/snapcam/unsigned.apk build-native/snapcam/aligned.apk
"$BT/apksigner" sign --ks development.jks --ks-key-alias rb1 --ks-pass pass:android --out build-native/RB1-Speed-snapcam.apk build-native/snapcam/aligned.apk
"$BT/zipalign" -c 4 build-native/RB1-Speed-snapcam.apk
"$BT/apksigner" verify build-native/RB1-Speed-snapcam.apk
(cd build-native && sha256sum RB1-Speed-snapcam.apk > RB1-Speed-snapcam.apk.sha256)
