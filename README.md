# Essence

A minimal Android launcher with an e-ink look and a built-in Claude assistant. You can speak a command, and Essence carries it out on the phone. Built with Kotlin and Jetpack Compose.

See [docs/ROADMAP.md](docs/ROADMAP.md) for the plan.

After installing, set Essence as the default home app under *Settings → Apps → Default apps → Home app*.

## Requirements

- Android Studio (latest stable)
- JDK 17 or newer
- Android SDK platform 37

## Set up the assistant

1. In **Settings → Claude**, add an API key from console.anthropic.com.
2. In **Settings → Assistant**, turn on screen access, which opens Android's Accessibility settings, and allow the
   microphone.
3. Tap **✦ SPEAK** on the home page, or the ✦ bubble over any app, and say what you want done.

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
├── agent/               # Claude tool loop (Claude API Java SDK) and the tool type
├── assist/              # accessibility service: screen reader, device tools, overlay, voice
├── data/                # installed apps, favorites, API key (encrypted), memories, agent cards
└── ui/
    ├── home/            # home page, glance header, A–Z app list
    ├── agent/           # agent cards
    ├── settings/        # API key, model, assistant setup, memories
    └── theme/           # e-ink theme: paper and ink colors, serif and monospace type
```

Dependency versions live in `gradle/libs.versions.toml`.
