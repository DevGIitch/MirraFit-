package de.mirrafit.app

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/** Which bottom sheet is open. */
sealed interface Sheet {
    data object Plan : Sheet
    data object Prefs : Sheet
    data object Order : Sheet
    data class ExForm(val mode: FormMode, val idx: Int = -1) : Sheet
    data class ExDetail(val exId: String) : Sheet
}

/** Confirmation dialog. */
class Confirm(
    val title: String,
    val text: String,
    val yes: String,
    val danger: Boolean = false,
    val onYes: () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MirraFitApp(vm: AppViewModel) {
    val data = vm.data
    // While the keyboard is open the bottom bars are hidden and the content sits above the keyboard.
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var tab by rememberSaveable {
        mutableStateOf(if (vm.data.session != null) NavTab.WORKOUT else NavTab.HOME)
    }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }

    // Keep the screen on during a workout.
    val view = LocalView.current
    SideEffect { view.keepScreenOn = data.session != null }

    // System back goes to the home tab first.
    BackHandler(enabled = tab != NavTab.HOME) { tab = NavTab.HOME }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> if (uri != null) vm.exportTo(uri) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val backup = vm.readBackup(uri)
            if (backup == null) {
                vm.showToast("Ungültige Backup-Datei")
            } else {
                confirm = Confirm(
                    "Backup importieren",
                    "Backup mit ${backup.history.size} Einheiten und ${backup.plan.size} Plan-Übungen importieren? Aktuelle Daten werden ersetzt.",
                    "Importieren",
                ) { vm.applyBackup(backup) }
            }
        }
    }

    val openSettings = { sheet = Sheet.Prefs }

    Box(
        Modifier
            .fillMaxSize()
            .background(CBg),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                if (!imeVisible) {
                    Column {
                        AnimatedVisibility(
                            visible = data.timer != null || (tab == NavTab.WORKOUT && data.session != null),
                            enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
                            exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(),
                        ) {
                            RestBar(vm)
                        }
                        NavBar(tab, live = data.session != null) { tab = it }
                    }
                }
            },
        ) { pad ->
            Box(
                Modifier
                    .padding(pad)
                    .consumeWindowInsets(pad)
                    .imePadding()
                    .fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
                    Crossfade(targetState = tab, animationSpec = tween(180), label = "tab") { t ->
                        when (t) {
                            NavTab.HOME -> HomeScreen(
                                vm = vm,
                                onStart = {
                                    if (vm.startSession()) tab = NavTab.WORKOUT else sheet = Sheet.Plan
                                },
                                onContinue = { tab = NavTab.WORKOUT },
                                onEditPlan = { sheet = Sheet.Plan },
                                onOpenExercise = { sheet = Sheet.ExDetail(it) },
                                onSettings = openSettings,
                            )
                            NavTab.WORKOUT -> WorkoutScreen(
                                vm = vm,
                                ask = { confirm = it },
                                onStart = {
                                    if (!vm.startSession()) sheet = Sheet.Plan
                                },
                                onAddExercise = { sheet = Sheet.ExForm(FormMode.SESSION_ADD) },
                                onReorder = { sheet = Sheet.Order },
                                onLeave = { tab = NavTab.HOME },
                            )
                            NavTab.STATS -> StatsScreen(
                                vm,
                                onOpenExercise = { sheet = Sheet.ExDetail(it) },
                                onSettings = openSettings,
                            )
                        }
                    }
                }
            }
        }

        ToastHost(vm)
    }

    // Sheets
    val sh = sheet
    if (sh != null) {
        AppSheet(onDismiss = { sheet = null }) {
            when (sh) {
                Sheet.Plan -> PlanEditorContent(
                    vm = vm,
                    onClose = { sheet = null },
                    onForm = { mode, idx -> sheet = Sheet.ExForm(mode, idx) },
                    ask = { confirm = it },
                )
                Sheet.Order -> SessionOrderContent(vm = vm, onClose = { sheet = null })
                Sheet.Prefs -> SettingsContent(
                    vm = vm,
                    onEditPlan = { sheet = Sheet.Plan },
                    onExport = {
                        val day = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(Date())
                        exportLauncher.launch("mirrafit-backup-$day.json")
                    },
                    onImport = { importLauncher.launch(arrayOf("*/*")) },
                    ask = { confirm = it },
                    onWiped = {
                        sheet = null
                        tab = NavTab.HOME
                    },
                )
                is Sheet.ExForm -> ExerciseFormContent(
                    vm = vm,
                    mode = sh.mode,
                    idx = sh.idx,
                    onCancel = {
                        sheet = if (sh.mode == FormMode.SESSION_ADD) null else Sheet.Plan
                    },
                    onSaved = {
                        sheet = if (sh.mode == FormMode.SESSION_ADD) null else Sheet.Plan
                    },
                )
                is Sheet.ExDetail -> ExerciseDetailContent(vm = vm, exId = sh.exId)
            }
        }
    }

    // Summary after the workout
    val entry = vm.summary
    if (entry != null) {
        AppSheet(onDismiss = { vm.dismissSummary() }) {
            SummaryContent(entry, vm.data.history) { vm.dismissSummary() }
        }
    }

    // Confirmation dialog
    val c = confirm
    if (c != null) {
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = CSurfaceHi,
            titleContentColor = CText,
            textContentColor = CMuted,
            shape = RoundedCornerShape(24.dp),
            title = { Text(c.title, style = display(24.sp, weight = FontWeight.ExtraBold)) },
            text = { Text(c.text, fontSize = 15.sp, lineHeight = 21.sp) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    c.onYes()
                }) { Text(c.yes, color = if (c.danger) CDanger else COrange, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text("Abbrechen", color = CMuted, fontSize = 15.sp) }
            },
        )
    }
}

/* ---------- Bottom navigation ---------- */

@Composable
private fun NavBar(tab: NavTab, live: Boolean, onSelect: (NavTab) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(CSurface),
    ) {
        PanelDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(68.dp)
                .padding(horizontal = 8.dp),
        ) {
            NavItem(Icons.Rounded.Home, "Start", tab == NavTab.HOME, false) { onSelect(NavTab.HOME) }
            NavItem(Icons.Rounded.FitnessCenter, "Training", tab == NavTab.WORKOUT, live) { onSelect(NavTab.WORKOUT) }
            NavItem(Icons.Rounded.Insights, "Auswertung", tab == NavTab.STATS, false) { onSelect(NavTab.STATS) }
        }
    }
}

@Composable
private fun RowScope.NavItem(icon: ImageVector, label: String, active: Boolean, live: Boolean, onClick: () -> Unit) {
    val fg by animateColorAsState(if (active) CText else CMuted, label = "navFg")
    val pill by animateColorAsState(if (active) COrange.copy(alpha = 0.18f) else Color.Transparent, label = "navPill")
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(width = 60.dp, height = 32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(pill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (active) COrange else fg, modifier = Modifier.size(24.dp))
            if (live) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 3.dp, end = 13.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(CRed),
                )
            }
        }
        Text(
            label,
            color = fg,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/* ---------- Rest timer bar ---------- */

@Composable
private fun RestBar(vm: AppViewModel) {
    val t = vm.data.timer
    val rest = vm.data.settings.rest
    val running = t != null
    val remainingMs = if (t != null) maxOf(0L, t.end - vm.now) else 0L
    val remainingSec = (remainingMs + 999) / 1000
    val frac = if (t != null && t.total > 0) (remainingMs / 1000f / t.total).coerceIn(0f, 1f) else 0f
    val ending = running && remainingSec in 1L..5L
    val timeColor by animateColorAsState(if (ending) CGold else CText, label = "restTime")

    Column(
        Modifier
            .fillMaxWidth()
            .background(CSurfaceHi),
    ) {
        // Remaining time as a line along the top edge.
        ProgressLine(frac, height = 3.dp, track = CLine)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Timer, contentDescription = null, tint = if (running) COrange else CMuted, modifier = Modifier.size(22.dp))
            if (running) {
                Text(
                    "${remainingSec / 60}:${(remainingSec % 60).toString().padStart(2, '0')}",
                    style = display(34.sp, timeColor, FontWeight.ExtraBold),
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .width(76.dp),
                )
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                ) {
                    TimerKey("−15") { vm.addTime(-15) }
                    TimerKey("+15") { vm.addTime(15) }
                    TimerKey("+30") { vm.addTime(30) }
                }
                IconBtn(
                    Icons.Rounded.Close,
                    contentDescription = "Pause beenden",
                    modifier = Modifier.padding(start = 8.dp),
                    tint = CText,
                ) { vm.stopTimer() }
            } else {
                Text(
                    "Pause",
                    color = CMuted,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 10.dp),
                )
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                ) {
                    listOf(60 to "1:00", 90 to "1:30", 120 to "2:00", 180 to "3:00").forEach { (secs, label) ->
                        TimerKey(label, accent = secs == rest) { vm.startTimer(secs) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimerKey(text: String, accent: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        style = display(18.sp, if (accent) CDark else CText, FontWeight.Bold),
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier
            .pressable(onClick = onClick)
            .clip(RoundedCornerShape(10.dp))
            .background(if (accent) COrange else CField)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    )
}

/* ---------- Toast ---------- */

@Composable
private fun ToastHost(vm: AppViewModel) {
    val t = vm.toast
    // Keep the last message so it can animate out after it was cleared.
    val holder = remember { arrayOfNulls<ToastMsg>(1) }
    if (t != null) holder[0] = t
    val shown = t ?: holder[0]

    LaunchedEffect(t?.id) {
        if (t != null) {
            delay(if (t.action != null) 4200 else 2400)
            vm.clearToast(t.id)
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 8.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(
            visible = t != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it / 2 } + fadeOut(),
        ) {
            if (shown != null) {
                val shape = RoundedCornerShape(16.dp)
                Row(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .shadow(12.dp, shape)
                        .clip(shape)
                        .background(CField)
                        .padding(start = 14.dp, end = if (shown.actionLabel != null) 6.dp else 16.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (shown.gold) Icons.Rounded.EmojiEvents else Icons.Rounded.Info,
                        contentDescription = null,
                        tint = if (shown.gold) CGold else CMuted,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        shown.text,
                        color = CText,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .widthIn(max = 250.dp)
                            .padding(vertical = 8.dp),
                    )
                    if (shown.actionLabel != null) {
                        Text(
                            shown.actionLabel,
                            color = COrange,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { vm.runToastAction(shown) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
