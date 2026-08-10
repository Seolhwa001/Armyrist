# Armyrist — 실셈 First Usable Release

Stage 1 / Sprint 1 implementation based on Architecture Handover No.001.

## Included
- Counting sheet CRUD
- Item CRUD
- Quantity + / - / direct input
- Custom units
- Group CRUD and assignment
- Same-unit aggregation
- A+B / A-B group calculations
- Item note / sheet memo
- Immediate local persistence
- App relaunch restore
- Result preview
- Clipboard copy
- Android generic share
- Offline-only core flow

## Technical choices
- Kotlin
- Jetpack Compose
- Local JSON snapshot in private SharedPreferences
- No account, server, cloud sync, analytics, or network permission
- `android:allowBackup="false"` and data extraction exclusions

## Build
Open the project in Android Studio. JDK 17 and Android SDK 36 are expected.

If the Gradle wrapper JAR is not present, run a local Gradle 8.13 `wrapper` task once or let Android Studio repair/regenerate the wrapper. The ZIP includes the wrapper scripts and properties.

Command line after wrapper is complete:

```text
./gradlew test
./gradlew assembleDebug
```

Expected APK:
`app/build/outputs/apk/debug/app-debug.apk`

## Important validation
The app keeps temporary invalid quantity text outside the committed domain state. A quantity is persisted only after validation succeeds.
Group deletion unassigns its items and removes calculations referencing that group.
Different unit strings are never merged.
