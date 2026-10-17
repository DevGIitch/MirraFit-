package de.mirrafit.app

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Same JSON format as the HTML app backup (plan, history, settings). */
val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    coerceInputValues = true
    isLenient = true
}

val BackupJson = Json(AppJson) {
    prettyPrint = true
}

@Serializable
data class PlanEx(
    val id: String,
    val name: String,
    val cat: String = "Allgemein",
    val reps: String = "10",
    val sets: Int = 3,
)

@Serializable
data class DoneSet(
    val kg: Double = 0.0,
    val reps: Double = 0.0,
    val pr: Boolean = false,
)

@Serializable
data class HistEx(
    val id: String,
    val name: String,
    val cat: String = "",
    val sets: List<DoneSet> = emptyList(),
)

@Serializable
data class HistEntry(
    val id: Long,
    val start: Long,
    val end: Long = 0L,
    val exercises: List<HistEx> = emptyList(),
)

@Serializable
data class AppSettings(
    val rest: Int = 90,
    val autoTimer: Boolean = true,
    val sound: Boolean = true,
    val vibrate: Boolean = true,
    /** Workouts per week that count as the goal (ring on the home screen). */
    val weeklyGoal: Int = 3,
    /** Short haptic tick during the last 3 seconds of a rest. */
    val tickBuzz: Boolean = false,
)

@Serializable
data class SessSet(
    val kg: String = "",
    val reps: String = "",
    val done: Boolean = false,
    val pr: Boolean = false,
)

@Serializable
data class SessEx(
    val id: String,
    val name: String,
    val cat: String = "",
    val reps: String = "",
    val sets: List<SessSet> = emptyList(),
)

@Serializable
data class Session(
    val start: Long,
    val exercises: List<SessEx> = emptyList(),
)

@Serializable
data class TimerState(
    val end: Long,
    val total: Int,
)

@Serializable
data class AppData(
    val plan: List<PlanEx> = emptyList(),
    val history: List<HistEntry> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val session: Session? = null,
    val timer: TimerState? = null,
)

@Serializable
data class Backup(
    val app: String = "mirrafit",
    val version: Int = 2,
    val exported: String = "",
    val plan: List<PlanEx>,
    val history: List<HistEntry>,
    val settings: AppSettings = AppSettings(),
)

const val APP_VERSION = "2.1"

val DEFAULT_PLAN = listOf(
    PlanEx("ex1", "Klimmzüge (Untergriff)", "Rücken / Bizeps", "6-10", 3),
    PlanEx("ex2", "Schrägbankdrücken", "Brust / Schulter", "8-10", 3),
    PlanEx("ex3", "Breites Rudern", "Rücken", "10-12", 3),
    PlanEx("ex4", "Dips", "Brust / Trizeps", "8-10", 3),
    PlanEx("ex5", "Seitheben", "Schulter", "12-15", 3),
    PlanEx("ex6", "SZ- / Hammercurls", "Bizeps · Supersatz", "10-12", 3),
    PlanEx("ex7", "Trizeps-Drücken Seil", "Trizeps · Supersatz", "12-15", 3),
    PlanEx("ex8", "Beinheben", "Bauch", "12-15", 3),
    PlanEx("ex9", "Crunches", "Bauch", "15-20", 2),
)
