# DSH session commands

The composer consumes DSH command declarations and argument completions from Matrix to-device events. It does not call a bot HTTP endpoint, run a model, or use another bot integration. Existing independent command sources remain available.

Type `/` in a mapped room to discover `/models` and `/model <provider/model>`. Selecting a row inserts the literal command. Model completions come from the current DSH catalog and refresh while the composer is open. Sending uses the normal encrypted room-message path; DSH handles recognized commands outside conversation/model work. No login command is advertised.

## Trust and lifecycle

Discovery first reads cached joined-room state for a signed, short-lived
`icu.victor.dsh.commands.service` advertisement. Exactly one currently joined,
verified service device must match the room and sender. Ambiguous, expired,
revoked or absent services receive no discovery requests. The bot must have
permission to publish its own room-state advertisement.

Version-2 `icu.victor.dsh.commands.request` targets only that verified device and
contains a nonce, room/device binding, limit and optional **declared command name**.
Unsent arguments are never transmitted: all argument-prefix matching happens
locally. Unknown command drafts send no draft text. The independent command source
continues to receive only the command-name prefix, never arguments. A shared
composer boundary separates the name at any Unicode whitespace (including tabs),
rejects controls/malformed or overlong input, and passes only the normalized name
to either discovery consumer. The argument-completion flag carries no argument
text. Filtering stays in the composer and never changes the literal sent command.

Responses use `icu.victor.dsh.commands.response` with a signed UTF-8 payload bound
to sender/device, nonce, room and command name. The SDK verifies the stored trusted
device key; response-supplied keys are never trusted. Membership and the current
service advertisement are checked again on receipt. Requests have a five-second
bound and refresh every five seconds while completion is open. The visible and
parser catalog is replaced on every result, cleared on failure/cancellation, and
scoped to the current room/account. Only an explicitly selected literal can be
sent after expiry; it never becomes an autocomplete authorization cache.

Commands are generic descriptors (`name`, `description`, optional `argument_hint`); completions can include an exact argument after the declared name. Model names are not compiled into the app. Backend authorization remains authoritative when a command is sent.

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
