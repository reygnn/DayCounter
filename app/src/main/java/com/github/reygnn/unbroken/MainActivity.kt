package com.github.reygnn.unbroken

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// ── ViewModel ────────────────────────────────────────────────────────────────

data class UnbrokenUiState(
    val totalDays: Long = 0,
    val startDate: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val years: Long = 0,
    val months: Long = 0,
    val weeks: Long = 0,
    val days: Long = 0,
)

class UnbrokenViewModel @JvmOverloads constructor(
    application: Application,
    private val clock: Clock = Clock.systemDefaultZone(),
) : AndroidViewModel(application) {

    companion object {
        private const val PREFS_NAME = "unbroken_prefs"
        private const val KEY_START_DATE = "start_date"
    }

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(calculateState())
    val uiState: StateFlow<UnbrokenUiState> = _uiState.asStateFlow()

    fun refresh() {
        _uiState.value = calculateState()
    }

    fun setStartDate(date: LocalDate) {
        prefs.edit { putString(KEY_START_DATE, date.toString()) }
        refresh()
    }

    private fun getStartDate(): LocalDate {
        val stored = prefs.getString(KEY_START_DATE, null)
        return if (stored != null) LocalDate.parse(stored) else LocalDate.now(clock)
    }

    private fun calculateState(): UnbrokenUiState {
        val today = LocalDate.now(clock)
        val start = getStartDate()
        val totalDays = ChronoUnit.DAYS.between(start, today)

        val years = ChronoUnit.YEARS.between(start, today)
        val afterYears = start.plusYears(years)

        val months = ChronoUnit.MONTHS.between(afterYears, today)
        val afterMonths = afterYears.plusMonths(months)

        val remainingDays = ChronoUnit.DAYS.between(afterMonths, today)
        val weeks = remainingDays / 7
        val days = remainingDays % 7

        return UnbrokenUiState(
            totalDays = totalDays,
            startDate = start,
            today = today,
            years = years,
            months = months,
            weeks = weeks,
            days = days,
        )
    }
}

// ── Activity ─────────────────────────────────────────────────────────────────

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UnbrokenTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    UnbrokenScreen()
                }
            }
        }
    }
}

// ── Theme ────────────────────────────────────────────────────────────────────

@Composable
fun UnbrokenTheme(
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val colorScheme = if (darkTheme) dynamicDarkColorScheme(context)
    else dynamicLightColorScheme(context)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}

// ── UI ───────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnbrokenScreen(viewModel: UnbrokenViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("dd. MMMM yyyy", Locale.GERMAN)
    }
    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Unbroken ${BuildConfig.VERSION_NAME}") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Seit dem",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = state.startDate.format(dateFormatter),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { showDatePicker = true }
                    .padding(8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        val calculated = (maxWidth / "${state.totalDays}".length) * 1.5f
                        val fontSize = calculated.value.coerceIn(96f, 192f).sp

                        Text(
                            text = "${state.totalDays}",
                            fontSize = fontSize,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                    Text(
                        text = if (state.totalDays == 1L) "Tag" else "Tage",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${state.years}J / ${state.months}M / ${state.weeks}W / ${state.days}T",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            FilledTonalButton(onClick = { viewModel.refresh() }) {
                Text("Aktualisieren")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Heute: ${state.today.format(dateFormatter)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showDatePicker) {
        StartDatePickerDialog(
            initial = state.startDate,
            onDismiss = { showDatePicker = false },
            onConfirm = { picked ->
                viewModel.setStartDate(picked)
                showDatePicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartDatePickerDialog(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    val initialMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = pickerState.selectedDateMillis ?: return@TextButton
                val picked = Instant.ofEpochMilli(millis)
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate()
                onConfirm(picked)
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    ) {
        DatePicker(state = pickerState)
    }
}
