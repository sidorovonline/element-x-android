# DSH session commands

The composer consumes DSH command declarations and argument completions from Matrix to-device events. It does not call a bot HTTP endpoint, run a model, or use another bot integration. Existing independent command sources remain available.

Type `/` in a mapped room to discover `/models` and `/model <provider/model>`. Selecting a row inserts the literal command. Model completions come from the current DSH catalog and refresh while the composer is open. Sending uses the normal encrypted room-message path; DSH handles recognized commands outside conversation/model work. No login command is advertised.

## Trust and lifecycle

Discovery sends `icu.victor.dsh.commands.request` with a fresh transaction ID, room ID, query, requesting device ID and `signed_response: true`. Candidate sources must be joined members on the account's homeserver; discovery is disabled above ten candidates. Responses use `icu.victor.dsh.commands.response` and contain a signed UTF-8 payload plus device ID and Ed25519 signature. The payload binds the response event type, sender, device, transaction, room and exact query.

The SDK verifies against its stored, verified, non-deleted Matrix device key. A key supplied by the response is never trusted. Unsigned responses and unverified devices are rejected. The bot device must already be verified by the account through Matrix trust. Membership is checked again on receipt. No catalog is retained across calls, rooms or accounts. Requests, payloads, arrays and field lengths are bounded; timeout, cancellation and query changes release the collector. The whole operation is limited to five seconds. A fresh query is made every five seconds while command completion is active.

Commands are generic descriptors (`name`, `description`, optional `argument_hint`); completions can include an exact argument after the declared name. Model names are not compiled into the app. Backend authorization remains authoritative when a command is sent.

## SDK dependency and build

This fork already requires a locally built Rust SDK AAR. This feature additionally requires `Client.verify_device_signature`, exposed to Kotlin as `verifyDeviceSignature`. The matching source checkpoints are:

- matrix-rust-sdk: `1f97535` (verified-device signature verification)
- matrix-rust-components-kotlin: `b41d3dc` (generated UniFFI bindings)

Build the matching native library and generated Kotlin bindings together, then place the resulting AAR at `libraries/rustsdk/matrix-rust-sdk.aar` (ignored). Never commit binaries or signing/authentication material. Use the existing Docker-only Android development stack; do not install host Android/JDK tooling. Run `:app:assembleFdroidDebug` and the focused DSH/MyClaw discovery tests. Package identity and signing configuration are unchanged.

The SDK changes are currently committed in isolated local branches; no writable user-owned SDK remote was available. Obtain those committed sources before rebuilding this branch. An older AAR will fail compilation rather than silently disable authentication.
