package com.example.daycounter

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * ViewModel unit tests under Robolectric — we need a real (in-memory)
 * `SharedPreferences`. The whole feature is a pure date-math layer over
 * one prefs key, so the tests pin both halves: persistence round-trips
 * and Y/M/W/T breakdown across the boundary cases that are easy to get
 * wrong by hand.
 *
 * `Clock` is injected so "today" is deterministic; production gets
 * `Clock.systemDefaultZone()` via the `@JvmOverloads` no-Clock
 * constructor that the Android ViewModel factory finds at runtime.
 */
@RunWith(RobolectricTestRunner::class)
class DayCounterViewModelTest {

    private val application: Application = ApplicationProvider.getApplicationContext()
    private val today: LocalDate = LocalDate.of(2026, 5, 26)
    private val clock: Clock = Clock.fixed(
        today.atStartOfDay(ZoneId.systemDefault()).toInstant(),
        ZoneId.systemDefault(),
    )

    @Before
    fun clearPrefs() {
        application
            .getSharedPreferences("day_counter_prefs", Context.MODE_PRIVATE)
            .edit { clear() }
    }

    // ── Defaults & persistence ────────────────────────────────────────────────

    @Test
    fun `default start date is today when no preference stored`() {
        val vm = DayCounterViewModel(application, clock)
        assertEquals(today, vm.uiState.value.startDate)
        assertEquals(0L, vm.uiState.value.totalDays)
    }

    @Test
    fun `setStartDate updates state and total`() {
        val vm = DayCounterViewModel(application, clock)
        vm.setStartDate(today.minusDays(10))
        assertEquals(today.minusDays(10), vm.uiState.value.startDate)
        assertEquals(10L, vm.uiState.value.totalDays)
    }

    @Test
    fun `start date survives ViewModel re-creation`() {
        val pick = today.minusDays(42)
        DayCounterViewModel(application, clock).setStartDate(pick)
        val vm2 = DayCounterViewModel(application, clock)
        assertEquals(pick, vm2.uiState.value.startDate)
        assertEquals(42L, vm2.uiState.value.totalDays)
    }

    @Test
    fun `state exposes today from the injected clock`() {
        val vm = DayCounterViewModel(application, clock)
        assertEquals(today, vm.uiState.value.today)
    }

    // ── totalDays edge cases ──────────────────────────────────────────────────

    @Test
    fun `totalDays = 0 when start is today`() {
        val vm = DayCounterViewModel(application, clock)
        vm.setStartDate(today)
        assertEquals(0L, vm.uiState.value.totalDays)
    }

    @Test
    fun `totalDays = 1 when start is yesterday`() {
        val vm = DayCounterViewModel(application, clock)
        vm.setStartDate(today.minusDays(1))
        assertEquals(1L, vm.uiState.value.totalDays)
    }

    @Test
    fun `totalDays is negative when start is in the future`() {
        val vm = DayCounterViewModel(application, clock)
        vm.setStartDate(today.plusDays(5))
        assertEquals(-5L, vm.uiState.value.totalDays)
    }

    // ── Y/M/W/T breakdown ─────────────────────────────────────────────────────

    @Test
    fun `breakdown 1 year exactly`() {
        val vm = DayCounterViewModel(application, clock)
        vm.setStartDate(today.minusYears(1))
        with(vm.uiState.value) {
            assertEquals(1L, years)
            assertEquals(0L, months)
            assertEquals(0L, weeks)
            assertEquals(0L, days)
        }
    }

    @Test
    fun `breakdown 1 year 2 months 1 week 3 days`() {
        val vm = DayCounterViewModel(application, clock)
        vm.setStartDate(
            today.minusYears(1).minusMonths(2).minusWeeks(1).minusDays(3)
        )
        with(vm.uiState.value) {
            assertEquals(1L, years)
            assertEquals(2L, months)
            assertEquals(1L, weeks)
            assertEquals(3L, days)
        }
    }

    @Test
    fun `breakdown 10 days only — splits into 1 week 3 days`() {
        val vm = DayCounterViewModel(application, clock)
        vm.setStartDate(today.minusDays(10))
        with(vm.uiState.value) {
            assertEquals(0L, years)
            assertEquals(0L, months)
            assertEquals(1L, weeks)
            assertEquals(3L, days)
        }
    }

    @Test
    fun `breakdown 6 days — still under a week`() {
        val vm = DayCounterViewModel(application, clock)
        vm.setStartDate(today.minusDays(6))
        with(vm.uiState.value) {
            assertEquals(0L, weeks)
            assertEquals(6L, days)
        }
    }

    @Test
    fun `breakdown handles month boundary correctly`() {
        // 2026-05-26 minus 1 month = 2026-04-26 → exactly 1 month
        val vm = DayCounterViewModel(application, clock)
        vm.setStartDate(LocalDate.of(2026, 4, 26))
        with(vm.uiState.value) {
            assertEquals(0L, years)
            assertEquals(1L, months)
            assertEquals(0L, weeks)
            assertEquals(0L, days)
        }
    }

    @Test
    fun `breakdown handles leap-year span`() {
        // Feb 29, 2024 → May 26, 2026 = 2 years, 2 months, 3 weeks, 5 days
        // start = 2024-02-29; +2y = 2026-02-28 (Feb 2026 has 28 days);
        // +2m = 2026-04-28; remaining 28 days = 4 weeks, 0 days.
        val vm = DayCounterViewModel(application, clock)
        vm.setStartDate(LocalDate.of(2024, 2, 29))
        with(vm.uiState.value) {
            assertEquals(2L, years)
            assertEquals(2L, months)
            assertEquals(4L, weeks)
            assertEquals(0L, days)
        }
    }
}
