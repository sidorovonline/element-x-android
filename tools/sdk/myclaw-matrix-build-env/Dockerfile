FROM ubuntu:24.04

ARG ANDROID_CMDLINE_TOOLS_ZIP=https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
ARG ANDROID_PLATFORM=android-37.0
ARG ANDROID_BUILD_TOOLS=37.0.0
ARG ANDROID_NDK_VERSION=26.3.11579264
ARG RUST_VERSION=1.93.0

ENV DEBIAN_FRONTEND=noninteractive
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV ANDROID_NDK_HOME=/opt/android-sdk/ndk/${ANDROID_NDK_VERSION}
ENV ANDROID_NDK=/opt/android-sdk/ndk/${ANDROID_NDK_VERSION}
ENV CARGO_HOME=/opt/cargo
ENV RUSTUP_HOME=/opt/rustup
ENV PATH=/opt/cargo/bin:/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:/usr/local/bin:${PATH}

RUN sed -i '/^Types:/i Architectures: arm64' /etc/apt/sources.list.d/ubuntu.sources \
    && cat > /etc/apt/sources.list.d/ubuntu-amd64.sources <<'EOF'
Types: deb
URIs: http://archive.ubuntu.com/ubuntu/
Suites: noble noble-updates noble-backports
Components: main restricted universe multiverse
Architectures: amd64

Types: deb
URIs: http://security.ubuntu.com/ubuntu/
Suites: noble-security
Components: main restricted universe multiverse
Architectures: amd64
EOF
RUN dpkg --add-architecture amd64 \
    && apt-get update \
    && apt-get install -y --no-install-recommends \
        bash \
        build-essential \
        ca-certificates \
        cmake \
        curl \
        file \
        git \
        jq \
        openjdk-21-jdk-headless \
        perl \
        pkg-config \
        python3 \
        qemu-user-static \
        unzip \
        zip \
        libc6:amd64 \
        libgcc-s1:amd64 \
        libstdc++6:amd64 \
        zlib1g:amd64 \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p ${ANDROID_HOME}/cmdline-tools \
    && curl -fsSL ${ANDROID_CMDLINE_TOOLS_ZIP} -o /tmp/android-commandline-tools.zip \
    && unzip -q /tmp/android-commandline-tools.zip -d /tmp/android-commandline-tools \
    && mv /tmp/android-commandline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest \
    && rm -rf /tmp/android-commandline-tools /tmp/android-commandline-tools.zip

RUN yes | sdkmanager --licenses >/dev/null \
    && sdkmanager \
        "platform-tools" \
        "platforms;${ANDROID_PLATFORM}" \
        "build-tools;${ANDROID_BUILD_TOOLS}" \
        "ndk;${ANDROID_NDK_VERSION}" \
        "cmake;3.22.1"

RUN curl -fsSL https://sh.rustup.rs -o /tmp/rustup-init.sh \
    && sh /tmp/rustup-init.sh -y --no-modify-path --profile minimal --default-toolchain ${RUST_VERSION} \
    && rm /tmp/rustup-init.sh \
    && rustup target add aarch64-linux-android \
    && cargo install cargo-ndk --locked

COPY bin/wrap-x86-android-tools /usr/local/bin/wrap-x86-android-tools
RUN chmod +x /usr/local/bin/wrap-x86-android-tools \
    && wrap-x86-android-tools

WORKDIR /workspace
