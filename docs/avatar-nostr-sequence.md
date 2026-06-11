# Avatar <-> KeyMaster Nostr Scenario (Mermaid)

```mermaid
sequenceDiagram
    autonumber
    participant A as Avatar (root keypair)
    participant SA as Avatar SSH Channel (service_avatar_pubkey)
    participant SKM as KM SSH Channel (service_pubkey)
    participant KM as KeyMaster (root keypair)

    Note over A,KM: Bootstrap + Attach (all content NIP-44 encrypted)
    A->>KM: QR shared out-of-band: relay + avatar_pubkey
    KM->>A: kind 27235 attach {method:"attach", services[], identity, alt_id[]}
    A-->>KM: attach response {result:"ok"} + attached_session_event_id

    Note over A,KM: Root session control-plane
    A->>SA: create {service_type:"ssh"}
    SA-->>A: get_pubkey -> {service_avatar_pubkey}
    A->>KM: service.spawn {service_type:"ssh", service_avatar_pubkey, allowed_identity[], methods[]}
    KM->>SKM: create {service_type:"ssh", service_avatar_pubkey}
    SKM-->>KM: get_pubkey -> {service_pubkey}
    KM-->>A: service.spawn result {service_pubkey}
    A->>SA: connect {service_pubkey}
    SA-->>A: setup_ok
    KM->>SKM: connect {service_avatar_pubkey}
    SKM-->>KM: setup_ok

    Note over SA,SKM: SSH service channel (separate keypair per side)
    SA->>SKM: request_identities {method:"request_identities", params:[]}
    SKM-->>SA: request_identities result {identities:[...]}
    SA->>SKM: sign_request {method:"sign_request", params:[key_blob,data,flags]}
    SKM-->>SA: sign_request result {signature}

    Note over A,KM: Detach / teardown
    KM->>A: detach {method:"detach"}
    A-->>KM: detach response {result:"ok"}
    A->>SA: destroy
    SA-->>A: destroy_ok
    KM->>SKM: destroy
    SKM-->>KM: destroy_ok
    Note over A,SKM: Revoke all service channels linked to attached_session_event_id
```

## Tagging Rules (applies to the sequence above)

- All requests include:
  - `p` tag addressed to the receiver pubkey
  - `e` tag with session descriptor:
    - root requests: `["e","<attached_session_event_id>","","session"]`
    - service requests: `["e","<service_session_event_id>","","session"]`
- All responses include:
  - `p` tag addressed to requester pubkey
  - `e` reply tag: `["e","<request_event_id>","","reply"]`
- Response does not require a `session` tag.
