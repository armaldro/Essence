# Essence

An Android app built with Kotlin and Jetpack Compose (Material 3).

## Requirements

- Android Studio (latest stable)
- JDK 17 or newer
- Android SDK platform 37

## Build

```sh
./gradlew assembleDebug          # build a debug APK
./gradlew installDebug           # install on a connected device or emulator
./gradlew testDebugUnitTest      # run unit tests
./gradlew connectedAndroidTest   # run instrumented tests on a device
```

## Project layout

```
app/src/main/java/com/armaldro/essence/
├── MainActivity.kt      # entry point
└── ui/theme/            # Material 3 theme, colors, typography
```

Dependency versions live in `gradle/libs.versions.toml`.
