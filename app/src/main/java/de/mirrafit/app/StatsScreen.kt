package de.mirrafit.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingFlat
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private class WeekPoint(val ws: Long, val n: Int, val v: Double)

private class ExAgg(val id: String, var name: String) {
    val points = ArrayList<Double>()
    var bestSet: DoneSet? = null
    var bestE = 0.0
    var bestReps = 0.0
    var last = 0L
}

@Composable
fun StatsScreen(vm: AppViewModel, onOpenExercise: (String) -> Unit, onSettings: () -> Unit) {
    val hist = vm.data.history

    if (hist.isEmpty()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            ScreenTitle("Auswertung") {
                IconBtn(Icons.Rounded.Tune, contentDescription = "Einstellungen", onClick = onSettings)
            }
            Panel(padding = 20.dp) {
                Icon(Icons.Rounded.Insights, contentDescription = null, tint = COrange, modifier = Modifier.size(32.dp))
                Text(
                    "Noch nichts auszuwerten.",
                    color = CText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    "Sobald du deine erste Einheit speicherst, siehst du hier Volumen, Bestwerte, Konstanz und deinen Fortschritt je Übung.",
                    color = CMuted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        return
    }

    // Totals
    var totalVol = 0.0
    var totalSets = 0
    var totalPrs = 0
    var totalTime = 0L
    var timedCount = 0
    for (h in hist) {
        val s = histStats(h.exercises)
        totalVol += s.volume
        totalSets += s.sets
        totalPrs += s.prs
        if (h.end > h.start) {
            totalTime += h.end - h.start
            timedCount++
        }
    }
    val vol = fmtVolume(totalVol)
    val streak = streakWeeks(hist)

    // Last 8 weeks
    val weeks = ArrayList<Long>()
    var w = weekStart(System.currentTimeMillis())
    for (i in 0 until 8) {
        weeks.add(0, w)
        w = prevWeek(w)
    }
    val weekData = weeks.map { ws ->
        val hs = hist.filter { weekStart(it.start) == ws }
        WeekPoint(ws, hs.size, hs.sumOf { histStats(it.exercises).volume })
    }

    // Progress per exercise
    val map = LinkedHashMap<String, ExAgg>()
    for (h in hist) {
        for (ex in h.exercises) {
            if (ex.sets.isEmpty()) continue
            val m = map.getOrPut(ex.id) { ExAgg(ex.id, ex.name) }
            m.name = ex.name
            m.last = maxOf(m.last, h.start)
            var top = 0.0
            var topReps = 0.0
            for (st in ex.sets) {
                val est = e1rm(st.kg, st.reps)
                top = maxOf(top, st.kg)
                topReps = maxOf(topReps, st.reps)
                if (est > m.bestE) {
                    m.bestE = est
                    m.bestSet = st
                }
                m.bestReps = maxOf(m.bestReps, st.reps)
            }
            m.points.add(if (top > 0) top else topReps)
        }
    }
    val exList = map.values.sortedWith(compareByDescending<ExAgg> { it.points.size }.thenByDescending { it.last })
    val muscles = setsPerMuscle(hist, addDays(dayStart(System.currentTimeMillis()), -29))

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 28.dp),
    ) {
        item(key = "title") {
            ScreenTitle("Auswertung", subtitle = "Seit ${formatDate(hist.first().start)}") {
                IconBtn(Icons.Rounded.Tune, contentDescription = "Einstellungen", onClick = onSettings)
            }
        }
        item(key = "overview") {
            Panel(padding = 18.dp) {
                Row(Modifier.fillMaxWidth()) {
                    Stat(vol.first, "Volumen gesamt", Modifier.weight(1f), unit = vol.second, size = 40.sp)
                    Stat("${hist.size}", "Einheiten", Modifier.weight(1f), size = 40.sp)
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp),
                ) {
                    Stat("$totalPrs", "Bestwerte", Modifier.weight(1f), color = CGold, size = 40.sp)
                    Stat(
                        if (timedCount > 0) fmt(totalTime / timedCount / 60000.0, 0) else "–",
                        "Dauer im Schnitt",
                        Modifier.weight(1f),
                        unit = "min",
                        size = 40.sp,
                    )
                }
                PanelDivider(Modifier.padding(top = 16.dp, bottom = 12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconLabel(
                        Icons.Rounded.LocalFireDepartment,
                        if (streak > 0) "$streak ${if (streak == 1) "Woche" else "Wochen"} in Folge trainiert" else "Diese Woche noch kein Training",
                        tint = if (streak > 0) COrange else CFaint,
                        modifier = Modifier.weight(1f),
                    )
                    Text("$totalSets Sätze", color = CMuted, fontSize = 13.5.sp)
                }
            }
        }
        item(key = "weeksHeader") { SectionHeader("Letzte 8 Wochen") }
        item(key = "weeks") { WeeklyChart(weekData) }
        item(key = "heatHeader") { SectionHeader("Konstanz") }
        item(key = "heat") { ConsistencyPanel(hist) }
        if (muscles.isNotEmpty()) {
            item(key = "musclesHeader") { SectionHeader("Muskelgruppen, letzte 30 Tage") }
            item(key = "muscles") { MusclePanel(muscles) }
        }
        item(key = "exHeader") { SectionHeader("Übungen") }
        item(key = "exList") {
            Panel(padding = 0.dp) {
                exList.forEachIndexed { i, m ->
                    if (i > 0) PanelDivider(Modifier.padding(start = 16.dp))
                    ExStatRow(m) { onOpenExercise(m.id) }
                }
            }
        }
    }
}

/* ---------- Weekly chart ---------- */

@Composable
private fun WeeklyChart(weekData: List<WeekPoint>) {
    val hasVolume = weekData.any { it.v > 0 }
    var mode by rememberSaveable { mutableIntStateOf(if (hasVolume) 0 else 1) } // 0 = volume, 1 = workouts
    val useVol = mode == 0
    val maxVal = maxOf(1.0, weekData.maxOf { if (useVol) it.v else it.n.toDouble() })
    val grow = remember { Animatable(0f) }
    LaunchedEffect(mode) {
        grow.snapTo(0f)
        grow.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
    }

    Panel {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (useVol) "Bewegtes Gewicht pro Woche" else "Einheiten pro Woche",
                color = CMuted,
                fontSize = 13.5.sp,
                modifier = Modifier.weight(1f),
            )
            Segmented(
                options = listOf("Volumen" to 0, "Anzahl" to 1),
                selected = mode,
                onSelect = { mode = it },
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            weekData.forEachIndexed { i, wp ->
                val v = if (useVol) wp.v else wp.n.toDouble()
                val label = if (v > 0) {
                    if (useVol) fmtVolume(wp.v).first else "${wp.n}"
                } else ""
                val barHeight = if (v > 0) maxOf(6.0, v / maxVal * 104.0 * grow.value) else 4.0
                val isNow = i == weekData.size - 1
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Text(label, style = display(15.sp, if (isNow) COrange else CText, FontWeight.SemiBold), maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(barHeight.dp)
                            .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                            .background(
                                when {
                                    v <= 0 -> CField
                                    isNow -> COrange
                                    else -> COrange.copy(alpha = 0.42f)
                                },
                            ),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (isNow) "Jetzt" else formatWeekLabel(wp.ws),
                        style = display(13.sp, if (isNow) COrange else CMuted, FontWeight.Medium),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/* ---------- Consistency grid ---------- */

@Composable
private fun ConsistencyPanel(hist: List<HistEntry>) {
    val weeksBack = 16
    val perDay = workoutsPerDay(hist)
    val today = dayStart(System.currentTimeMillis())
    var start = weekStart(today)
    repeat(weeksBack - 1) { start = prevWeek(start) }
    var trainedDays = 0
    for (k in 0 until weeksBack * 7) {
        val d = dayStart(addDays(start, k))
        if (d <= today && (perDay[d] ?: 0) > 0) trainedDays++
    }
    val cellDp = 17.dp

    Panel {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$trainedDays", style = display(34.sp, weight = FontWeight.ExtraBold))
            Text(
                "Trainingstage in $weeksBack Wochen",
                color = CMuted,
                fontSize = 13.5.sp,
                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
            )
        }
        Row(Modifier.padding(top = 14.dp)) {
            Column(Modifier.padding(end = 6.dp)) {
                listOf("Mo", "", "Mi", "", "Fr", "", "So").forEach {
                    Box(Modifier.height(cellDp), contentAlignment = Alignment.CenterStart) {
                        Text(it, color = CFaint, fontSize = 10.sp)
                    }
                }
            }
            Canvas(
                Modifier
                    .weight(1f)
                    .height(cellDp * 7),
            ) {
                val cols = weeksBack
                val cell = minOf(size.width / cols, size.height / 7f)
                val gap = cell * 0.18f
                val x0 = size.width - cell * cols
                for (c in 0 until cols) {
                    for (row in 0 until 7) {
                        val d = dayStart(addDays(start, c * 7 + row))
                        val n = perDay[d] ?: 0
                        val color = when {
                            d > today -> Color.Transparent
                            n > 1 -> CGold
                            n == 1 -> COrange
                            else -> CField
                        }
                        val tl = Offset(x0 + c * cell + gap / 2f, row * cell + gap / 2f)
                        val sz = Size(cell - gap, cell - gap)
                        drawRoundRect(color, topLeft = tl, size = sz, cornerRadius = CornerRadius(cell * 0.22f))
                        if (d == today) {
                            drawRoundRect(
                                CText.copy(alpha = 0.85f),
                                topLeft = tl,
                                size = sz,
                                cornerRadius = CornerRadius(cell * 0.22f),
                                style = Stroke(1.2.dp.toPx()),
                            )
                        }
                    }
                }
            }
        }
        Row(
            Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Legend(CField, "Pause")
            Legend(COrange, "1 Einheit")
            Legend(CGold, "2 oder mehr")
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
        Text(label, color = CMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 5.dp))
    }
}

/* ---------- Muscle groups ---------- */

@Composable
private fun MusclePanel(muscles: List<Pair<String, Int>>) {
    val top = muscles.take(7)
    val max = maxOf(1, top.maxOf { it.second })
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }

    Panel {
        top.forEach { (name, n) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    name,
                    color = CText,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(100.dp),
                )
                Canvas(
                    Modifier
                        .weight(1f)
                        .height(8.dp),
                ) {
                    val cr = CornerRadius(size.height / 2f)
                    drawRoundRect(CField, cornerRadius = cr)
                    drawRoundRect(
                        COrange,
                        size = Size(size.width * (n / max.toFloat()) * grow.value, size.height),
                        cornerRadius = cr,
                    )
                }
                Text(
                    "$n",
                    style = display(18.sp, weight = FontWeight.SemiBold),
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(38.dp),
                )
            }
        }
        Text("Gezählt werden erledigte Sätze; Übungen mit zwei Gruppen zählen für beide.", color = CFaint, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

/* ---------- Exercise list ---------- */

@Composable
private fun ExStatRow(m: ExAgg, onClick: () -> Unit) {
    val pts = m.points.takeLast(12)
    val first = pts.first()
    val last = pts.last()
    val diff = if (pts.size > 1 && first != 0.0) (last - first) / first * 100.0 else 0.0
    val bs = m.bestSet
    val best = if (bs != null) "Bester Satz ${fmt(bs.kg)} kg × ${plainNum(bs.reps)}" else "Beste Serie ${plainNum(m.bestReps)} Wdh."

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(m.name, color = CText, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(best, color = CMuted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                if (pts.size >= 2) {
                    val (icon, color) = when {
                        diff > 0.5 -> Icons.Rounded.TrendingUp to CGreen
                        diff < -0.5 -> Icons.Rounded.TrendingDown to CDanger
                        else -> Icons.Rounded.TrendingFlat to CMuted
                    }
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                    Text(
                        if (Math.abs(diff) > 0.5) fmtPercent(diff) else "stabil",
                        style = display(15.sp, color, FontWeight.SemiBold),
                        modifier = Modifier.padding(start = 4.dp, end = 10.dp),
                    )
                }
                Text("${m.points.size}× trainiert", color = CFaint, fontSize = 12.sp)
            }
        }
        Spark(pts)
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = CFaint)
    }
}

@Composable
private fun Spark(pts: List<Double>) {
    Canvas(Modifier.size(width = 80.dp, height = 34.dp)) {
        val sx = size.width / 96f
        val sy = size.height / 36f
        if (pts.size < 2) {
            drawLine(
                CLine,
                Offset(4f * sx, 18f * sy),
                Offset(92f * sx, 18f * sy),
                strokeWidth = 2f * sx,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * sx, 4f * sx), 0f),
            )
            return@Canvas
        }
        val mn = pts.minOrNull() ?: 0.0
        val mx = pts.maxOrNull() ?: 0.0
        val range = if (mx - mn == 0.0) 1.0 else mx - mn
        val xy = pts.mapIndexed { i, p ->
            Offset(
                (4f + i * (88f / (pts.size - 1))) * sx,
                (32f - ((p - mn) / range).toFloat() * 26f) * sy,
            )
        }
        val area = Path()
        area.moveTo(4f * sx, 34f * sy)
        xy.forEach { area.lineTo(it.x, it.y) }
        area.lineTo(92f * sx, 34f * sy)
        area.close()
        drawPath(area, Brush.verticalGradient(listOf(COrange.copy(alpha = 0.25f), Color.Transparent)))

        val line = Path()
        line.moveTo(xy[0].x, xy[0].y)
        for (i in 1 until xy.size) line.lineTo(xy[i].x, xy[i].y)
        drawPath(line, COrange, style = Stroke(width = 2.2f * sx, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(COrange, radius = 3f * sx, center = xy.last())
    }
}
