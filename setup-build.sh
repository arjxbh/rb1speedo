#!/usr/bin/env bash
# Repository-local Linux x86-64 toolchain; no sudo or shell profile changes.
set -euo pipefail
cd "$(dirname "$0")"
[[ "$(uname -sm)" == 'Linux x86_64' ]] || { echo 'This setup script requires Linux x86-64.' >&2; exit 1; }
for tool in curl unzip tar zip sha256sum; do command -v "$tool" >/dev/null || { echo "Install $tool first." >&2; exit 1; }; done
mkdir -p .tools/downloads .tools/jdk .tools/android-sdk/cmdline-tools
download() { curl --fail --location --retry 3 --output "$2.part" "$1"; mv "$2.part" "$2"; }
verify() { echo "$1  $2" | sha256sum --check; }
if [[ ! -x .tools/jdk/bin/java ]]; then
    download 'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.21%2B9/OpenJDK17U-jdk_x64_linux_hotspot_17.0.21_9.tar.gz' .tools/downloads/jdk17.tar.gz
    verify 8b9b611d917e3dfd8419d3566b5949226f69b052fed7e109c1368add1dfe8711 .tools/downloads/jdk17.tar.gz
    tar -xzf .tools/downloads/jdk17.tar.gz --strip-components=1 -C .tools/jdk
fi
export JAVA_HOME="$PWD/.tools/jdk"
export PATH="$JAVA_HOME/bin:$PATH"
if [[ ! -x .tools/android-sdk/cmdline-tools/19.0/bin/sdkmanager ]]; then
    download 'https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip' .tools/downloads/commandlinetools.zip
    verify 7ec965280a073311c339e571cd5de778b9975026cfcbe79f2b1cdcb1e15317ee .tools/downloads/commandlinetools.zip
    unzip -q .tools/downloads/commandlinetools.zip -d .tools/android-sdk/cmdline-tools
    mv .tools/android-sdk/cmdline-tools/cmdline-tools .tools/android-sdk/cmdline-tools/19.0
fi
if [[ ! -f .tools/ecj-3.38.0.jar ]]; then
    download 'https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/3.38.0/ecj-3.38.0.jar' .tools/ecj-3.38.0.jar
fi
verify 97c566b120009c203a2fc8b291f4a9adbc171cf1ccb70f06f6b4e1828c00ce8e .tools/ecj-3.38.0.jar
# sdkmanager presents the Android SDK license for acceptance on first setup.
.tools/android-sdk/cmdline-tools/19.0/bin/sdkmanager --sdk_root="$PWD/.tools/android-sdk" 'platforms;android-35' 'build-tools;35.0.0'
echo 'Ready. Run ./build.sh; APK: build-native/RB1-Speed.apk'
