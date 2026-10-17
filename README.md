# Essence

A minimal Android launcher with an e-ink look and built-in AI agents. Built with Kotlin and Jetpack Compose.

See [docs/ROADMAP.md](docs/ROADMAP.md) for the plan.

After installing, set Essence as the default home app under *Settings → Apps → Default apps → Home app*.

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
├── MainActivity.kt      # HOME activity
├── EssenceApp.kt        # holds the repositories
├── data/                # installed apps (LauncherApps) and favorites (DataStore)
└── ui/
    ├── home/            # home page, glance header, A–Z app list
    └── theme/           # e-ink theme: paper and ink colors, serif and monospace type
```

Dependency versions live in `gradle/libs.versions.toml`.
