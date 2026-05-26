package com.example.daycounter

/**
 * ============================================================================
 * STARTING POINT
 * ============================================================================
 *
 * One test file exists: `DayCounterViewModelTest` — JVM tests via
 * Robolectric 4.16.1 (needed for in-memory `SharedPreferences`).
 * Conventions in this file are what's actually applied there; the rest
 * is forward-looking guidance for when the surface grows.
 *
 * Test dependencies (`app/build.gradle.kts`):
 *
 *     testImplementation("junit:junit:4.13.2")
 *     testImplementation("org.robolectric:robolectric:4.16.1")
 *     testImplementation("androidx.test:core-ktx:1.7.0")
 *     testImplementation("androidx.test.ext:junit-ktx:1.3.0")
 *     testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
 *
 * Robolectric requires JDK 21 and matched-SDK Android JARs; 4.16.1 is
 * the first version with proper SDK 36 (Android 16) support. **Do not
 * downgrade below 4.16.1.**
 *
 * `android.testOptions.unitTests.isIncludeAndroidResources = true` in
 * `build.gradle.kts` is required — without it, Robolectric can't find
 * the merged-resources AAR layout and aborts at boot.
 *
 * MockK / Turbine are not pulled in yet. Add them when the first test
 * that actually needs mocking or flow collection over time lands.
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
 * ANDROIDVIEWMODEL + SHAREDPREFERENCES — WHAT WE PICKED
 * ============================================================================
 *
 * `DayCounterViewModel` extends `AndroidViewModel(application)` and calls
 * `application.getSharedPreferences(...)` directly. Both `Application`
 * and `SharedPreferences` are Android types, so a JVM-only test needs
 * one of: Robolectric (real shims), MockK (mock the prefs surface), or
 * a refactor that extracts the persistence behind an interface.
 *
 * The project picked **Robolectric** for `DayCounterViewModelTest`:
 *
 * - `ApplicationProvider.getApplicationContext()` returns a real-ish
 *   Application backed by Robolectric. `getSharedPreferences(...)`
 *   returns an in-memory implementation that honours the full contract
 *   (`edit().putString().apply()`, the lot) — no editor-chain mocking,
 *   no fake `Application`.
 * - Boot cost is ~4s for the first test, ~30-50ms per test after.
 *   Acceptable at this project size.
 * - Test isolation is per-`@Before clearPrefs()`, not per-class. Robolectric
 *   shares the in-memory prefs file across tests inside the same class
 *   unless you clear it.
 *
 * **Reach for MockK or a `PrefsStore` extraction instead** if:
 * - the test boot cost ever stops being acceptable, or
 * - the persistence surface grows beyond one key (extracting a
 *   `PrefsStore` interface makes the VM a plain `ViewModel`, drops the
 *   Robolectric requirement, and centralises the persistence shape).
 *
 * For now the Robolectric route is the lowest-friction path that
 * doesn't sneak production-incompatible behavior into the test (the
 * editor-chain mock has bitten several teams who got the `apply()` vs
 * `commit()` interaction wrong).
 * ============================================================================
 */

/**
 * ============================================================================
 * TIME / DATE TESTS — INJECT A `Clock`, NOT THE OS
 * ============================================================================
 *
 * `LocalDate.now()` reads the system clock — testing anything that
 * depends on "today" against a moving target makes the test flaky on
 * its own. The project applies:
 *
 *   class DayCounterViewModel @JvmOverloads constructor(
 *       application: Application,
 *       private val clock: Clock = Clock.systemDefaultZone(),
 *   ) : AndroidViewModel(application)
 *
 * `@JvmOverloads` is load-bearing: it generates the no-clock
 * `(Application)` constructor that Android's reflective ViewModel
 * factory looks for at runtime, while tests use the two-arg
 * `(Application, Clock)` form with `Clock.fixed(instant, zone)`.
 *
 * Same pattern applies any time something depends on "now" — a future
 * notification scheduler, a streak-day-rollover handler, etc. Take
 * `Clock` in the constructor; never reach for `LocalDate.now()`,
 * `Instant.now()`, or `System.currentTimeMillis()` inside a class
 * that's worth testing in isolation.
 *
 * `testScheduler.currentTime` from `runTest` is NOT a substitute. It's
 * virtual coroutine time, not wall-clock time, and `LocalDate.now()`
 * ignores it entirely.
 * ============================================================================
 */
