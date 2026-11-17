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
for probe in snapcam aweme meitu; do
    case "$probe" in
        snapcam) probe_package=org.codeaurora.snapcam ;;
        aweme) probe_package=com.ss.android.ugc.aweme ;;
        meitu) probe_package=com.meitu.meiyancamera ;;
    esac
    probe_dir="build-native/probe-$probe"
    mkdir -p "$probe_dir"
    cat > "$probe_dir/AndroidManifest.xml" <<MANIFEST
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="$probe_package">
    <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-feature android:name="android.hardware.camera" android:required="false" />
    <application android:label="RB1 Lens Probe · $probe" android:theme="@android:style/Theme.Material.NoActionBar" android:icon="@drawable/icon" android:allowBackup="false">
        <activity android:name="club.midlife.rb1speed.CameraDiagnosticsActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
MANIFEST
    "$BT/aapt2" link -o "$probe_dir/unsigned.apk" -I "$JAR" --manifest "$probe_dir/AndroidManifest.xml" --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" build-native/resources.zip
    (cd build-native/dex && zip -q "../probe-$probe/unsigned.apk" classes.dex)
    "$BT/zipalign" -f 4 "$probe_dir/unsigned.apk" "$probe_dir/aligned.apk"
    "$BT/apksigner" sign --ks development.jks --ks-key-alias rb1 --ks-pass pass:android --out "build-native/RB1-LensProbe-$probe.apk" "$probe_dir/aligned.apk"
    "$BT/zipalign" -c 4 "build-native/RB1-LensProbe-$probe.apk"
    "$BT/apksigner" verify "build-native/RB1-LensProbe-$probe.apk"
    (cd build-native && sha256sum "RB1-LensProbe-$probe.apk" > "RB1-LensProbe-$probe.apk.sha256")
done
