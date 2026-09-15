#!/usr/bin/env bash
# Run inside the documented container, after fetch.py. No host SDK/auth required.
set -euo pipefail
app_root=$(cd "$(dirname "$0")/../.." && pwd)
work="$app_root/.dsh-build"
python3 - "$app_root" <<'PY'
import json,pathlib,subprocess,sys
p=pathlib.Path(sys.argv[1])
for name, source in json.loads((p/'tools/dsh-build/sources.json').read_text()).items():
 d=p/'.dsh-build'/name
 assert subprocess.check_output(['git','-C',str(d),'rev-parse','HEAD'],text=True).strip()==source['commit'],name
 assert not subprocess.check_output(['git','-C',str(d),'status','--porcelain','--untracked-files=no']),name+' has modified source'
assert not (p/'.dsh-build/matrix-rust-sdk.aar').exists(), 'Refusing to reuse a prebuilt AAR'
PY
export ANDROID_HOME=${ANDROID_HOME:-/opt/android-sdk}
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_NDK="$ANDROID_HOME/ndk/26.3.11579264"
export ANDROID_NDK_HOME="$ANDROID_NDK" NDK_VERSION=26.3.11579264
export AWS_LC_SYS_NO_ASM=1 ANDROID_ABI=arm64-v8a ANDROID_PLATFORM=android-21
export CMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake"
export CFLAGS_aarch64_linux_android=-fintegrated-cc1
export CXXFLAGS_aarch64_linux_android=-fintegrated-cc1
export CARGO_BUILD_JOBS=${CARGO_BUILD_JOBS:-6}
# Keep target ABI explicit for aws-lc's CMake build, including ARM build hosts.
cat > "$work/cmake-android" <<'CMAKE'
#!/usr/bin/env bash
case "${1:-}" in
 --build|-E|--help|--version) exec "$ANDROID_HOME/cmake/3.22.1/bin/cmake" "$@";;
esac
exec "$ANDROID_HOME/cmake/3.22.1/bin/cmake" -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-21 "$@"
CMAKE
chmod +x "$work/cmake-android"
export CMAKE="$work/cmake-android" AWS_LC_SYS_CMAKE="$work/cmake-android"
export AWS_LC_SYS_CMAKE_TOOLCHAIN_FILE="$CMAKE_TOOLCHAIN_FILE"
cd "$work/matrix-rust-sdk"
cargo xtask kotlin build-android-library --profile dev --only-target aarch64-linux-android \
 --src-dir "$work/matrix-rust-components-kotlin/sdk/sdk-android/src/main" --package full-sdk
cd "$work/matrix-rust-components-kotlin"
./gradlew :sdk:sdk-android:assembleDebug --no-daemon --max-workers=6
cp sdk/sdk-android/build/outputs/aar/sdk-android-debug.aar "$work/matrix-rust-sdk.aar"
cd "$app_root"
./gradlew :app:assembleFdroidDebug --no-daemon --max-workers=6 -Dorg.gradle.jvmargs=-Xmx8g
sha256sum "$work/matrix-rust-sdk.aar" app/build/outputs/apk/fdroid/debug/app-fdroid-arm64-v8a-debug.apk
