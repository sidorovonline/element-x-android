# Self-contained x86_64 build recipe; existing equivalent ARM Android tooling
# can also run build.sh. No local SDK source, AAR, or credentials enter this image.
FROM --platform=linux/amd64 eclipse-temurin:21-jdk-jammy
ENV DEBIAN_FRONTEND=noninteractive ANDROID_HOME=/opt/android-sdk ANDROID_SDK_ROOT=/opt/android-sdk
ENV CARGO_HOME=/opt/cargo RUSTUP_HOME=/opt/rustup
ENV PATH=/opt/cargo/bin:/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:$PATH
RUN apt-get update && apt-get install -y --no-install-recommends \
 bash build-essential ca-certificates cmake curl git libsqlite3-dev perl pkg-config python3 unzip \
 && rm -rf /var/lib/apt/lists/*
RUN mkdir -p /opt/android-sdk/cmdline-tools \
 && curl -fSL https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o /tmp/sdk.zip \
 && echo '2d2d50857e4eb553af5a6dc3ad507a17adf43d115264b1afc116f95c92e5e258  /tmp/sdk.zip' | sha256sum -c - \
 && unzip -q /tmp/sdk.zip -d /opt/android-sdk/cmdline-tools \
 && mv /opt/android-sdk/cmdline-tools/cmdline-tools /opt/android-sdk/cmdline-tools/latest \
 && rm /tmp/sdk.zip
RUN yes | sdkmanager --licenses >/dev/null \
 && sdkmanager 'platform-tools' 'platforms;android-34' 'platforms;android-35' 'platforms;android-36' 'platforms;android-37' \
 'build-tools;34.0.0' 'build-tools;35.0.0' 'build-tools;37.0.0' 'ndk;26.3.11579264' 'cmake;3.22.1'
RUN curl --fail --silent --show-error https://sh.rustup.rs -o /tmp/rustup-init.sh \
 && sh /tmp/rustup-init.sh -y --no-modify-path --profile minimal --default-toolchain 1.93.0 \
 && rm /tmp/rustup-init.sh \
 && rustup target add aarch64-linux-android \
 && cargo install cargo-ndk --version 4.1.2 --locked
WORKDIR /app
ENTRYPOINT ["bash"]
