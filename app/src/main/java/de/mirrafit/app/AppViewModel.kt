package de.mirrafit.app

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Transient message at the top of the screen, optionally with an action such as "Undo". */
data class ToastMsg(
    val text: String,
    val gold: Boolean = false,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null,
    val id: Long = System.nanoTime(),
)

enum class FormMode { PLAN_ADD, PLAN_EDIT, SESSION_ADD }

class AppViewModel(private val app: Application) : AndroidViewModel(app) {

    private val store = Store(app)

    var data by mutableStateOf(initial())
        private set

    /** Current time, refreshed by the ticker (clock and rest timer). */
    var now by mutableLongStateOf(System.currentTimeMillis())
        private set

    var toast by mutableStateOf<ToastMsg?>(null)
        private set

    /** Summary shown after "finish workout". */
    var summary by mutableStateOf<HistEntry?>(null)
        private set

    private var lastTick = -1L

    private fun initial(): AppData {
        val loaded = store.load() ?: AppData(plan = DEFAULT_PLAN)
        val t = loaded.timer
        return if (t != null && t.end <= System.currentTimeMillis()) loaded.copy(timer = null) else loaded
    }

    init {
        viewModelScope.launch {
            while (true) {
                delay(if (data.timer != null) 250L else 1000L)
                now = System.currentTimeMillis()
                val t = data.timer ?: continue

                // Optional countdown ticks in the last three seconds.
                val remaining = (t.end - now + 999) / 1000
                if (remaining in 1L..3L && remaining != lastTick) {
                    lastTick = remaining
                    if (data.settings.tickBuzz) buzz(18)
                }

                if (now >= t.end) {
                    // In the foreground the app reports the end itself. The alarm (RestAlarm) is only
                    // the fallback for background / screen off; claim() prevents a double report.
                    RestAlarm.cancel(app)
                    if (RestAlarm.claim(t.end) && now - t.end < 5000L) {
                        if (data.settings.vibrate) Feedback.buzz(app, 400, 150, 400)
                        if (data.settings.sound) Feedback.beep()
                    }
                    lastTick = -1L
                    update { it.copy(timer = null) }
                    showToast("Pause vorbei – nächster Satz!")
                }
            }
        }
    }

    private fun update(block: (AppData) -> AppData) {
        data = block(data)
        store.saveAsync(data)
    }

    private fun buzz(vararg p: Long) {
        if (data.settings.vibrate) Feedback.buzz(app, *p)
    }

    /* ---------- Toast ---------- */

    fun showToast(text: String, gold: Boolean = false, actionLabel: String? = null, action: (() -> Unit)? = null) {
        toast = ToastMsg(text, gold, actionLabel, action)
    }

    fun clearToast(id: Long) {
        if (toast?.id == id) toast = null
    }

    fun runToastAction(msg: ToastMsg) {
        clearToast(msg.id)
        msg.action?.invoke()
    }

    fun dismissSummary() {
        summary = null
    }

    /* ---------- Workout ---------- */

    /** false = plan is empty, no workout was started. */
    fun startSession(): Boolean {
        if (data.plan.isEmpty()) {
            showToast("Dein Plan ist leer – füge erst Übungen hinzu")
            return false
        }
        val session = Session(
            start = System.currentTimeMillis(),
            exercises = data.plan.map { e ->
                SessEx(e.id, e.name, e.cat, e.reps, List(e.sets) { SessSet() })
            },
        )
        update { it.copy(session = session) }
        buzz(30)
        showToast("Los geht's!")
        return true
    }

    fun discardSession() {
        update { it.copy(session = null, timer = null) }
        RestAlarm.cancel(app)
    }

    fun finishSession() {
        val s = data.session ?: return
        val exercises = s.exercises
            .map { e ->
                HistEx(
                    e.id, e.name, e.cat,
                    e.sets.filter { it.done }.map { DoneSet(num(it.kg), num(it.reps), it.pr) },
                )
            }
            .filter { it.sets.isNotEmpty() }
        if (exercises.isEmpty()) {
            discardSession()
            return
        }
        val end = System.currentTimeMillis()
        val entry = HistEntry(id = end, start = s.start, end = end, exercises = exercises)
        update {
            it.copy(
                history = (it.history + entry).sortedBy { h -> h.start },
                session = null,
                timer = null,
            )
        }
        RestAlarm.cancel(app)
        summary = entry
        buzz(50, 50, 50, 50, 120)
    }

    private fun updateEx(ei: Int, block: (SessEx) -> SessEx) {
        val s = data.session ?: return
        if (ei !in s.exercises.indices) return
        val list = s.exercises.toMutableList()
        list[ei] = block(list[ei])
        update { it.copy(session = s.copy(exercises = list)) }
    }

    fun setField(ei: Int, si: Int, isKg: Boolean, value: String) {
        updateEx(ei) { e ->
            if (si !in e.sets.indices) e else {
                val sets = e.sets.toMutableList()
                sets[si] = if (isKg) sets[si].copy(kg = value) else sets[si].copy(reps = value)
                e.copy(sets = sets)
            }
        }
    }

    /** Adjusts a field by a step (kg ± 2.5, reps ± 1), starting from the suggestion if empty. */
    fun stepField(ei: Int, si: Int, isKg: Boolean, delta: Double) {
        val s = data.session ?: return
        val ex = s.exercises.getOrNull(ei) ?: return
        val set = ex.sets.getOrNull(si) ?: return
        val sug = suggestion(lastSets(data.history, ex.id), ex, si)
        val cur = if (isKg) set.kg.ifEmpty { sug.first } else set.reps.ifEmpty { sug.second }
        val next = maxOf(0.0, num(cur) + delta)
        setField(ei, si, isKg, plainNum(next))
        buzz(8)
    }

    fun toggleSet(ei: Int, si: Int) {
        val s = data.session ?: return
        val ex = s.exercises.getOrNull(ei) ?: return
        val set = ex.sets.getOrNull(si) ?: return

        if (set.done) {
            updateEx(ei) { e ->
                val sets = e.sets.toMutableList()
                sets[si] = sets[si].copy(done = false, pr = false)
                e.copy(sets = sets)
            }
            return
        }

        // Fill empty fields from the last workout / previous set / target – one tap is enough.
        val sug = suggestion(lastSets(data.history, ex.id), ex, si)
        val kg = set.kg.ifEmpty { sug.first }
        val reps = set.reps.ifEmpty { sug.second }

        val best = bestFor(data.history, s, ex.id, ei, si)
        val kgN = num(kg)
        val rN = num(reps)
        val pr = best.seen && (
            (kgN > 0 && e1rm(kgN, rN) > best.e1rm + 0.01) ||
                (kgN == 0.0 && best.e1rm == 0.0 && rN > best.bwReps)
            )

        updateEx(ei) { e ->
            val sets = e.sets.toMutableList()
            sets[si] = SessSet(kg = kg, reps = reps, done = true, pr = pr)
            e.copy(sets = sets)
        }

        val nowComplete = data.session?.exercises?.getOrNull(ei)?.sets?.all { it.done } == true
        if (pr) {
            showToast("Neuer Bestwert: ${ex.name}", gold = true)
            buzz(30, 60, 30)
        } else if (nowComplete) {
            buzz(40, 60, 40)
        } else {
            buzz(40)
        }
        if (data.settings.autoTimer) startTimer(data.settings.rest)
    }

    fun changeSets(ei: Int, delta: Int) {
        updateEx(ei) { e ->
            if (delta > 0) e.copy(sets = e.sets + SessSet())
            else if (e.sets.size > 1) e.copy(sets = e.sets.dropLast(1))
            else e
        }
    }

    /** Moves an exercise of the running workout one step up (dir = -1) or down (dir = +1). */
    fun moveSessionEx(ei: Int, dir: Int) = moveSessionExTo(ei, ei + dir)

    /** Moves an exercise of the running workout from [from] to [to] (used by drag & drop). */
    fun moveSessionExTo(from: Int, to: Int) {
        val s = data.session ?: return
        if (from !in s.exercises.indices || to !in s.exercises.indices || from == to) return
        val list = s.exercises.toMutableList()
        list.add(to, list.removeAt(from))
        update { it.copy(session = s.copy(exercises = list)) }
    }

    fun skipExercise(ei: Int) {
        val s = data.session ?: return
        val ex = s.exercises.getOrNull(ei) ?: return
        val list = s.exercises.toMutableList()
        list.removeAt(ei)
        update { it.copy(session = s.copy(exercises = list)) }
        showToast("„${ex.name}“ übersprungen", actionLabel = "Rückgängig") {
            val cur = data.session
            if (cur != null && cur.exercises.none { it.id == ex.id }) {
                val l = cur.exercises.toMutableList()
                l.add(ei.coerceIn(0, l.size), ex)
                update { it.copy(session = cur.copy(exercises = l)) }
            }
        }
    }

    fun deleteHistory(id: Long) {
        val removed = data.history.firstOrNull { it.id == id } ?: return
        update { it.copy(history = it.history.filter { h -> h.id != id }) }
        showToast("Einheit gelöscht", actionLabel = "Rückgängig") {
            if (data.history.none { it.id == removed.id }) {
                update { it.copy(history = (it.history + removed).sortedBy { h -> h.start }) }
            }
        }
    }

    /* ---------- Rest timer ---------- */

    fun startTimer(secs: Int) {
        val end = System.currentTimeMillis() + secs * 1000L
        lastTick = -1L
        update { it.copy(timer = TimerState(end, secs)) }
        RestAlarm.schedule(app, end, data.settings.sound, data.settings.vibrate)
    }

    fun addTime(secs: Int) {
        val t = data.timer
        if (t == null) {
            startTimer(maxOf(secs, 15))
            return
        }
        val end = maxOf(System.currentTimeMillis() + 1000L, t.end + secs * 1000L)
        val total = maxOf(t.total + secs, 1)
        lastTick = -1L
        update { it.copy(timer = TimerState(end, total)) }
        RestAlarm.schedule(app, end, data.settings.sound, data.settings.vibrate)
    }

    fun stopTimer() {
        update { it.copy(timer = null) }
        RestAlarm.cancel(app)
    }

    /* ---------- Plan ---------- */

    fun movePlan(i: Int, dir: Int) = movePlanTo(i, i + dir)

    /** Moves a plan exercise from [from] to [to] (used by drag & drop). */
    fun movePlanTo(from: Int, to: Int) {
        val p = data.plan.toMutableList()
        if (from !in p.indices || to !in p.indices || from == to) return
        p.add(to, p.removeAt(from))
        update { it.copy(plan = p) }
    }

    /** Applies the plan order to the running workout (exercises not in the plan keep their place at the end). */
    fun sessionOrderLikePlan() {
        val s = data.session ?: return
        val order = data.plan.map { it.id }
        val sorted = s.exercises.sortedBy { e -> order.indexOf(e.id).let { if (it < 0) Int.MAX_VALUE else it } }
        update { it.copy(session = s.copy(exercises = sorted)) }
    }

    fun removePlan(i: Int) {
        val ex = data.plan.getOrNull(i) ?: return
        update { it.copy(plan = it.plan.filterIndexed { k, _ -> k != i }) }
        showToast("„${ex.name}“ entfernt", actionLabel = "Rückgängig") {
            if (data.plan.none { it.id == ex.id }) {
                val l = data.plan.toMutableList()
                l.add(i.coerceIn(0, l.size), ex)
                update { it.copy(plan = l) }
            }
        }
    }

    fun resetPlan() {
        update { it.copy(plan = DEFAULT_PLAN) }
    }

    /**
     * Creates or edits an exercise. [reuseId] links a new entry to an exercise that already
     * exists in history, so records and charts continue. true = saved.
     */
    fun saveExercise(
        mode: FormMode,
        idx: Int,
        name: String,
        cat: String,
        setsText: String,
        reps: String,
        alsoPlan: Boolean,
        reuseId: String? = null,
    ): Boolean {
        val n = name.trim()
        if (n.isEmpty()) {
            showToast("Bitte einen Namen eingeben")
            return false
        }
        val c = cat.trim().ifEmpty { "Allgemein" }
        val sets = (setsText.trim().toIntOrNull() ?: 3).coerceIn(1, 10)
        val r = reps.trim().ifEmpty { "10" }

        when (mode) {
            FormMode.PLAN_EDIT -> {
                val old = data.plan.getOrNull(idx) ?: return false
                val plan = data.plan.toMutableList()
                plan[idx] = old.copy(name = n, cat = c, sets = sets, reps = r)
                val session = data.session?.let { s ->
                    s.copy(
                        exercises = s.exercises.map { e ->
                            if (e.id == old.id) e.copy(name = n, cat = c, reps = r) else e
                        },
                    )
                }
                update { it.copy(plan = plan, session = session) }
            }
            FormMode.PLAN_ADD -> {
                val id = reuseId?.takeIf { rid -> data.plan.none { it.id == rid } } ?: newId()
                update { it.copy(plan = it.plan + PlanEx(id, n, c, r, sets)) }
            }
            FormMode.SESSION_ADD -> {
                val s = data.session ?: return false
                val id = reuseId?.takeIf { rid -> s.exercises.none { it.id == rid } } ?: newId()
                val ex = PlanEx(id, n, c, r, sets)
                val sessEx = SessEx(ex.id, n, c, r, List(sets) { SessSet() })
                update {
                    it.copy(
                        plan = if (alsoPlan && it.plan.none { p -> p.id == id }) it.plan + ex else it.plan,
                        session = s.copy(exercises = s.exercises + sessEx),
                    )
                }
            }
        }
        return true
    }

    /* ---------- Settings & data ---------- */

    fun setRest(v: Int) = update { it.copy(settings = it.settings.copy(rest = v)) }
    fun setAutoTimer(v: Boolean) = update { it.copy(settings = it.settings.copy(autoTimer = v)) }
    fun setSound(v: Boolean) = update { it.copy(settings = it.settings.copy(sound = v)) }
    fun setVibrate(v: Boolean) = update { it.copy(settings = it.settings.copy(vibrate = v)) }
    fun setTickBuzz(v: Boolean) = update { it.copy(settings = it.settings.copy(tickBuzz = v)) }
    fun setWeeklyGoal(v: Int) = update { it.copy(settings = it.settings.copy(weeklyGoal = v.coerceIn(1, 7))) }

    fun exportTo(uri: Uri) {
        try {
            val backup = Backup(
                exported = java.time.Instant.now().toString(),
                plan = data.plan,
                history = data.history,
                settings = data.settings,
            )
            val text = BackupJson.encodeToString(Backup.serializer(), backup)
            val out = app.contentResolver.openOutputStream(uri)
            if (out == null) {
                showToast("⚠️ Speichern fehlgeschlagen")
                return
            }
            out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            showToast("Backup gespeichert")
        } catch (e: Exception) {
            showToast("⚠️ Speichern fehlgeschlagen")
        }
    }

    /** Reads a backup file (also from the HTML app). null = invalid. */
    fun readBackup(uri: Uri): Backup? {
        return try {
            val input = app.contentResolver.openInputStream(uri) ?: return null
            val text = input.use { it.readBytes().toString(Charsets.UTF_8) }
            AppJson.decodeFromString(Backup.serializer(), text)
        } catch (e: Exception) {
            null
        }
    }

    fun applyBackup(b: Backup) {
        update {
            it.copy(
                plan = b.plan,
                history = b.history.sortedBy { h -> h.start },
                settings = b.settings,
            )
        }
        showToast("Import erfolgreich ✓")
    }

    fun wipeAll() {
        RestAlarm.cancel(app)
        update { AppData(plan = DEFAULT_PLAN) }
        showToast("Alles zurückgesetzt")
    }

    fun testFeedback() {
        val ok = Feedback.buzz(app, 300, 150, 300)
        Feedback.beep()
        showToast(
            if (ok) "Vibration gesendet – spürst du nichts, prüfe die Handy-Einstellungen"
            else "⚠️ Dieses Gerät meldet keinen Vibrationsmotor",
        )
    }
}
