# DSH session commands

The composer consumes DSH command declarations and argument completions from Matrix to-device events. It does not call a bot HTTP endpoint, run a model, or use another bot integration. Existing independent command sources remain available.

Type `/` in an authorized encrypted mapped room for the full DSH Matrix catalog:
`models`, `model`, `retry`, `help`, `status`, `stop`, `room`, `dm`, `visibility`,
`rename` and `session`. Filtering happens as you type. A space after `model`,
`visibility` or `dm` enables current choices; free-text titles have an argument
hint. Selecting a row inserts its literal command and a trailing space. Sending
remains a separate user action through the normal encrypted message path. DSH
handles these controls without provider inference. Rename remains subject to
ordinary room permissions; no extra powers are required for discovery or chat.

## Trust and lifecycle

Version-3 `icu.victor.dsh.commands.request` is sent to up to 32 other joined
members on the account's own server. It contains only a random nonce, shared room
ID, own device ID and bounded result limit. No composer text, command name,
prefix or argument is sent. No room-state advertisement or special room setup is
needed. The backend responds only for an allowed joined requester with an already
trusted device in an existing unblocked encrypted DSH mapping.

The client gathers responses for two seconds within a five-second total bound.
Exactly one signed service-device response is required. Responses bind sender,
recipient, both devices, room and nonce with a short expiration. Native SDK
trusted device keys verify signatures; response-supplied keys are never trusted.
Membership and trust are rechecked at use. Results are replaced on refresh and
cleared on cancellation/failure, with no persistent discovery catalog. Multiple
responding services fail closed rather than arbitrarily choosing one.

Declarations and dynamic choices come from DSH, not a hardcoded visual list.
All filtering is local. The separate working MyClaw source still receives only
its command-name prefix, never unsent arguments, and uses its own protocol.
Selecting a literal command does not grant execution authority; DSH checks that
again on actual submission. Titles are free text, not suggested or transmitted
while composing. Login is not advertised.

Both this app update and the version-3 DSH plugin must be delivered. Updating the
backend cannot change an already installed client. Generic Matrix clients may
send typed commands but do not automatically gain this composer menu. Visual
acceptance on the user's device is distinct from protocol/build validation.

## Reproducible SDK and app build

The complete SDK source is published in user-owned repositories. Exact commits,
not moving branches, are locked in `tools/dsh-build/sources.json`. Authentication
is needed to clone the private SDK repositories; Git uses the operator's normal
authorized transport. No credentials are copied into the app, SDK, or container.
Official upstream history, licenses and attribution are retained.

From a **fresh authorized clone** of `sidorovonline/element-x-android`, branch
`dsh-integrated-source` (or a reported immutable app commit):

```sh
python3 tools/dsh-build/fetch.py
docker build --platform linux/amd64 -t element-dsh-source-builder tools/dsh-build
docker run --name element-dsh-source-build --platform linux/amd64 \
  -e GIT_CONFIG_COUNT=1 -e GIT_CONFIG_KEY_0=safe.directory -e GIT_CONFIG_VALUE_0='*' \
  -v "$PWD:/app" element-dsh-source-builder tools/dsh-build/build.sh
```

Apply your environment's owned-resource/activity guard before container lifecycle
operations. Builds need access to official dependency registries. No app/account
credentials are required. Existing equivalent Docker-only ARM64 tooling may run
the same script with Android SDK, Rust 1.93.0 and cargo-ndk 4.1.2; its SDK executables
must support the host architecture. Nothing is installed on the host.

`fetch.py` refuses to overwrite existing SDK directories. `build.sh` verifies
both immutable source revisions and source cleanliness, generates matching UniFFI
bindings and the Android native library together, then builds the AAR and app.
It refuses to reuse a pre-existing AAR. All generated content stays under ignored
`.dsh-build/` and normal Gradle build directories. The Gradle dependency consumes
only this generated AAR; the legacy `libraries/rustsdk/matrix-rust-sdk.aar` is no
longer used. Caches may accelerate registry downloads but cannot substitute a
local SDK checkout or prebuilt library. To repeat cleanly, use another fresh clone.

The installable candidate is
`app/build/outputs/apk/fdroid/debug/app-fdroid-arm64-v8a-debug.apk`. This recipe
builds **ARM64 only**; do not distribute its other split/universal outputs as
multi-architecture builds. Package/signing configuration is unchanged. It uses
the repository's existing public debug key, not production signing credentials.
Production signing and installation are separate delivery decisions.

Run focused discovery/query tests plus the integrated Markdown, activity, typing
and home connection tests in the same container. The source branch preserves the
two unpublished Markdown commits and the meaningful Home/activity source changes
from the protected app checkout; generated QA artifacts and private notes are not
build dependencies. Existing independent MyClaw autocomplete remains separate.

For an app-only change with unchanged locked SDK sources, an existing generated
AAR from this exact source-build lineage can be retained if its digest and source
provenance are recorded. Build the app with `:app:assembleFdroidDebug` in the same
containerized toolchain; no SDK API or native-library replacement is needed for
version-3 discovery. Verify package, version, ABI and signing certificate against
the prior delivered APK before distributing an update. Do not uninstall or erase
app data to bypass a signing mismatch.
