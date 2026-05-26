# CLAUDE.md

Project conventions for **DayCounter** — a tiny Android app that counts the
days since a user-chosen start date. Claude Code reads this file
automatically at session start. Keep it short and actionable.

> **Single-file by design.** Tooling auto-loads `CLAUDE.md`. A separate
> `RULES.md` would either need a "read this first" pointer (ignorable) or
> duplicate the rules (drift class). Stays single-file until this file
> outgrows practical readability.

---

## Stack

- Kotlin, **Jetpack Compose**, Material 3 (dynamic color)
- `minSdk = compileSdk = targetSdk = 36` — **Android 16 only, no
  backwards-compat shims**
- **JDK 21** to run Gradle (bytecode targets Java 17 via
  `sourceCompatibility` / `jvmTarget`)
- Single Gradle module (`:app`), single Activity (`MainActivity`)
- State: one `AndroidViewModel` exposing `StateFlow<DayCounterUiState>`
- Persistence: `SharedPreferences` (one string key: `start_date` as ISO
  `LocalDate`). Storage need is one tiny value with no async pressure — a
  `DataStore` migration would be more code than the whole feature.
- Tests: **none yet.** Conventions for when they get added live in
  `app/src/test/java/com/example/daycounter/TESTING_CONVENTIONS.kt`.

## Build & test

```bash
./gradlew assembleDebug          # debug APK
./gradlew test                   # unit tests (JVM, no emulator)
./gradlew assembleRelease        # signed release (ProGuard enabled)
```

Gradle is bundled via wrapper. The build needs **JDK 21** — set
`JAVA_HOME` accordingly, or rely on Android Studio's bundled JDK.

---

## Architecture

The whole app lives in **one file**: `app/src/main/java/com/example/daycounter/MainActivity.kt`.
It contains, in order:

- `DayCounterUiState` — immutable snapshot consumed by the UI.
- `DayCounterViewModel` — owns `SharedPreferences`, computes the state.
- `MainActivity` — Compose entry point.
- `DayCounterTheme` — Material 3 + dynamic color wrapper.
- `DayCounterScreen` + `StartDatePickerDialog` — Composables.

Don't split this file preemptively. If a new feature genuinely needs its
own logic surface (e.g. adding multiple independent counters back, a
notification, a widget), promote the relevant chunk into its own file at
that point — not before.

---

## Hard architectural rules

1. **Logic in the ViewModel, not in the Composable.** Date math, date
   persistence, and "what to show" decisions belong in
   `DayCounterViewModel`. Composables read `StateFlow` and render. This
   keeps everything testable on the JVM without Robolectric.

2. **`refresh()` is explicit.** The ViewModel does not auto-recompute on
   midnight rollover or `onResume`. The "Aktualisieren" button is the only
   refresh trigger. Don't add a `LaunchedEffect` polling loop without
   asking — the visible staleness is intentional and obvious to the user.

3. **No `try/catch` around can't-throw operations.** Catches are for real
   failure modes (I/O, system APIs, malformed user input). They are NOT a
   "just in case" wrapper around pure operations like `LocalDate.parse` on
   a value we wrote ourselves, `ChronoUnit.DAYS.between`, or
   `String.format`. Programmer errors should crash loudly during
   development, not be swallowed silently.

   The exception is `LocalDate.parse` on the **stored** prefs value — if
   a future schema change ever lands, that read could fail. If you add a
   schema change, handle it explicitly with a fallback to "default =
   today" and a `Log.w` line, not a blanket catch.

4. **Testable logic lives outside Activity / Composable bodies.**
   Anything worth pinning (state transitions, date arithmetic, persistence
   decisions) belongs in `DayCounterViewModel`. The Composable becomes thin
   glue: collect `StateFlow`, render, forward events. JVM tests are the
   default target — fast, deterministic, no emulator.

5. **New comments and KDoc are English; existing German UI strings stay.**
   Code comments and KDoc you add or rewrite are written in English.
   UI-facing strings (`"Tagezähler"`, `"Seit dem"`, `"Aktualisieren"`,
   `"Abbrechen"`) are German and stay German — this is a personal-use app
   for a German speaker. If the app ever gets localised, strings move to
   `res/values/strings.xml` + `res/values-de/strings.xml`; until then,
   inline `"…"` is fine.

6. **`SharedPreferences` stays.** No premature `DataStore` migration. The
   whole stored state is one ISO date string. Migrating buys nothing
   except more code, a coroutine boundary, and a Hilt dependency we don't
   have.

7. **Material 3 `DatePicker` returns midnight UTC.** Convert via
   `ZoneOffset.UTC`, not `ZoneId.systemDefault()`. The latter shifts the
   date by one in time zones west of UTC. If you see this regression, the
   conversion site is `StartDatePickerDialog`.

---

## Git workflow: branch before non-trivial work

Larger changes — bigger bugfixes, refactorings, new features, anything
that touches multiple files or could plausibly be reverted as a unit —
must happen on a dedicated branch, never directly on `main`. Trivial
edits (typo fix, single-line tweak, doc nit) can stay on the current
branch.

When in doubt, **stop and ask the user before starting**. It is always
cheaper to confirm than to realize mid-implementation that the work is
on the wrong branch.

Suggested branch prefixes:

- `fix/<slug>` — bugfix
- `refactor/<slug>` — refactoring
- `feature/<slug>` — new feature
- `chore/<slug>` — tooling, build, dependencies

After a fast-forward merge into `main`: switch back to `main` and ask
whether the merged branch should be deleted both locally and on the
remote. Don't delete branches silently — even after a merge, an open PR
or historical reference may still hang on the branch.

---

## What this file is NOT

- Not a description of the project (one-paragraph blurb belongs in a
  `README.md` if/when one gets written).
- Not the full testing reference (see `TESTING_CONVENTIONS.kt`).
- Not a holding pen for transient refactor notes — those belong in
  commit messages.

Update this file when an architectural rule changes or a hard-won lesson
deserves to be future-proofed. Do not bloat it with details that are
obvious from reading the (one) file.
