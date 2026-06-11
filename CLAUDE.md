# KeyMaster Android — Agent Rules

## Project

- Package: `org.dwdc.keymaster`
- Kotlin + Jetpack Compose, Gradle 8.11.1, AGP 8.7.3
- JDK 21 toolchain with Java 17 Android bytecode target
- NIP-55 Nostr signer for external signing (intents + content provider)

## Build & Test

```bash
./gradlew assembleDebug          # build
./gradlew test                   # unit tests (JUnit)
```

## Check-in Rules

### 1. Tests must pass before commit

Run `./gradlew test` before every commit. Do NOT commit if tests fail.

### 2. Signed commits

All commits MUST be GPG-signed. Use `git commit -S` or ensure `commit.gpgsign = true` is set.
Do NOT use `--no-gpg-sign` or skip signing.

### 3. Reference issues

- When a commit addresses a GitHub issue, include `Refs #<number>` in the commit message body.
- When a commit fully resolves an issue, use `Closes #<number>` or `Fixes #<number>` in the commit message body.

### 4. Tag releases

See [RELEASEPOLICY.md](RELEASEPOLICY.md) for versioning scheme and tagging rules.
Do NOT tag automatically — ask the user if a tag/release is appropriate.

### Commit message format

```
<short summary in imperative mood>

<optional body explaining why, not what>

Closes #<issue>
Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>
```

## Key Technical Notes

- JSON serialization for NIP-01 event IDs MUST use `GsonBuilder().disableHtmlEscaping().create()` — never default `Gson()`. See issue #16.
- BIP-340 Schnorr signing is implemented manually with BouncyCastle (bitcoinj's `ECKey.signSchnorr()` is not available).
- Curve25519 encryption seeds must be clamped before creating `X25519PrivateKeyParameters`.
