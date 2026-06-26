#!/usr/bin/env bash
set -euo pipefail

export ANDROID_HOME=/opt/android-sdk
export ANDROID_SDK_ROOT=/opt/android-sdk
export ANDROID_NDK=/opt/android-sdk/ndk/26.3.11579264
export ANDROID_NDK_HOME="$ANDROID_NDK"
export GRADLE_USER_HOME=/workspace/.gradle-myclaw
export AWS_LC_SYS_NO_ASM=1
export CMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake"
export ANDROID_ABI=arm64-v8a
export ANDROID_PLATFORM=android-21
export CFLAGS_aarch64_linux_android="-fintegrated-cc1"
export CXXFLAGS_aarch64_linux_android="-fintegrated-cc1"

cat > /tmp/cmake-android-arm64 <<'EOF'
#!/usr/bin/env bash
case "${1:-}" in
    --build|-E|--help|--version)
        exec /opt/android-sdk/cmake/3.22.1/bin/cmake "$@"
        ;;
esac
exec /opt/android-sdk/cmake/3.22.1/bin/cmake \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-21 \
    -DCMAKE_VERBOSE_MAKEFILE=ON \
    "$@"
EOF
chmod +x /tmp/cmake-android-arm64
export CMAKE=/tmp/cmake-android-arm64
export AWS_LC_SYS_CMAKE="$CMAKE"
export AWS_LC_SYS_CMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake"

mkdir -p "$GRADLE_USER_HOME"
cat > "$GRADLE_USER_HOME/gradle.properties" <<'EOF'
android.aapt2FromMavenOverride=/opt/android-sdk/build-tools/37.0.0/aapt2
EOF

cd /workspace/matrix-rust-sdk
cargo xtask kotlin build-android-library \
    --profile dev \
    --only-target aarch64-linux-android \
    --src-dir /workspace/matrix-rust-components-kotlin/sdk/sdk-android/src/main \
    --package full-sdk

cd /workspace/matrix-rust-components-kotlin
find sdk/sdk-android/src/main/jniLibs -type f ! -name 'libmatrix_sdk_ffi.so' -delete
./gradlew :sdk:sdk-android:assembleDebug --no-daemon

mkdir -p /workspace/element-x-android/libraries/rustsdk
cp sdk/sdk-android/build/outputs/aar/sdk-android-debug.aar \
    /workspace/element-x-android/libraries/rustsdk/matrix-rust-sdk.aar
