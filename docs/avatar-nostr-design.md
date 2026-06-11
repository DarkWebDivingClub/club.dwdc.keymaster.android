# Avatar over Nostr Design (Avatar Hosts Relay, KeyMaster Logs In)

## Decision

Build **Avatar in Rust** and make it the bootstrap host.

Rationale:

- best fit for long-running local daemon + Unix socket handling (`ssh-agent` compatibility)
- lightweight static binary for desktop/server deployment
- aligns well with running a local lightweight Rust Nostr relay
- clean language boundary: KeyMaster services exposed over protocol, not in-process reflection

Keep existing Java/Kotlin KeyVault logic as-is initially; integrate via protocol boundary.
KeyMaster remains Android/JVM-first and logs into Avatar.
KeyMaster is authoritative for which services and identities are available.

## Scope

Design a transport/service model where:

- Avatar starts and hosts a Nostr relay on a reachable network endpoint
- Avatar presents a QR code with relay endpoint + `avatar_pubkey`
- KeyMaster scans QR and performs authenticated attach
- a root session is established for control-plane methods
- service channels are spawned for data-plane methods
- subavatars can be spawned with scoped permissions

## Core Model

### 1) Root Session Anchor

- `attached_session_event_id` (Nostr event id of KM `attach`)
- `keymaster_pubkey`
- `avatar_pubkey`
- `auth_state`
- `subavatar_of_attach` (optional)
- all control-plane messages in that session MUST reference `attached_session_event_id`

### 2) Identity Set (KM-owned)

- `identity` (primary/default identity set in attach)
- `alt_id[]` (additional identities KM allows Avatar to choose from)
- optional per-identity public refs/fingerprints

### 3) Service Channel Session

- each enabled service runs on its own channel keypair/session
- `service_session_event_id` (anchor for that service channel)
- `service_pubkey` (channel sender identity)
- fixed `service_type` and fixed identity scope are bound at spawn time
- requests/responses inside a service channel do NOT carry per-message `service` or `identity`

### 4) Service Versions (KM-owned)

- `services[]` contains `service_type` and optional `version`
- methods are defined by service spec for that version (not sent in attach payload)
- for service version `1.0`, omit `version`

## Message Envelope (all requests/responses)

Request shape:

```json
{
  "method": "service.spawn",
  "params": []
}
```

Response shape (`result`/`error` semantics, all channels):

```json
{
  "result": {}
}
```

or

```json
{
  "error": "signing rejected"
}
```

No `options` wrapper is used; method-specific fields live directly in `content`.
Requests use NIP-46-style positional `params` array.
`result` and `error` are mutually exclusive; do not send both in a success response.
No request `id` field is used in `content`; correlation is done with strict `e` tags.

### Encryption

All request/reply payloads MUST use **NIP-44 encryption (version 2)**.

- `content` is the NIP-44 ciphertext string, not plaintext JSON
- plaintext JSON request/response objects shown in this doc are the pre-encryption payload shape
- `p` tag recipient pubkey is required to derive shared-secret encryption context
- this applies to all requests and responses, including `attach`
- no protocol message in a session is sent with plaintext `content`

### Tag Rules

- all session requests MUST include:
  - root control-plane request -> `["e","<attached_session_event_id>","","session"]`
  - service-channel request -> `["e","<service_session_event_id>","","session"]`
- all responses MUST include:
  - `["e","<request_event_id>","","reply"]`

Request/response direction is symmetric:

- either side MAY send requests and responses

Directed routing rule:

- KM -> Avatar: include `["p","<avatar_pubkey>"]`
- Avatar -> KM: include `["p","<keymaster_pubkey>"]`
- service avatar channel -> KM service channel: include `["p","<service_pubkey>"]`
- KM service channel -> service avatar channel: include `["p","<service_avatar_pubkey>"]`

## Nostr Kinds

Use one protocol kind for Avatar/KM traffic:

- `27235`: attach + control-plane + service-channel requests/responses

`kind` is configurable, but defaults to this constant.

## Bootstrap + Attach Handshake

### A) Avatar bootstrap (QR)

Avatar starts:

- relay (`ws://<host-or-ip>:<port>`)
- Avatar keypair (or loads existing)
- session policy defaults

QR payload example:

```json
{
  "v": 1,
  "relay": "ws://192.168.1.50:4848",
  "avatar_pubkey": "<hex>"
}
```

### B) KeyMaster attach

KeyMaster scans QR and sends `attach`:

- `keymaster_pubkey`
- services KM exposes with versions (`services[]`)
- `identity` (primary identity for the session)
- `alt_id[]` (optional additional identities Avatar may use)
- signature over canonical attach payload

Avatar verifies signature and replies with attach response:

- `result` on success
- `error` on failure

Detach closes the root session:

- KM sends `method: "detach"`
- Avatar replies with `result` or `error`
- after detach, all service channels linked to that root session MUST be revoked

## Subavatar

`subavatar.create` and `service.spawn` are control-plane methods.

`service.spawn` request `params` includes:

- `service_type`
- `allowed_identity[]` (subset of attach `identity + alt_id[]`)
- optional method allowlist
- optional ttl/expiry policy

Subavatar receives its own `attached_session_event_id` anchor and scoped policy.
KM remains authority for what identities/services exist; Avatar can only scope down.

## Message Examples

### 1) KM -> Avatar attach request (kind 27235)

```json
{
  "kind": 27235,
  "pubkey": "<keymaster_pubkey>",
  "tags": [["p","<avatar_pubkey>"]],
  "content": {
    "method": "attach",
    "params": [
      {
        "services": [
          {"service_type": "nostr"},
          {"service_type": "ssh"}
        ],
        "identity": "default",
        "alt_id": ["ops", "personal"]
      }
    ]
  },
  "sig": "<keymaster_signature>"
}
```

### 2) Avatar -> KM attach response (kind 27235)

```json
{
  "kind": 27235,
  "pubkey": "<avatar_pubkey>",
  "tags": [["p","<keymaster_pubkey>"],["e","<attached_session_event_id>","","reply"]],
  "content": {
    "result": "ok"
  },
  "sig": "<avatar_signature>"
}
```

### 3) Avatar -> KM `service.spawn` request (root control-plane)

```json
{
  "kind": 27235,
  "pubkey": "<avatar_pubkey>",
  "tags": [
    ["p","<keymaster_pubkey>"],
    ["e","<attached_session_event_id>","","session"]
  ],
  "content": {
    "method": "service.spawn",
    "params": [
      {
        "service_avatar_pubkey": "<ssh_service_avatar_pubkey>",
        "service_type": "ssh",
        "allowed_identity": ["default", "ops"],
        "methods": ["request_identities", "sign_request"]
      }
    ]
  },
  "sig": "<avatar_signature>"
}
```

### 4) KM -> Avatar `service.spawn` response (root control-plane)

```json
{
  "kind": 27235,
  "pubkey": "<keymaster_pubkey>",
  "tags": [
    ["p","<avatar_pubkey>"],
    ["e","<service_spawn_request_event_id>","","reply"]
  ],
  "content": {
    "result": {
      "service_pubkey": "<ssh_service_pubkey>"
    }
  },
  "sig": "<keymaster_signature>"
}
```

### 5) Avatar SSH channel -> KM `request_identities` request

```json
{
  "kind": 27235,
  "pubkey": "<ssh_service_avatar_pubkey>",
  "tags": [
    ["p","<ssh_service_pubkey>"],
    ["e","<ssh_service_session_event_id>","","session"]
  ],
  "content": {
    "method": "request_identities",
    "params": []
  },
  "sig": "<ssh_service_avatar_signature>"
}
```

### 6) KM -> Avatar SSH channel `request_identities` response

```json
{
  "kind": 27235,
  "pubkey": "<ssh_service_pubkey>",
  "tags": [
    ["p","<ssh_service_avatar_pubkey>"],
    ["e","<request_identities_event_id>","","reply"]
  ],
  "content": {
    "result": {
      "identities": [
        {
          "key_blob": "<base64>",
          "comment": "default@avatar"
        }
      ]
    }
  },
  "sig": "<ssh_service_signature>"
}
```

### 7) Avatar SSH channel -> KM `sign_request` request

```json
{
  "kind": 27235,
  "pubkey": "<ssh_service_avatar_pubkey>",
  "tags": [
    ["p","<ssh_service_pubkey>"],
    ["e","<ssh_service_session_event_id>","","session"]
  ],
  "content": {
    "method": "sign_request",
    "params": [
      {
        "key_blob": "<base64>",
        "data": "<base64>",
        "flags": 0
      }
    ]
  },
  "sig": "<ssh_service_avatar_signature>"
}
```

### 8) KM -> Avatar SSH channel `sign_request` response

```json
{
  "kind": 27235,
  "pubkey": "<ssh_service_pubkey>",
  "tags": [
    ["p","<ssh_service_avatar_pubkey>"],
    ["e","<sign_request_event_id>","","reply"]
  ],
  "content": {
    "result": {
      "signature": "<base64>"
    }
  },
  "sig": "<ssh_service_signature>"
}
```

## SSH Handling

Use a local `ssh-agent` adapter process/socket.

Flow:

1. OpenSSH client talks Unix socket (`SSH_AUTH_SOCK`)
2. adapter uses root session to request `service.spawn` for `ssh`
3. adapter uses returned SSH service channel for:
   - `request_identities`
   - `sign_request`
4. KM responds on that service channel within scoped policy

Reason: preserves OpenSSH compatibility and makes service scope explicit.

## Routing Rules

For each request:

1. validate session anchor (`attached_session_event_id` for root, `service_session_event_id` for service channel)
2. for service channels, resolve service + identity scope from channel binding (not from payload fields)
3. check service version is KM-advertised and method is valid for that version
4. resolve concrete key within the channel scope:
   - key blob/fingerprint authoritative when provided (SSH)
   - else use bound default identity for that service channel if unambiguous
5. map to HD path
6. execute KeyMaster operation
7. return response linked by Nostr event tags (`e`)
8. on `detach`, close root session and revoke all linked service channels

## Security

- require signed/authenticated session establishment
- bind root session anchor to `keymaster_pubkey` after signature verification
- bind each service channel to fixed `service_type` + identity scope at spawn time
- strict correlation by Nostr event ids/tags and timeouts
- require NIP-44 request/reply encryption for all protocol payloads
- no reflection-based method invocation
- explicit allowlist registry (`service`,`method` -> handler)
- audit log per request: session id, channel pubkey, service, identity scope, method, result

## Outstanding Questions / TODO

- protocol `version` field strategy:
  - keep unset for initial rollout and treat the current payload schema as implicit v1
  - define explicit version negotiation/version field rules before introducing any breaking schema changes
- service channel key lifecycle:
  - key rotation cadence and revocation semantics
  - maximum channel ttl
- whether `service.spawn` is initiated by Avatar, KM, or both (current examples use Avatar->KM)

## Rollout Plan

1. Implement Rust Avatar core (relay host + root session registry + envelope)
2. Add QR bootstrap payload (relay, avatar pubkey)
3. Implement signed `attach` / attach response
4. Implement root control-plane methods (`service.spawn`, `detach`, `subavatar.create`)
5. Implement service channel registry and channel-bound policy enforcement in KM
6. Add SSH adapter (`request_identities`, `sign_request`) over spawned SSH channel
7. Add subavatar scopes
8. Add OpenPGP/X.509/WireGuard services incrementally
