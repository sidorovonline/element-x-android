# MyClaw Matrix Android Build Environment

This directory contains the container setup used to build a local Matrix Rust
SDK Android AAR for Element X. It keeps Rust, Android, and Gradle tooling inside
the container and project-mounted directories under `/home/victor/dev`.

Build the image:

```bash
docker build -t myclaw-matrix-android-sdk-builder:latest /home/victor/dev/element-x-android/tools/sdk/myclaw-matrix-build-env
```

Build and copy the SDK AAR:

```bash
docker run --rm \
    -v /home/victor/dev/element-x-android:/workspace/element-x-android \
    -v /home/victor/dev/matrix-rust-sdk:/workspace/matrix-rust-sdk \
    -v /home/victor/dev/matrix-rust-components-kotlin:/workspace/matrix-rust-components-kotlin \
    myclaw-matrix-android-sdk-builder:latest \
    /workspace/element-x-android/tools/sdk/myclaw-matrix-build-env/build-sdk-aar.sh
```

The script writes the AAR to:

```text
/home/victor/dev/element-x-android/libraries/rustsdk/matrix-rust-sdk.aar
```

Run Element X Gradle checks with the same container:

```bash
docker run --rm \
    -v /home/victor/dev/element-x-android:/workspace/element-x-android \
    myclaw-matrix-android-sdk-builder:latest \
    /workspace/element-x-android/tools/sdk/myclaw-matrix-build-env/gradle-check.sh <gradle task>
```
