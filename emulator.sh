#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="$PWD/.tools/jdk"
export ANDROID_HOME="$PWD/.tools/android-sdk"
export ANDROID_USER_HOME="$PWD/.tools/android-user"
export ANDROID_AVD_HOME="$PWD/.tools/avd"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
mkdir -p "$ANDROID_USER_HOME" "$ANDROID_AVD_HOME"
case "${1:-start}" in
    create)
        if [[ -f "$ANDROID_AVD_HOME/RB1_API_35.ini" ]]; then
            echo 'RB1_API_35 already exists.'
        else
            "$ANDROID_HOME/cmdline-tools/19.0/bin/avdmanager" create avd \
                --name RB1_API_35 --package 'system-images;android-35;google_apis;x86_64' \
                --device pixel_5 < /dev/null
        fi
        ;;
    start)
        shift "$(( $# > 0 ? 1 : 0 ))"
        exec "$ANDROID_HOME/emulator/emulator" -avd RB1_API_35 -gpu software \
            -memory 2048 -no-boot-anim -no-audio -no-metrics "$@"
        ;;
    adb)
        shift
        exec "$ANDROID_HOME/platform-tools/adb" "$@"
        ;;
    *) echo 'Usage: ./emulator.sh [create|start [emulator options]|adb [ADB arguments]]' >&2; exit 1 ;;
esac
