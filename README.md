# DayCounter

[![API](https://img.shields.io/badge/API-36-brightgreen.svg?style=flat-square)](https://source.android.com/docs/setup/about/build-numbers)
[![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7f52ff.svg?style=flat-square)](https://www.jetbrains.com/kotlin/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=flat-square)](https://opensource.org/licenses/MIT)

A tiny Android app that counts the days since a user-chosen start date.
One screen, one number.

## What it is

DayCounter shows the day count since whatever date you pick. The big
number is the total; below it, the same span is broken out as
`years / months / weeks / days` for context. Tap the date to change it;
default is today.

It exists because the equivalent "first launch" date for some life
moment (a quit date, an anniversary, a streak) deserves something more
honest than a calendar reminder — and because I wanted a sandbox to
keep current with Jetpack Compose + Material 3 dynamic color in a
project small enough to ship in one session.

## Requirements

- **Android 16** (API 36). `minSdk = compileSdk = targetSdk = 36`.
  No backwards-compat shims. If your device is older, this app won't
  install.
- **JDK 21** to build (set `JAVA_HOME` accordingly, or use the JDK
  bundled with Android Studio).

## Features

- One headline number — the total days since the start date.
- Same span as `Y / M / W / T` for human-readable context.
- Tap the start date → Material 3 `DatePicker`. Choice is persisted in
  `SharedPreferences`.
- Manual refresh button — no auto-poll at midnight. If you open the app
  the next day, tap *Aktualisieren* once.
- Dynamic color (Material You) throughout; light/dark theming follows
  the system.
- German UI; the codebase is small enough that there's no `strings.xml`
  yet.

## Architecture & Tech Stack

Single Gradle module, single Activity, single file. The entire app
lives in `app/src/main/java/com/example/daycounter/MainActivity.kt`:

- `DayCounterUiState` — immutable snapshot for the UI.
- `DayCounterViewModel` (an `AndroidViewModel`) — date math + prefs I/O.
- `MainActivity` — Compose entry point.
- `DayCounterTheme` — Material 3 wrapper.
- `DayCounterScreen` + `StartDatePickerDialog` — the UI.

### Stack

- **100 % Kotlin**, Jetpack Compose, Material 3.
- **MVVM-lite.** One ViewModel owns state; the Composable reads
  `StateFlow` and renders. No use cases, no repositories, no DI —
  scope doesn't justify the ceremony.
- **`SharedPreferences`** as the only persistent store (one ISO date
  string). DataStore would be more code than the whole feature.
- **Android 16 only, no backwards-compat shims.** Dynamic color is
  unconditional.

### Testing

JVM unit tests via Robolectric 4.16.1 (needed for an in-memory
`SharedPreferences`). `DayCounterViewModelTest` covers the persistence
round-trip, the `Clock`-injected "today" boundary cases, and the
`years / months / weeks / days` breakdown across leap-year and
month-boundary spans.

Conventions, dependency pins, and the rationale for the Robolectric
choice live in
[`TESTING_CONVENTIONS.kt`](app/src/test/java/com/example/daycounter/TESTING_CONVENTIONS.kt).

## Building

```bash
git clone git@github.com:reygnn/DayCounter.git
cd DayCounter
./gradlew assembleDebug         # debug APK
./gradlew test                  # JVM unit tests (none yet — see above)
./gradlew assembleRelease       # release APK (ProGuard enabled)
```

Gradle 8.13 is bundled via the wrapper.

## Project documentation

- [`CLAUDE.md`](CLAUDE.md) — project conventions and the 7 hard
  architectural rules. Read this first before any code change.
- [`WHY_CLAUDE.md`](WHY_CLAUDE.md) — collaboration rationale: why
  Claude is in the loop and why branches are still sacred.
- [`TESTING_CONVENTIONS.kt`](app/src/test/java/com/example/daycounter/TESTING_CONVENTIONS.kt)
  — testing conventions for when the first test lands.

## Contributing

Personal project. Issues are fine if something's broken; pull
requests are welcome but not actively solicited. Architectural
changes that contradict `CLAUDE.md` need an issue first to discuss
the rule trade-off.

## License

MIT — see [`LICENSE`](LICENSE).
