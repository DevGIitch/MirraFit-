package de.mirrafit.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.ListAlt
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Frame for all bottom sheets. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = CBg,
        contentColor = CText,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = CLine) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 22.dp),
            content = content,
        )
    }
}

@Composable
private fun SheetTitle(text: String, subtitle: String? = null) {
    Column(Modifier.padding(start = 2.dp, bottom = 16.dp)) {
        Text(text, style = display(30.sp, weight = FontWeight.ExtraBold))
        if (subtitle != null) Text(subtitle, color = CMuted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

/* ---------- Plan editor ---------- */

@Composable
fun PlanEditorContent(
    vm: AppViewModel,
    onClose: () -> Unit,
    onForm: (FormMode, Int) -> Unit,
    ask: (Confirm) -> Unit,
) {
    val plan = vm.data.plan
    val totalSets = plan.sumOf { it.sets }
    SheetTitle("Mein Plan", "${plan.size} Übungen, $totalSets Sätze. Griff links gedrückt halten und ziehen zum Sortieren.")
    Panel(padding = 0.dp) {
        if (plan.isEmpty()) {
            Text(
                "Noch keine Übungen. Füge unten deine erste hinzu.",
                color = CMuted,
                fontSize = 14.5.sp,
                modifier = Modifier.padding(18.dp),
            )
        }
        ReorderList(
            items = plan.mapIndexed { i, e -> ReorderItem(if (plan.count { it.id == e.id } > 1) "${e.id}#$i" else e.id, e.name, "${e.sets} × ${e.reps} Wdh. – ${e.cat}") },
            onMove = { from, to -> vm.movePlanTo(from, to) },
            onRowClick = { i -> onForm(FormMode.PLAN_EDIT, i) },
        ) { i ->
            IconBtn(Icons.Rounded.KeyboardArrowUp, "Nach oben", enabled = i > 0, bg = Color.Transparent, size = 34.dp) { vm.movePlan(i, -1) }
            IconBtn(Icons.Rounded.KeyboardArrowDown, "Nach unten", enabled = i < plan.size - 1, bg = Color.Transparent, size = 34.dp) { vm.movePlan(i, 1) }
            IconBtn(Icons.Rounded.DeleteOutline, "Entfernen", tint = CDanger, bg = Color.Transparent, size = 34.dp) { vm.removePlan(i) }
        }
    }
    Text(
        "Tippe auf eine Übung, um sie zu bearbeiten.",
        color = CFaint,
        fontSize = 12.5.sp,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
    Spacer(Modifier.height(16.dp))
    PrimaryButton("Übung hinzufügen", icon = Icons.Rounded.Add) { onForm(FormMode.PLAN_ADD, -1) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SecondaryButton("Standard-Plan", Modifier.weight(1f), icon = Icons.Rounded.Restore) {
            ask(
                Confirm("Plan zurücksetzen", "Plan auf den Standard zurücksetzen? Dein Verlauf bleibt erhalten.", "Zurücksetzen") { vm.resetPlan() },
            )
        }
        SecondaryButton("Fertig", Modifier.weight(1f), icon = Icons.Rounded.Check, onClick = onClose)
    }
}

/* ---------- Order of the running workout ---------- */

@Composable
fun SessionOrderContent(vm: AppViewModel, onClose: () -> Unit) {
    val s = vm.data.session
    SheetTitle("Reihenfolge", "Griff links gedrückt halten und ziehen, oder die Pfeile nutzen. Gilt nur für dieses Training.")
    if (s == null) {
        Text("Kein laufendes Training.", color = CMuted, fontSize = 14.5.sp)
        return
    }
    Panel(padding = 0.dp) {
        ReorderList(
            items = s.exercises.mapIndexed { i, e ->
                val done = e.sets.count { it.done }
                ReorderItem(
                    if (s.exercises.count { it.id == e.id } > 1) "${e.id}#$i" else e.id,
                    e.name,
                    if (done == e.sets.size && done > 0) "Erledigt" else "$done von ${e.sets.size} Sätzen",
                )
            },
            onMove = { from, to -> vm.moveSessionExTo(from, to) },
        ) { i ->
            IconBtn(Icons.Rounded.KeyboardArrowUp, "Nach oben", enabled = i > 0, bg = Color.Transparent, size = 36.dp) { vm.moveSessionEx(i, -1) }
            IconBtn(Icons.Rounded.KeyboardArrowDown, "Nach unten", enabled = i < s.exercises.size - 1, bg = Color.Transparent, size = 36.dp) { vm.moveSessionEx(i, 1) }
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SecondaryButton("Wie im Plan", Modifier.weight(1f), icon = Icons.Rounded.Sort) { vm.sessionOrderLikePlan() }
        SecondaryButton("Fertig", Modifier.weight(1f), icon = Icons.Rounded.Check, onClick = onClose)
    }
}

/* ---------- Create / edit exercise ---------- */

private val REP_PRESETS = listOf("5", "6-8", "8-10", "10-12", "12-15", "15-20")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseFormContent(
    vm: AppViewModel,
    mode: FormMode,
    idx: Int,
    onCancel: () -> Unit,
    onSaved: () -> Unit,
) {
    val initial = if (mode == FormMode.PLAN_EDIT) vm.data.plan.getOrNull(idx) else null
    var name by remember(mode, idx) { mutableStateOf(initial?.name ?: "") }
    var cat by remember(mode, idx) { mutableStateOf(initial?.cat ?: "") }
    var sets by remember(mode, idx) { mutableIntStateOf(initial?.sets ?: 3) }
    var reps by remember(mode, idx) { mutableStateOf(initial?.reps ?: "10-12") }
    var alsoPlan by remember(mode, idx) { mutableStateOf(true) }
    var reuse by remember(mode, idx) { mutableStateOf<KnownEx?>(null) }

    // Exercises from plan + history that can be picked again (keeps records and charts).
    val taken: Set<String> = when (mode) {
        FormMode.SESSION_ADD -> vm.data.session?.exercises?.map { it.id }?.toSet() ?: emptySet()
        else -> vm.data.plan.map { it.id }.toSet()
    }
    val known = if (mode == FormMode.PLAN_EDIT) emptyList() else {
        val q = name.trim().lowercase()
        knownExercises(vm.data.plan, vm.data.history)
            .filter { it.id !in taken }
            .filter { q.isEmpty() || reuse != null || it.name.lowercase().contains(q) }
            .take(12)
    }
    val cats = (vm.data.plan.map { it.cat } + vm.data.history.flatMap { h -> h.exercises.map { it.cat } })
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .map { it.key }
        .filter { it != cat.trim() }
        .take(8)

    SheetTitle(
        if (mode == FormMode.PLAN_EDIT) "Übung bearbeiten" else "Neue Übung",
        if (mode == FormMode.SESSION_ADD) "Wird zum laufenden Training hinzugefügt." else null,
    )

    if (known.isNotEmpty()) {
        FieldLabel("Schon mal gemacht")
        FlowRow(
            Modifier.padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            known.forEach { k ->
                val on = reuse?.id == k.id
                Chip(k.name, selected = on) {
                    if (on) {
                        reuse = null
                    } else {
                        reuse = k
                        name = k.name
                        cat = k.cat
                        reps = k.reps
                        sets = k.sets
                    }
                }
            }
        }
    }

    LabeledField(
        "Name",
        name,
        {
            name = it
            if (reuse != null && it.trim() != reuse?.name) reuse = null
        },
        placeholder = "z. B. Kabelflys",
    )
    LabeledField("Muskelgruppe", cat, { cat = it }, Modifier.padding(bottom = 0.dp), placeholder = "z. B. Brust / Trizeps")
    if (cats.isNotEmpty()) {
        FlowRow(
            Modifier.padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            cats.forEach { c -> Chip(c) { cat = c } }
        }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            FieldLabel("Sätze")
            Stepper(sets, 1, 10, { sets = it }, Modifier.fillMaxWidth())
        }
        Column(Modifier.weight(1f)) {
            LabeledField("Wiederholungen", reps, { reps = it }, Modifier.fillMaxWidth())
        }
    }
    FlowRow(
        Modifier.padding(top = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        REP_PRESETS.forEach { p -> Chip(p, selected = reps.trim() == p) { reps = p } }
    }
    if (mode == FormMode.SESSION_ADD) {
        ToggleRow("Auch dauerhaft in den Plan", alsoPlan) { alsoPlan = it }
    }
    if (reuse != null) {
        Text(
            "Verlauf und Bestwerte von „${reuse?.name}“ werden weitergeführt.",
            color = CGold,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SecondaryButton("Abbrechen", Modifier.weight(1f), onClick = onCancel)
        PrimaryButton("Speichern", Modifier.weight(1f)) {
            if (vm.saveExercise(mode, idx, name, cat, sets.toString(), reps, alsoPlan, reuse?.id)) onSaved()
        }
    }
}

/* ---------- Settings ---------- */

@Composable
fun ToggleRow(label: String, checked: Boolean, hint: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = CText, fontSize = 15.5.sp)
            if (hint != null) Text(hint, color = CMuted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CDark,
                checkedTrackColor = COrange,
                checkedBorderColor = COrange,
                uncheckedThumbColor = CMuted,
                uncheckedTrackColor = CField,
                uncheckedBorderColor = CField,
            ),
        )
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title,
        color = CMuted,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp),
    )
    Panel(padding = 0.dp) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), content = content)
    }
    Spacer(Modifier.height(18.dp))
}

@Composable
fun SettingsContent(
    vm: AppViewModel,
    onEditPlan: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    ask: (Confirm) -> Unit,
    onWiped: () -> Unit,
) {
    val st = vm.data.settings
    SheetTitle("Einstellungen")

    SettingsGroup("Pause") {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text("Standard-Pause", color = CText, fontSize = 15.5.sp)
            Segmented(
                options = listOf("1:00" to 60, "1:30" to 90, "2:00" to 120, "3:00" to 180),
                selected = st.rest,
                onSelect = { vm.setRest(it) },
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        PanelDivider()
        ToggleRow("Timer automatisch starten", st.autoTimer, hint = "Nach jedem abgehakten Satz") { vm.setAutoTimer(it) }
        PanelDivider()
        ToggleRow("Ton am Ende", st.sound) { vm.setSound(it) }
        PanelDivider()
        ToggleRow("Vibration", st.vibrate) { vm.setVibrate(it) }
        PanelDivider()
        ToggleRow("Countdown spüren", st.tickBuzz, hint = "Kurzes Vibrieren in den letzten 3 Sekunden") { vm.setTickBuzz(it) }
        SecondaryButton(
            "Vibration und Ton testen",
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 12.dp),
            icon = Icons.Rounded.Vibration,
        ) { vm.testFeedback() }
    }

    SettingsGroup("Wochenziel") {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text("Trainings pro Woche", color = CText, fontSize = 15.5.sp)
            Segmented(
                options = (1..6).map { "$it" to it },
                selected = st.weeklyGoal,
                onSelect = { vm.setWeeklyGoal(it) },
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }

    SettingsGroup("Daten") {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SecondaryButton("Backup sichern", Modifier.weight(1f), icon = Icons.Rounded.FileDownload, onClick = onExport)
            SecondaryButton("Backup laden", Modifier.weight(1f), icon = Icons.Rounded.FileUpload, onClick = onImport)
        }
        SecondaryButton(
            "Plan bearbeiten",
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            icon = Icons.Rounded.ListAlt,
            onClick = onEditPlan,
        )
        SecondaryButton(
            "Alle Daten löschen",
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 12.dp),
            icon = Icons.Rounded.DeleteOutline,
            danger = true,
        ) {
            ask(
                Confirm(
                    "Alle Daten löschen",
                    "Wirklich alle Daten löschen? Das lässt sich nicht rückgängig machen. Sichere vorher ein Backup.",
                    "Löschen",
                    danger = true,
                ) {
                    vm.wipeAll()
                    onWiped()
                },
            )
        }
    }

    Text(
        "MirraFit $APP_VERSION. Deine Daten liegen nur auf diesem Gerät. Backups aus der HTML-App lassen sich laden.",
        color = CFaint,
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/* ---------- Summary after the workout ---------- */

private val SUMMARY_LINES = listOf(
    "Starke Einheit!",
    "Wieder ein Stück gewachsen.",
    "Dranbleiben zahlt sich aus.",
    "Sauber durchgezogen.",
    "Das war ordentlich Arbeit.",
)

@Composable
fun SummaryContent(entry: HistEntry, history: List<HistEntry>, onClose: () -> Unit) {
    val st = histStats(entry.exercises)
    val vol = fmtVolume(st.volume)
    val line = remember(entry.id) { SUMMARY_LINES.random() }
    val prNames = entry.exercises.filter { e -> e.sets.any { it.pr } }.map { it.name }
    val idx = history.indexOfFirst { it.id == entry.id }
    val before = if (idx > 0) history[idx - 1] else null
    val beforeVol = before?.let { histStats(it.exercises).volume } ?: 0.0
    val volDiff = if (beforeVol > 0) (st.volume - beforeVol) / beforeVol * 100.0 else null

    val pop = remember { Animatable(0.5f) }
    LaunchedEffect(entry.id) {
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .padding(top = 4.dp)
                .size(72.dp)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                }
                .clip(CircleShape)
                .background(if (prNames.isNotEmpty()) CGold else CGreen),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (prNames.isNotEmpty()) Icons.Rounded.EmojiEvents else Icons.Rounded.Check,
                contentDescription = null,
                tint = CDark,
                modifier = Modifier.size(38.dp),
            )
        }
        Text(line, style = display(32.sp, weight = FontWeight.ExtraBold), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
        Text("Einheit gespeichert", color = CMuted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))
    }
    Panel(padding = 18.dp) {
        Row(Modifier.fillMaxWidth()) {
            Stat(fmtDuration(entry.end - entry.start), "Dauer", Modifier.weight(1f), size = 34.sp)
            Stat(vol.first, "Volumen", Modifier.weight(1f), unit = vol.second, size = 34.sp)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) {
            Stat("${st.sets}", "Sätze", Modifier.weight(1f), size = 34.sp)
            Stat("${st.prs}", "Bestwerte", Modifier.weight(1f), color = if (st.prs > 0) CGold else CText, size = 34.sp)
        }
        if (volDiff != null) {
            PanelDivider(Modifier.padding(top = 16.dp, bottom = 12.dp))
            Text(
                "${fmtPercent(volDiff)} Volumen gegenüber der letzten Einheit",
                color = if (volDiff >= 0) CGreen else CMuted,
                fontSize = 14.sp,
            )
        }
    }
    if (prNames.isNotEmpty()) {
        Panel(Modifier.padding(top = 12.dp), padding = 16.dp) {
            IconLabel(Icons.Rounded.EmojiEvents, "Neue Bestwerte", tint = CGold, textColor = CGold)
            Text(prNames.joinToString(", "), color = CText, fontSize = 14.5.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
    Spacer(Modifier.height(18.dp))
    PrimaryButton("Weiter", onClick = onClose)
}

/* ---------- Exercise detail ---------- */

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseDetailContent(vm: AppViewModel, exId: String) {
    val hist = vm.data.history
    val sessions = exerciseSessions(hist, exId)
    val lastEx = hist.lastOrNull { h -> h.exercises.any { it.id == exId } }?.exercises?.firstOrNull { it.id == exId }
    val name = lastEx?.name ?: vm.data.plan.firstOrNull { it.id == exId }?.name ?: "Übung"
    val cat = vm.data.plan.firstOrNull { it.id == exId }?.cat ?: lastEx?.cat ?: ""

    SheetTitle(name, cat.ifBlank { null })

    if (sessions.isEmpty()) {
        Panel(padding = 18.dp) {
            Text(
                "Für diese Übung gibt es noch keine gespeicherten Sätze. Nach dem ersten Training siehst du hier Verlauf und Bestwerte.",
                color = CMuted,
                fontSize = 14.5.sp,
                lineHeight = 20.sp,
            )
        }
        return
    }

    val bodyweight = sessions.all { it.topKg <= 0.0 }
    var metric by remember(exId) { mutableIntStateOf(0) } // 0 = top weight/reps, 1 = e1RM, 2 = volume
    val bestE = sessions.maxOf { it.bestE1rm }
    val bestSet = sessions.flatMap { it.sets }.maxByOrNull { e1rm(it.kg, it.reps) + it.reps * 0.001 }
    val totalVol = fmtVolume(sessions.sumOf { it.volume })

    Panel(padding = 18.dp) {
        Row(Modifier.fillMaxWidth()) {
            if (bestSet != null) Stat(setLabel(bestSet.kg, bestSet.reps), "Bester Satz", Modifier.weight(1f), color = CGold, size = 30.sp)
            if (!bodyweight) Stat(fmt(bestE, 0), "1RM geschätzt", Modifier.weight(1f), unit = "kg", size = 30.sp)
            Stat("${sessions.size}", "Einheiten", Modifier.weight(1f), size = 30.sp)
        }
    }

    val recent = sessions.takeLast(20)
    val values = recent.map {
        when (metric) {
            1 -> it.bestE1rm
            2 -> it.volume
            else -> it.main
        }
    }

    Panel(Modifier.padding(top = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Verlauf", color = CText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Segmented(
                options = if (bodyweight) listOf("Wdh." to 0) else listOf("Gewicht" to 0, "1RM" to 1, "Volumen" to 2),
                selected = metric,
                onSelect = { metric = it },
            )
        }
        LineChart(values, recent.map { it.hasPr }, metric, Modifier.padding(top = 16.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, start = 40.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(formatWeekLabel(recent.first().start), style = display(13.sp, CMuted, FontWeight.Medium))
            Text(formatWeekLabel(recent.last().start), style = display(13.sp, CMuted, FontWeight.Medium))
        }
        Text(
            "Insgesamt ${totalVol.first} ${totalVol.second} bewegt. Goldene Punkte markieren Bestwerte.",
            color = CFaint,
            fontSize = 12.5.sp,
            modifier = Modifier.padding(top = 10.dp),
        )
    }

    SectionHeader("Letzte Einheiten")
    Panel(padding = 0.dp) {
        sessions.asReversed().take(8).forEachIndexed { i, s ->
            if (i > 0) PanelDivider(Modifier.padding(start = 16.dp))
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(relativeDay(s.start), color = CText, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text(
                        if (s.topKg > 0) "1RM ≈ ${fmt(s.bestE1rm, 0)} kg" else "max. ${plainNum(s.topReps)} Wdh.",
                        color = CMuted,
                        fontSize = 12.5.sp,
                    )
                }
                FlowRow(
                    Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    s.sets.forEach { SetChip(it) }
                }
            }
        }
    }
}

/** Line chart with max/min labels; sessions with a record get a gold dot. */
@Composable
private fun LineChart(values: List<Double>, prs: List<Boolean>, metric: Int, modifier: Modifier = Modifier) {
    val grow = remember { Animatable(0f) }
    LaunchedEffect(metric, values.size) {
        grow.snapTo(0f)
        grow.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    val mn = values.minOrNull() ?: 0.0
    val mx = values.maxOrNull() ?: 0.0
    Row(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .height(160.dp)
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(fmtAxis(mx, metric), style = display(14.sp, CMuted, FontWeight.Medium))
            Text(fmtAxis(mn, metric), style = display(14.sp, CMuted, FontWeight.Medium))
        }
        Canvas(
            Modifier
                .weight(1f)
                .height(160.dp),
        ) {
            val padY = 8.dp.toPx()
            val h = size.height - 2 * padY
            for (k in 0..3) {
                val y = padY + h * k / 3f
                drawLine(CLine, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            if (values.isEmpty()) return@Canvas
            val range = if (mx - mn == 0.0) 1.0 else mx - mn
            val pts = values.mapIndexed { i, v ->
                val x = if (values.size == 1) size.width / 2f else size.width * i / (values.size - 1f)
                val norm = if (mx - mn == 0.0) 0.5f else ((v - mn) / range).toFloat()
                Offset(x, padY + h - norm * h * grow.value)
            }
            if (pts.size > 1) {
                val area = Path().apply {
                    moveTo(pts.first().x, size.height)
                    pts.forEach { lineTo(it.x, it.y) }
                    lineTo(pts.last().x, size.height)
                    close()
                }
                drawPath(area, Brush.verticalGradient(listOf(COrange.copy(alpha = 0.22f), Color.Transparent)))
                val line = Path().apply {
                    moveTo(pts.first().x, pts.first().y)
                    for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                }
                drawPath(line, COrange, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            pts.forEachIndexed { i, p ->
                val pr = prs.getOrNull(i) == true
                drawCircle(CSurface, radius = 5.5.dp.toPx(), center = p)
                drawCircle(if (pr) CGold else COrange, radius = if (pr) 4.5.dp.toPx() else 3.dp.toPx(), center = p)
            }
        }
    }
}

private fun fmtAxis(v: Double, metric: Int): String =
    if (metric == 2) fmtVolume(v).let { it.first + " " + it.second } else fmt(v, 1)
