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

## SDK dependency and build

This fork already requires a locally built Rust SDK AAR. This feature additionally requires `Client.verify_device_signature` and the bounded joined-room `get_room_state_events`, exposed through Kotlin. The matching source checkpoints are:

- matrix-rust-sdk: `7a16a36` (verified-device signatures, bounded room-state discovery, and subscribed-room service state)
- matrix-rust-components-kotlin: `47aac15` (matching generated UniFFI bindings)

Build the matching native library and generated Kotlin bindings together, then place the resulting AAR at `libraries/rustsdk/matrix-rust-sdk.aar` (ignored). Never commit binaries or signing/authentication material. Use the existing Docker-only Android development stack; do not install host Android/JDK tooling. Run `:app:assembleFdroidDebug` and the focused DSH/MyClaw discovery tests. Package identity and signing configuration are unchanged.

The SDK changes are currently committed in isolated local branches; no writable user-owned SDK remote was available. Obtain those committed sources before rebuilding this branch. An older AAR will fail compilation rather than silently disable authentication.
