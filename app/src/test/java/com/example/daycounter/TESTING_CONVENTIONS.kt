package com.example.daycounter

/**
 * ============================================================================
 * NO TESTS YET — READ THIS FIRST WHEN YOU WRITE THE FIRST ONE
 * ============================================================================
 *
 * As of project setup, DayCounter ships without tests. The whole app is one
 * ViewModel and one Compose tree; for the current scope, an explicit
 * "Aktualisieren" button and a single date input cover the realistic
 * regression surface.
 *
 * This file exists so that *when* tests get added, the conventions are
 * fixed in advance — not bolted on after the suite has already drifted.
 * The patterns below are the ones that proved load-bearing in the
 * Kolibri Launcher project (this app's sibling); they are pared down to
 * what's actually relevant here.
 *
 * Recommended dependencies for the first test (`app/build.gradle.kts`):
 *
 *     testImplementation("junit:junit:4.13.2")
 *     testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
 *     testImplementation("io.mockk:mockk:1.13.13")
 *     testImplementation("app.cash.turbine:turbine:1.1.0")   // only if collecting flows
 *
 * ============================================================================
 */

/**
 * ============================================================================
 * COROUTINE TEST DISPATCHER — PROJECT CONVENTION
 * ============================================================================
 *
 * TL;DR: Use ONE dispatcher everywhere. Never mix dispatcher instances.
 *
 * THE PATTERN THAT WORKS:
 *
 *   @get:Rule
 *   val mainDispatcherRule = MainDispatcherRule()   // sets Dispatchers.Main
 *
 *   @Test
 *   fun `my test`() = runTest {       // top-level runTest
 *       val vm = DayCounterViewModel(application)
 *       vm.refresh()
 *       advanceUntilIdle()
 *       // assert on vm.uiState.value
 *   }
 *
 * `MainDispatcherRule` is a thin JUnit4 rule that calls `Dispatchers.setMain`
 * with a `TestDispatcher` in `before` and `Dispatchers.resetMain` in `after`.
 * Standard kotlinx-coroutines-test boilerplate — drop it into
 * `app/src/test/java/com/example/daycounter/rule/MainDispatcherRule.kt` when
 * the first test that needs Main lands.
 *
 * RULES:
 * 1. ONE dispatcher source: `mainDispatcherRule.testDispatcher`. Never
 *    create a second `StandardTestDispatcher()` or `TestScope()` —
 *    `advanceUntilIdle()` only drives the dispatcher you actually share.
 * 2. ONE `setMain`: the rule does it. Never call `Dispatchers.setMain`
 *    manually in `@Before` if the rule is present.
 * 3. Top-level `runTest`: use `= runTest { }`, not `= testScope.runTest { }`.
 *
 * Why this matters even for DayCounter: `DayCounterViewModel` exposes a
 * `StateFlow`. The moment a test wants to observe state transitions over
 * time (`refresh()` after a clock advance, multiple `setStartDate` calls)
 * the dispatcher rules above kick in. Don't skip the rule for "simple"
 * VM tests — the simple-looking test is exactly the one that breaks
 * silently under a mismatched dispatcher.
 * ============================================================================
 */

/**
 * ============================================================================
 * MOCKK CONVENTIONS (Mockito is intentionally NOT brought in)
 * ============================================================================
 *
 * Use MockK for all mocking. Default to `mockk(relaxed = true)` — it's
 * the lenient default that matches what you usually want and lets the
 * test focus on the calls you actually care about.
 *
 * VARIABLE NAMING — NO `mock` PREFIX
 *
 *   // correct
 *   @MockK private lateinit var prefs: SharedPreferences
 *   private val application: Application = mockk(relaxed = true)
 *
 *   // wrong (drift from Mockito-style naming)
 *   @MockK private lateinit var mockPrefs: SharedPreferences
 *   private val mockApplication: Application = mockk(relaxed = true)
 *
 * The `@MockK` annotation or `= mockk(...)` initializer already says
 * "this is a mock". The type tells the reader what it represents.
 * `every { prefs.getString(...) }` reads better than
 * `every { mockPrefs.getString(...) }`.
 *
 * SUSPEND vs NON-SUSPEND
 * - Use `coEvery` / `coVerify` for any function declared `suspend`.
 * - Use `every` / `verify` for properties (including `val foo: Flow<…>`)
 *   and regular non-suspend functions.
 * Mixing them gives runtime errors, not compile errors.
 *
 * RELAXED vs RELAXUNITFUN
 * `relaxUnitFun = true` relaxes ONLY non-suspend `Unit`-returning
 * functions. Suspend `Unit`-returning functions still throw
 * `MockKException: no answer found` at runtime. For Fake-like mocks
 * of any repository or store with suspend methods, use
 * `relaxed = true`.
 * ============================================================================
 */

/**
 * ============================================================================
 * ANDROIDVIEWMODEL + SHAREDPREFERENCES — THE HONEST OPTIONS
 * ============================================================================
 *
 * `DayCounterViewModel` extends `AndroidViewModel(application)` and calls
 * `application.getSharedPreferences(...)` directly. That's the simplest
 * production code, but it makes JVM-only testing awkward — both
 * `Application` and `SharedPreferences` are Android types.
 *
 * Three honest options, in order of cost:
 *
 * 1) Don't unit-test the ViewModel yet.
 *    For the current scope, this is fine. The feature surface is small,
 *    the math is `ChronoUnit.between`, and the UI is hand-verified after
 *    every change. Add tests when the surface grows.
 *
 * 2) Mock `SharedPreferences` with MockK.
 *    Works for pure-stub scenarios. `prefs.edit()` returns an `Editor`,
 *    which needs its own stubbing — slightly clunky but doable. Example:
 *
 *      val prefs = mockk<SharedPreferences>(relaxed = true)
 *      val editor = mockk<SharedPreferences.Editor>(relaxed = true)
 *      every { prefs.edit() } returns editor
 *      every { editor.putString(any(), any()) } returns editor
 *
 *    Then inject a fake `Application` whose `getSharedPreferences`
 *    returns this mock.
 *
 * 3) Extract a `PrefsStore` interface.
 *    Refactor `DayCounterViewModel` to take a small interface in its
 *    constructor (e.g. `interface PrefsStore { fun getStartDate(): LocalDate?; fun setStartDate(d: LocalDate) }`)
 *    and the Android-backed impl wraps `SharedPreferences`. The
 *    ViewModel becomes a plain `ViewModel`, not an `AndroidViewModel`,
 *    and tests pass a fake `PrefsStore`. This is the right move *when*
 *    the persistence surface grows beyond one key — not before.
 *
 * Don't reach for Robolectric for this. Robolectric works for
 * `SharedPreferences` (it has an in-memory shim), but the boot cost
 * (~9s for the first test class) is not worth it for a single-VM
 * project. Either option (2) or (3) keeps the test on the JVM at
 * full speed.
 * ============================================================================
 */

/**
 * ============================================================================
 * TIME / DATE TESTS — INJECT A `Clock`, NOT THE OS
 * ============================================================================
 *
 * `LocalDate.now()` reads the system clock — testing anything that
 * depends on "today" against a moving target makes the test flaky on
 * its own. Two options:
 *
 * 1) Pass a `java.time.Clock` into the ViewModel. Default to
 *    `Clock.systemDefaultZone()` in production; pass `Clock.fixed(...)`
 *    in the test. `LocalDate.now(clock)` does the right thing.
 *
 *    This is the right pattern *when* the first time-dependent test
 *    lands. Until then, the current `LocalDate.now()` calls are fine.
 *
 * 2) For one-shot tests that only care about the *delta*, capture
 *    `vm.uiState.value.today` and assert relative to it. No clock
 *    abstraction needed — the math just has to be self-consistent
 *    within a single test run.
 *
 * `testScheduler.currentTime` from `runTest` is NOT a substitute for
 * a real clock. It's virtual coroutine time, not wall-clock time, and
 * `LocalDate.now()` ignores it entirely.
 * ============================================================================
 */
