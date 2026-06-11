# org.dwdc.keymaster.android

Android KeyMaster app for DWDC.

This project was extracted from the existing `ae.redtoken.iz.keymaster.android` app and renamed to:

- Android namespace/application id: `org.dwdc.keymaster`
- Kotlin package root: `org.dwdc.keymaster`
- KeyVault dependency: `org.dwdc:org.dwdc.keyvault.nostr:0.1.0-SNAPSHOT`

Build locally:

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew assembleDebug
```

The default Java 25 runtime in this environment is currently too new for the Kotlin/Gradle stack used by the app.
