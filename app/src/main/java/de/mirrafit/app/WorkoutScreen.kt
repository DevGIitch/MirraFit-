package de.mirrafit.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WorkoutScreen(
    vm: AppViewModel,
    ask: (Confirm) -> Unit,
    onStart: () -> Unit,
    onAddExercise: () -> Unit,
    onReorder: () -> Unit,
    onLeave: () -> Unit,
) {
    val d = vm.data
    val s = d.session

    if (s == null) {
        NoSession(d.plan, onStart)
        return
    }

    val total = s.exercises.sumOf { it.sets.size }
    val doneN = s.exercises.sumOf { e -> e.sets.count { it.done } }
    val stats = sessionStats(s)
    val vol = fmtVolume(stats.volume)
    val currentIdx = s.exercises.indexOfFirst { e -> e.sets.any { !it.done } }

    // Finished exercises collapse automatically; the user can still open them.
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val listState = rememberLazyListState()
    var lastCurrent by remember { mutableIntStateOf(currentIdx) }
    LaunchedEffect(currentIdx) {
        // When an exercise is finished, glide to the next one.
        if (currentIdx > lastCurrent && currentIdx >= 0) listState.animateScrollToItem(currentIdx)
        lastCurrent = currentIdx
    }

    val onFinish: () -> Unit = {
        val open = total - doneN
        if (doneN == 0) {
            ask(
                Confirm("Training verwerfen", "Noch kein Satz abgehakt. Training verwerfen?", "Verwerfen", danger = true) {
                    vm.discardSession()
                    onLeave()
                },
            )
        } else {
            ask(
                Confirm(
                    "Training beenden",
                    if (open > 0) "Noch $open offene ${if (open == 1) "Satz" else "Sätze"}. Trotzdem beenden und speichern?" else "Alles erledigt. Training beenden und speichern?",
                    "Speichern",
                ) {
                    vm.finishSession()
                    onLeave()
                },
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Header: title, workout clock, progress
        Column(Modifier.padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(fmtClock((vm.now - s.start) / 1000), style = display(44.sp, weight = FontWeight.ExtraBold))
                    Text(
                        "$doneN von $total Sätzen, ${vol.first} ${vol.second}" + if (stats.prs > 0) ", ${stats.prs} Bestwert${if (stats.prs == 1) "" else "e"}" else "",
                        color = CMuted,
                        fontSize = 13.5.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                IconBtn(Icons.Rounded.SwapVert, contentDescription = "Reihenfolge ändern", size = 42.dp, tint = CText, onClick = onReorder)
            }
            ProgressLine(if (total > 0) doneN / total.toFloat() else 0f, Modifier.padding(top = 12.dp))
        }

        LazyColumn(
            Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(s.exercises) { ei, e ->
                val complete = e.sets.isNotEmpty() && e.sets.all { it.done }
                ExerciseCard(
                    e = e,
                    ei = ei,
                    count = s.exercises.size,
                    complete = complete,
                    current = ei == currentIdx,
                    open = expanded[e.id] ?: !complete,
                    onToggleOpen = { expanded[e.id] = !(expanded[e.id] ?: !complete) },
                    prev = lastSets(d.history, e.id),
                    vm = vm,
                )
            }
            item {
                SecondaryButton("Übung hinzufügen", Modifier.fillMaxWidth(), icon = Icons.Rounded.Add, onClick = onAddExercise)
            }
            item {
                PrimaryButton("Training beenden", icon = Icons.Rounded.Check, onClick = onFinish)
            }
            item {
                Text(
                    "Training verwerfen",
                    color = CDanger,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            ask(
                                Confirm("Training verwerfen", "Alle Eingaben dieser Einheit gehen verloren.", "Verwerfen", danger = true) {
                                    vm.discardSession()
                                    onLeave()
                                },
                            )
                        }
                        .padding(vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun NoSession(plan: List<PlanEx>, onStart: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        ScreenTitle("Training")
        Panel(padding = 20.dp) {
            Icon(Icons.Rounded.FitnessCenter, contentDescription = null, tint = COrange, modifier = Modifier.size(32.dp))
            Text(
                "Gerade läuft kein Training.",
                color = CText,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                if (plan.isEmpty()) "Lege zuerst Übungen in deinem Plan an."
                else "${plan.size} Übungen aus deinem Plan: ${plan.take(3).joinToString(", ") { it.name }}${if (plan.size > 3) " …" else ""}",
                color = CMuted,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
            )
            PrimaryButton("Training starten", icon = Icons.Rounded.PlayArrow, onClick = onStart)
        }
    }
}

@Composable
private fun ExerciseCard(
    e: SessEx,
    ei: Int,
    count: Int,
    complete: Boolean,
    current: Boolean,
    open: Boolean,
    onToggleOpen: () -> Unit,
    prev: List<DoneSet>,
    vm: AppViewModel,
) {
    val focus = LocalFocusManager.current
    val chevron by animateFloatAsState(if (open) 180f else 0f, label = "exChevron")
    val activeSet = if (current) e.sets.indexOfFirst { !it.done } else -1

    Panel(active = current, padding = 0.dp, modifier = Modifier.animateContentSize()) {
        // Header: order number (or check), name, target, move up/down, menu
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = complete, onClick = onToggleOpen)
                .padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = if (open) 4.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (complete) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(CGreen),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = "Erledigt", tint = CDark, modifier = Modifier.size(18.dp))
                }
            } else {
                Text(
                    "${ei + 1}",
                    style = display(26.sp, if (current) COrange else CFaint, FontWeight.ExtraBold),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(28.dp),
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 4.dp),
            ) {
                Text(
                    e.name,
                    color = CText,
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (complete && !open) {
                        e.sets.joinToString("  ") { setLabel(num(it.kg), num(it.reps)) }
                    } else {
                        "Ziel ${e.reps} Wdh. – ${e.cat}"
                    },
                    color = CMuted,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (complete) {
                Icon(
                    Icons.Rounded.ExpandMore,
                    contentDescription = if (open) "Zuklappen" else "Aufklappen",
                    tint = CFaint,
                    modifier = Modifier
                        .padding(end = 2.dp)
                        .rotate(chevron),
                )
            }
            IconBtn(
                Icons.Rounded.KeyboardArrowUp,
                contentDescription = "Nach oben",
                enabled = ei > 0,
                bg = Color.Transparent,
                size = 36.dp,
                tint = CMuted,
            ) { vm.moveSessionEx(ei, -1) }
            IconBtn(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Nach unten",
                enabled = ei < count - 1,
                bg = Color.Transparent,
                size = 36.dp,
                tint = CMuted,
            ) { vm.moveSessionEx(ei, 1) }
            ExerciseMenu(ei, vm, onAddSet = { vm.changeSets(ei, 1) })
        }

        if (open) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp)) {
                if (!complete && readyToProgress(prev, e.reps)) {
                    val bodyweight = prev.all { it.kg <= 0.0 }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, top = 4.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.TrendingUp, contentDescription = null, tint = CGold, modifier = Modifier.size(18.dp))
                        Text(
                            if (bodyweight) "Ziel zuletzt erreicht: Zeit für Zusatzgewicht?"
                            else "Ziel zuletzt erreicht: heute +2,5 kg?",
                            color = CGold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }

                // Column labels
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ColLabel("Satz", Modifier.width(30.dp), TextAlign.Start)
                    ColLabel("Vorher", Modifier.width(58.dp), TextAlign.Start)
                    ColLabel("kg", Modifier.weight(1f))
                    ColLabel("Wdh.", Modifier.weight(1f))
                    ColLabel("", Modifier.width(44.dp))
                }

                e.sets.forEachIndexed { si, st ->
                    val sug = suggestion(prev, e, si)
                    val p = prev.getOrNull(si)
                    val rowBg by animateColorAsState(
                        when {
                            st.pr -> CGold.copy(alpha = 0.10f)
                            st.done -> CGreen.copy(alpha = 0.08f)
                            else -> Color.Transparent
                        },
                        label = "rowBg",
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(rowBg)
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${si + 1}",
                            style = display(20.sp, if (si == activeSet) COrange else CMuted, FontWeight.Bold),
                            modifier = Modifier.width(30.dp),
                        )
                        Text(
                            if (p != null) setLabel(p.kg, p.reps) else "–",
                            style = display(16.sp, CFaint, FontWeight.Medium),
                            maxLines = 1,
                            modifier = Modifier.width(58.dp),
                        )
                        NumField(
                            value = st.kg,
                            placeholder = sug.first.ifEmpty { "kg" },
                            decimal = true,
                            done = st.done,
                            onChange = { vm.setField(ei, si, true, it) },
                            modifier = Modifier.weight(1f),
                        )
                        NumField(
                            value = st.reps,
                            placeholder = sug.second.ifEmpty { "–" },
                            decimal = false,
                            done = st.done,
                            onChange = { vm.setField(ei, si, false, it) },
                            modifier = Modifier.weight(1f),
                            imeAction = ImeAction.Done,
                            doneAction = {
                                // Enter on the reps field checks the set off.
                                focus.clearFocus()
                                if (!st.done) vm.toggleSet(ei, si)
                            },
                        )
                        SetCheck(done = st.done, pr = st.pr) {
                            focus.clearFocus()
                            vm.toggleSet(ei, si)
                        }
                    }
                    // Quick adjust for the active set – no keyboard needed.
                    AnimatedVisibility(
                        visible = si == activeSet,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 104.dp, end = 54.dp, top = 2.dp, bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                NudgeKey("−2,5", Modifier.weight(1f)) { vm.stepField(ei, si, true, -2.5) }
                                NudgeKey("+2,5", Modifier.weight(1f)) { vm.stepField(ei, si, true, 2.5) }
                            }
                            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                NudgeKey("−1", Modifier.weight(1f)) { vm.stepField(ei, si, false, -1.0) }
                                NudgeKey("+1", Modifier.weight(1f)) { vm.stepField(ei, si, false, 1.0) }
                            }
                        }
                    }
                }

                Row(Modifier.padding(top = 4.dp)) {
                    SetTool(Icons.Rounded.Add, "Satz") { vm.changeSets(ei, 1) }
                    if (e.sets.size > 1) SetTool(Icons.Rounded.Remove, "Satz") { vm.changeSets(ei, -1) }
                }
            }
        }
    }
}

@Composable
private fun ExerciseMenu(ei: Int, vm: AppViewModel, onAddSet: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        IconBtn(Icons.Rounded.MoreVert, contentDescription = "Optionen", bg = Color.Transparent, size = 36.dp) { menu = true }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Satz hinzufügen", color = CText) },
                leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, tint = CMuted) },
                onClick = {
                    menu = false
                    onAddSet()
                },
            )
            DropdownMenuItem(
                text = { Text("Für heute überspringen", color = CDanger) },
                leadingIcon = { Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = CDanger) },
                onClick = {
                    menu = false
                    vm.skipExercise(ei)
                },
            )
        }
    }
}

@Composable
private fun ColLabel(text: String, modifier: Modifier, align: TextAlign = TextAlign.Center) {
    Text(
        text,
        color = CFaint,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        textAlign = align,
        modifier = modifier,
    )
}

@Composable
private fun NudgeKey(text: String, modifier: Modifier, onClick: () -> Unit) {
    Text(
        text,
        style = display(15.sp, CMuted, FontWeight.SemiBold),
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = modifier
            .pressable(onClick = onClick)
            .clip(RoundedCornerShape(8.dp))
            .background(CField)
            .padding(vertical = 6.dp),
    )
}

@Composable
private fun SetTool(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = CMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, color = CMuted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
