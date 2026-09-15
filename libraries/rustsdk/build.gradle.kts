// Built from the immutable published sources in tools/dsh-build/sources.json.
// An old manually supplied libraries/rustsdk AAR is intentionally not consumed.
configurations.maybeCreate("default")
artifacts.add("default", rootProject.file(".dsh-build/matrix-rust-sdk.aar"))
