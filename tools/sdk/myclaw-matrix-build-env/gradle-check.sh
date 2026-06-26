#!/usr/bin/env bash
set -euo pipefail

cd /workspace/element-x-android

export ANDROID_HOME=/opt/android-sdk
export ANDROID_SDK_ROOT=/opt/android-sdk
export ANDROID_NDK=/opt/android-sdk/ndk/26.3.11579264
export ANDROID_NDK_HOME="$ANDROID_NDK"
export GRADLE_USER_HOME=/workspace/.gradle-myclaw

mkdir -p "$GRADLE_USER_HOME"
cat > "$GRADLE_USER_HOME/gradle.properties" <<'EOF'
android.aapt2FromMavenOverride=/opt/android-sdk/build-tools/37.0.0/aapt2
EOF

./gradlew "$@" --no-daemon
