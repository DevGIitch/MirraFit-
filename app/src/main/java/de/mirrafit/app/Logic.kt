package de.mirrafit.app

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

/* ---------- Numbers & formats ---------- */

/** Same as the HTML app: comma or dot, invalid = 0. */
fun num(s: String?): Double {
    if (s == null) return 0.0
    val v = s.replace(',', '.').toDoubleOrNull() ?: return 0.0
    return if (v.isNaN() || v.isInfinite()) 0.0 else v
}

/** Estimated 1RM (Epley). */
fun e1rm(kg: Double, reps: Double): Double =
    if (kg > 0 && reps > 0) kg * (1 + reps / 30.0) else 0.0

fun fmt(n: Double, digits: Int = 1): String {
    val nf = NumberFormat.getNumberInstance(Locale.GERMANY)
    nf.maximumFractionDigits = digits
    nf.minimumFractionDigits = 0
    return nf.format(n)
}

/** 60.0 -> "60", 62.5 -> "62,5" (for input fields). */
fun plainNum(d: Double): String =
    if (d == Math.rint(d) && Math.abs(d) < 1e12) d.toLong().toString()
    else d.toString().replace('.', ',')

fun fmtVolume(kg: Double): Pair<String, String> =
    if (kg >= 1000) Pair(fmt(kg / 1000.0, 1), "t") else Pair(fmt(kg, 0), "kg")

fun fmtDuration(ms: Long): String {
    val m = Math.round(ms / 60000.0).toInt()
    return if (m >= 60) "${m / 60}h ${(m % 60).toString().padStart(2, '0')}m" else "$m min"
}

fun fmtClock(totalSec: Long): String {
    val sec = if (totalSec < 0) 0L else totalSec
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    val mm = m.toString().padStart(2, '0')
    val ss = s.toString().padStart(2, '0')
    return if (h > 0) "$h:$mm:$ss" else "$mm:$ss"
}

fun formatDate(ts: Long): String =
    SimpleDateFormat("EEE, dd.MM.yyyy", Locale.GERMANY).format(Date(ts))

fun formatTime(ts: Long): String =
    SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(ts))

fun formatWeekLabel(ts: Long): String =
    SimpleDateFormat("dd.MM.", Locale.GERMANY).format(Date(ts))

fun firstNumber(s: String): String = Regex("\\d+").find(s)?.value ?: ""

fun newId(): String = "ex_" + UUID.randomUUID().toString().take(8)

/* ---------- Weeks (Monday = start of week) ---------- */

fun weekStart(ts: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = ts
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    val back = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Mo = 0 ... So = 6
    c.add(Calendar.DAY_OF_YEAR, -back)
    return c.timeInMillis
}

fun prevWeek(ts: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = ts
    c.add(Calendar.DAY_OF_YEAR, -7)
    return c.timeInMillis
}

fun streakWeeks(history: List<HistEntry>): Int {
    val weeks = history.map { weekStart(it.start) }.toSet()
    var w = weekStart(System.currentTimeMillis())
    var n = 0
    if (!weeks.contains(w)) w = prevWeek(w) // a streak stays alive until the end of the current week
    while (weeks.contains(w)) {
        n++
        w = prevWeek(w)
    }
    return n
}

/* ---------- History queries ---------- */

/** Sets of the most recent workout that contained this exercise. */
fun lastSets(history: List<HistEntry>, exId: String): List<DoneSet> {
    for (i in history.indices.reversed()) {
        val ex = history[i].exercises.firstOrNull { it.id == exId }
        if (ex != null && ex.sets.isNotEmpty()) return ex.sets
    }
    return emptyList()
}

class Best {
    var e1rm = 0.0
    var bwReps = 0.0
    var seen = false
}

/** Best values of an exercise from history + running session (excluding set ignoreEx/ignoreSet). */
fun bestFor(history: List<HistEntry>, session: Session?, exId: String, ignoreEx: Int, ignoreSet: Int): Best {
    val b = Best()
    fun consider(kg: Double, r: Double) {
        b.seen = true
        b.e1rm = maxOf(b.e1rm, e1rm(kg, r))
        if (kg == 0.0) b.bwReps = maxOf(b.bwReps, r)
    }
    for (h in history) {
        for (e in h.exercises) {
            if (e.id != exId) continue
            for (s in e.sets) consider(s.kg, s.reps)
        }
    }
    if (session != null) {
        session.exercises.forEachIndexed { ei, e ->
            if (e.id == exId) {
                e.sets.forEachIndexed { si, s ->
                    if (s.done && !(ei == ignoreEx && si == ignoreSet)) consider(num(s.kg), num(s.reps))
                }
            }
        }
    }
    return b
}

data class SessionStats(val volume: Double, val sets: Int, val prs: Int)

fun sessionStats(s: Session): SessionStats {
    var v = 0.0
    var n = 0
    var p = 0
    for (e in s.exercises) for (st in e.sets) {
        if (st.done) {
            v += num(st.kg) * num(st.reps)
            n++
            if (st.pr) p++
        }
    }
    return SessionStats(v, n, p)
}

fun histStats(exercises: List<HistEx>): SessionStats {
    var v = 0.0
    var n = 0
    var p = 0
    for (e in exercises) for (st in e.sets) {
        v += st.kg * st.reps
        n++
        if (st.pr) p++
    }
    return SessionStats(v, n, p)
}

/* ---------- Days & labels ---------- */

fun dayStart(ts: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = ts
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

fun addDays(ts: Long, days: Int): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = ts
    c.add(Calendar.DAY_OF_YEAR, days)
    return c.timeInMillis
}

/** "Heute", "Gestern" or the full date. */
fun relativeDay(ts: Long, now: Long = System.currentTimeMillis()): String {
    val d = dayStart(ts)
    val today = dayStart(now)
    return when (d) {
        today -> "Heute"
        addDays(today, -1) -> "Gestern"
        else -> formatDate(ts)
    }
}

/** "vor 3 Tagen" style label for the last workout. */
fun agoLabel(ts: Long, now: Long = System.currentTimeMillis()): String {
    val days = ((dayStart(now) - dayStart(ts)) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "heute"
        days == 1 -> "gestern"
        days < 7 -> "vor $days Tagen"
        days < 14 -> "vor 1 Woche"
        else -> "vor ${days / 7} Wochen"
    }
}

/** Section header for the history list. */
fun historyGroup(ts: Long, now: Long = System.currentTimeMillis()): String {
    val ws = weekStart(ts)
    val cur = weekStart(now)
    return when (ws) {
        cur -> "Diese Woche"
        prevWeek(cur) -> "Letzte Woche"
        else -> SimpleDateFormat("MMMM yyyy", Locale.GERMANY).format(Date(ts))
    }
}

fun shortMonth(ts: Long): String =
    SimpleDateFormat("MMM", Locale.GERMANY).format(Date(ts)).trimEnd('.').uppercase()

fun dayOfMonth(ts: Long): String =
    SimpleDateFormat("d", Locale.GERMANY).format(Date(ts))

fun fmtPercent(p: Double): String = (if (p > 0) "+" else if (p < 0) "−" else "±") + fmt(Math.abs(p), 0) + " %"

/** Compact set notation, e.g. "60×10" or "12" for bodyweight. */
fun setLabel(kg: Double, reps: Double): String =
    (if (kg > 0) fmt(kg) + "×" else "") + plainNum(reps)

/* ---------- Training helpers ---------- */

/** Upper end of a rep range: "8-10" -> 10, "12" -> 12, "" -> 0. */
fun topOfRange(reps: String): Int =
    Regex("\\d+").findAll(reps).lastOrNull()?.value?.toIntOrNull() ?: 0

/**
 * Value suggestion for a set: same set of the last workout first, otherwise the previous
 * set of the current session, otherwise the target reps. Used for placeholders and for the
 * one-tap fill when a set is checked with empty fields.
 */
fun suggestion(prev: List<DoneSet>, ex: SessEx, si: Int): Pair<String, String> {
    val p = prev.getOrNull(si)
    val before = ex.sets.getOrNull(si - 1)
    val kg = when {
        p != null && p.kg > 0 -> plainNum(p.kg)
        before != null && before.kg.isNotEmpty() -> before.kg
        else -> ""
    }
    val reps = when {
        p != null && p.reps > 0 -> plainNum(p.reps)
        before != null && before.reps.isNotEmpty() -> before.reps
        else -> firstNumber(ex.reps)
    }
    return kg to reps
}

/** True when every set of the last workout reached the top of the rep range. */
fun readyToProgress(prev: List<DoneSet>, targetReps: String): Boolean {
    val top = topOfRange(targetReps)
    return top > 0 && prev.isNotEmpty() && prev.all { it.reps >= top }
}

data class KnownEx(val id: String, val name: String, val cat: String, val reps: String, val sets: Int)

/** Every exercise ever seen (plan + history), latest name wins. Lets re-added exercises keep their stats. */
fun knownExercises(plan: List<PlanEx>, history: List<HistEntry>): List<KnownEx> {
    val map = LinkedHashMap<String, KnownEx>()
    for (h in history) for (e in h.exercises) {
        val old = map[e.id]
        map[e.id] = KnownEx(e.id, e.name, e.cat.ifBlank { old?.cat ?: "" }, old?.reps ?: "10", e.sets.size.coerceIn(1, 10))
    }
    for (p in plan) map[p.id] = KnownEx(p.id, p.name, p.cat, p.reps, p.sets)
    return map.values.sortedBy { it.name.lowercase() }
}

/* ---------- Stats helpers ---------- */

class ExSession(val start: Long, val sets: List<DoneSet>) {
    val topKg: Double = sets.maxOfOrNull { it.kg } ?: 0.0
    val topReps: Double = sets.maxOfOrNull { it.reps } ?: 0.0
    val bestE1rm: Double = sets.maxOfOrNull { e1rm(it.kg, it.reps) } ?: 0.0
    val volume: Double = sets.sumOf { it.kg * it.reps }
    val hasPr: Boolean = sets.any { it.pr }

    /** Main progress value: top weight, or top reps for bodyweight exercises. */
    val main: Double get() = if (topKg > 0) topKg else topReps
}

fun exerciseSessions(history: List<HistEntry>, exId: String): List<ExSession> =
    history.mapNotNull { h ->
        h.exercises.firstOrNull { it.id == exId && it.sets.isNotEmpty() }?.let { ExSession(h.start, it.sets) }
    }

/** Muscle group label(s) from a category such as "Brust / Trizeps" or "Bizeps · Supersatz". */
fun muscleGroups(cat: String): List<String> {
    val main = cat.substringBefore('·').trim()
    val parts = main.split('/').map { it.trim() }.filter { it.isNotEmpty() }
    return parts.ifEmpty { listOf("Sonstige") }
}

/** Completed sets per muscle group since [since], sorted descending. */
fun setsPerMuscle(history: List<HistEntry>, since: Long): List<Pair<String, Int>> {
    val map = LinkedHashMap<String, Int>()
    for (h in history) {
        if (h.start < since) continue
        for (e in h.exercises) for (g in muscleGroups(e.cat)) map[g] = (map[g] ?: 0) + e.sets.size
    }
    return map.entries.sortedByDescending { it.value }.map { it.key to it.value }
}

/** Number of workouts per calendar day. */
fun workoutsPerDay(history: List<HistEntry>): Map<Long, Int> {
    val map = HashMap<Long, Int>()
    for (h in history) {
        val d = dayStart(h.start)
        map[d] = (map[d] ?: 0) + 1
    }
    return map
}

/** Short month name in sentence case, e.g. "Okt.". */
fun shortMonthName(ts: Long): String =
    SimpleDateFormat("MMM", Locale.GERMANY).format(Date(ts))
