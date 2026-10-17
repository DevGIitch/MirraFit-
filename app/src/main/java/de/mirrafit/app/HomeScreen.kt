package de.mirrafit.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    vm: AppViewModel,
    onStart: () -> Unit,
    onContinue: () -> Unit,
    onEditPlan: () -> Unit,
    onOpenExercise: (String) -> Unit,
    onSettings: () -> Unit,
) {
    val d = vm.data
    val hist = d.history
    val nowTs = System.currentTimeMillis()
    val thisWeek = hist.count { weekStart(it.start) == weekStart(nowTs) }
    val streak = streakWeeks(hist)
    var showAllPlan by rememberSaveable { mutableStateOf(false) }
    // Bleibt stabil beim Scrollen und Drehen, wechselt erst beim Neuöffnen der App.
    val spruch = rememberSaveable { motivationSprueche.random() }

    // History grouped into "Diese Woche", "Letzte Woche" and months (newest first).
    val groups = LinkedHashMap<String, MutableList<Pair<HistEntry, Int>>>()
    hist.asReversed().forEachIndexed { i, h ->
        groups.getOrPut(historyGroup(h.start, nowTs)) { ArrayList() }.add(h to hist.size - i)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 28.dp),
    ) {
        item(key = "title") {
            ScreenTitle(
                title = greeting(),
                subtitle = SimpleDateFormat("EEEE, d. MMMM", Locale.GERMANY).format(Date(nowTs)),
            ) {
                Image(
                    painter = painterResource(R.drawable.mirra_logo),
                    contentDescription = "MirraFit",
                    modifier = Modifier.size(34.dp),
                )
                IconBtn(Icons.Rounded.Tune, contentDescription = "Einstellungen", onClick = onSettings)
            }
        }

        item(key = "hero") {
            val s = d.session
            if (s != null) {
                val st = sessionStats(s)
                val total = s.exercises.sumOf { it.sets.size }
                val vol = fmtVolume(st.volume)
                Panel(active = true, padding = 18.dp) {
                    Text("Training läuft", color = COrange, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(fmtClock((vm.now - s.start) / 1000), style = display(60.sp, weight = FontWeight.ExtraBold), modifier = Modifier.padding(top = 6.dp))
                    Row(Modifier.padding(top = 10.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        Stat("${st.sets}/$total", "Sätze", size = 24.sp)
                        Stat(vol.first, "Volumen", unit = vol.second, size = 24.sp)
                        if (st.prs > 0) Stat("${st.prs}", "Bestwerte", color = CGold, size = 24.sp)
                    }
                    ProgressLine(if (total > 0) st.sets / total.toFloat() else 0f)
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton("Weitermachen", icon = Icons.Rounded.PlayArrow, onClick = onContinue)
                }
            } else {
                val totalSets = d.plan.sumOf { it.sets }
                Panel(padding = 18.dp) {
                    Text("Nächstes Training", color = CText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        spruch,
                        color = CMuted,
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Row(
                        Modifier.padding(top = 16.dp, bottom = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(30.dp),
                    ) {
                        Stat("${d.plan.size}", "Übungen", size = 40.sp)
                        Stat("$totalSets", "Sätze", size = 40.sp)
                        Stat("${Math.round(totalSets * 2.2)}", "Minuten ca.", size = 40.sp)
                    }
                    PrimaryButton("Training starten", icon = Icons.Rounded.PlayArrow, onClick = onStart)
                }
            }
        }

        item(key = "weekHeader") {
            SectionHeader("Diese Woche") {
                if (streak > 0) {
                    IconLabel(
                        Icons.Rounded.LocalFireDepartment,
                        "$streak ${if (streak == 1) "Woche" else "Wochen"} in Folge",
                        tint = COrange,
                    )
                }
            }
        }
        item(key = "week") { WeekPanel(hist, thisWeek, d.settings.weeklyGoal) }

        item(key = "planHeader") { SectionHeader("Plan", actionLabel = "Bearbeiten", onAction = onEditPlan) }
        item(key = "plan") {
            Panel(padding = 0.dp) {
                if (d.plan.isEmpty()) {
                    Text(
                        "Noch keine Übungen. Tippe auf „Bearbeiten“, um deinen Plan anzulegen.",
                        color = CMuted,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(18.dp),
                    )
                }
                val shown = if (showAllPlan) d.plan else d.plan.take(5)
                shown.forEachIndexed { i, e ->
                    if (i > 0) PanelDivider(Modifier.padding(start = 50.dp))
                    PlanRow(i, e, lastSets(hist, e.id)) { onOpenExercise(e.id) }
                }
                if (d.plan.size > 5) {
                    PanelDivider()
                    Text(
                        if (showAllPlan) "Weniger anzeigen" else "Alle ${d.plan.size} Übungen anzeigen",
                        color = COrange,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAllPlan = !showAllPlan }
                            .padding(14.dp),
                    )
                }
            }
        }

        item(key = "histHeader") {
            SectionHeader("Verlauf") {
                if (hist.isNotEmpty()) Text("${hist.size} Einheiten", color = CMuted, fontSize = 13.5.sp)
            }
        }
        if (groups.isEmpty()) {
            item(key = "histEmpty") {
                Panel(padding = 22.dp) {
                    Icon(Icons.Rounded.FitnessCenter, contentDescription = null, tint = CFaint, modifier = Modifier.size(30.dp))
                    Text(
                        "Hier landet jede Einheit, die du speicherst. Starte dein erstes Training.",
                        color = CMuted,
                        fontSize = 14.5.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        } else {
            groups.forEach { (label, entries) ->
                item(key = "g_$label") {
                    Text(
                        label,
                        color = CMuted,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 8.dp),
                    )
                    Panel(padding = 0.dp) {
                        entries.forEachIndexed { k, (h, n) ->
                            if (k > 0) PanelDivider(Modifier.padding(start = 72.dp))
                            HistoryRow(h, n) { vm.deleteHistory(h.id) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

private fun greeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 5 -> "Gute Nacht"
        hour < 11 -> "Guten Morgen"
        hour < 18 -> "Hallo"
        else -> "Guten Abend"
    }
}

private val motivationSprueche = listOf(
    "Heute wird's stark. Los geht's!",
    "Jeder Satz zählt.",
    "Disziplin schlägt Motivation.",
    "Du musst nicht perfekt sein, nur dranbleiben.",
    "Einmal starten, dann läuft's.",
    "Besser als gestern reicht völlig.",
    "Der schwerste Satz ist der erste.",
)

/* ---------- This week ---------- */

@Composable
private fun WeekPanel(history: List<HistEntry>, thisWeek: Int, goal: Int) {
    val ws = weekStart(System.currentTimeMillis())
    val today = dayStart(System.currentTimeMillis())
    val perDay = workoutsPerDay(history)
    val names = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
    Panel {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (k in 0 until 7) {
                val day = dayStart(addDays(ws, k))
                val trained = (perDay[day] ?: 0) > 0
                val isToday = day == today
                val future = day > today
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        names[k],
                        color = if (isToday) CText else CMuted,
                        fontSize = 12.5.sp,
                        fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    Box(
                        Modifier
                            .padding(top = 7.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    trained -> COrange
                                    future -> CField.copy(alpha = 0.45f)
                                    else -> CField
                                },
                            )
                            .then(if (isToday && !trained) Modifier.border(2.dp, COrange, CircleShape) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (trained) {
                            Icon(Icons.Rounded.Check, contentDescription = "Trainiert", tint = CDark, modifier = Modifier.size(20.dp))
                        } else {
                            Text(dayOfMonth(day), style = display(16.sp, if (future) CFaint else CMuted, FontWeight.SemiBold))
                        }
                    }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconLabel(
                Icons.Rounded.Flag,
                if (thisWeek >= goal) "Wochenziel erreicht" else "Wochenziel",
                tint = if (thisWeek >= goal) CGreen else CMuted,
                textColor = if (thisWeek >= goal) CGreen else CMuted,
                modifier = Modifier.weight(1f),
            )
            Text("$thisWeek von $goal", style = display(18.sp, weight = FontWeight.SemiBold))
        }
        ProgressLine(thisWeek / goal.toFloat())
    }
}

/* ---------- Plan preview ---------- */

@Composable
private fun PlanRow(i: Int, e: PlanEx, last: List<DoneSet>, onClick: () -> Unit) {
    val top = last.maxByOrNull { e1rm(it.kg, it.reps) + it.reps * 0.001 }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${i + 1}", style = display(20.sp, CFaint), modifier = Modifier.width(34.dp))
        Column(Modifier.weight(1f)) {
            Text(e.name, color = CText, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${e.sets} × ${e.reps} Wdh. – ${e.cat}",
                color = CMuted,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (top != null) {
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 10.dp)) {
                Text(setLabel(top.kg, top.reps), style = display(19.sp))
                Text("zuletzt", color = CFaint, fontSize = 11.sp)
            }
        }
    }
}

/* ---------- History ---------- */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryRow(entry: HistEntry, n: Int, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val st = histStats(entry.exercises)
    val vol = fmtVolume(st.volume)
    val chevron by animateFloatAsState(if (open) 180f else 0f, label = "chevron")

    Column(
        Modifier
            .fillMaxWidth()
            .clickable { open = !open }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(56.dp)) {
                Text(dayOfMonth(entry.start), style = display(28.sp, weight = FontWeight.ExtraBold))
                Text(shortMonthName(entry.start), color = CMuted, fontSize = 12.sp)
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Einheit $n", color = CText, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold)
                    if (st.prs > 0) PrBadge(st.prs, Modifier.padding(start = 8.dp))
                }
                Row(
                    Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (entry.end > entry.start) IconLabel(Icons.Rounded.Schedule, fmtDuration(entry.end - entry.start))
                    IconLabel(Icons.Rounded.FitnessCenter, "${vol.first} ${vol.second}")
                    Text("${st.sets} Sätze", color = CMuted, fontSize = 13.5.sp)
                }
            }
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = if (open) "Zuklappen" else "Aufklappen",
                tint = CFaint,
                modifier = Modifier.rotate(chevron),
            )
        }

        AnimatedVisibility(
            visible = open,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(Modifier.padding(start = 56.dp, top = 10.dp)) {
                Text(
                    "${relativeDay(entry.start)}, ${formatTime(entry.start)} Uhr",
                    color = CMuted,
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                if (entry.exercises.isEmpty()) {
                    Text("Für diese Einheit wurden keine Satzdaten gespeichert.", color = CMuted, fontSize = 13.5.sp)
                }
                entry.exercises.forEach { e ->
                    Text(
                        e.name,
                        color = CText,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 8.dp, bottom = 5.dp),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        e.sets.forEach { s -> SetChip(s) }
                    }
                }
                SecondaryButton(
                    "Einheit löschen",
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    icon = Icons.Rounded.DeleteOutline,
                    danger = true,
                    onClick = onDelete,
                )
            }
        }
    }
}

/** A finished set, e.g. "60×10"; records in gold with a trophy. */
@Composable
fun SetChip(s: DoneSet) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (s.pr) CGold.copy(alpha = 0.14f) else CField)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (s.pr) {
            Icon(Icons.Rounded.EmojiEvents, contentDescription = "Bestwert", tint = CGold, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(setLabel(s.kg, s.reps), style = display(16.sp, if (s.pr) CGold else CText, FontWeight.SemiBold))
    }
}
