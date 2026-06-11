# org.dwdc.keymaster.android

Android KeyMaster app for DWDC.

This project was extracted from the existing `ae.redtoken.iz.keymaster.android` app and renamed to:

- Android namespace/application id: `org.dwdc.keymaster`
- Kotlin package root: `org.dwdc.keymaster`
- KeyVault dependency: `org.dwdc:org.dwdc.keyvault.nostr:0.1.0-SNAPSHOT`

## Requirements

- JDK 21
- Android SDK 35

The repository includes a `.java-version` file for version managers such as
`jenv`, `asdf`, and `mise`. Set `JAVA_HOME` to a JDK 21 installation when your
environment does not use one of these tools.

## Build

```sh
./gradlew test assembleDebug
```

Gradle and Kotlin use a JDK 21 toolchain. Android Java and Kotlin bytecode remain
targeted at Java 17 for device compatibility.
