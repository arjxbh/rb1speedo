#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="$PWD/.tools/jdk"
export PATH="$JAVA_HOME/bin:$PATH"
if [[ ! -x "$JAVA_HOME/bin/java" || ! -f .tools/android-sdk/platforms/android-35/android.jar || ! -f .tools/ecj-3.38.0.jar ]]; then
    echo 'Build tools missing. Run ./setup-build.sh first.' >&2
    exit 1
fi
exec ./build-native.sh .tools/android-sdk .tools/ecj-3.38.0.jar
