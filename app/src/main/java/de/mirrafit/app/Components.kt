package de.mirrafit.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ---------- Colors (taken from the Mirra logo) ---------- */

val CBg = Color(0xFF0A1424)
val CSurface = Color(0xFF111E33)
val CSurfaceHi = Color(0xFF16263F)
val CField = Color(0xFF1B2C46)
val CLine = Color(0xFF1F3150)
val CText = Color(0xFFEEF3F9)
val CMuted = Color(0xFF8B9BB1)
val CFaint = Color(0xFF52657F)
val CGreen = Color(0xFF3DD68C)
val CDanger = Color(0xFFFF6F7A)
val CDark = Color(0xFF0D1420)

/** Logo gradient: red → orange → gold. Orange = action/active, gold = records, red = gradient start. */
val CRed = Color(0xFFE2404B)
val COrange = Color(0xFFF07D3C)
val CGold = Color(0xFFF4B24C)

val BrandBrush: Brush = Brush.horizontalGradient(listOf(CRed, COrange, CGold))

/** Color on the logo gradient, t = 0 (red) to 1 (gold). */
fun brandColorAt(t: Float): Color {
    val x = t.coerceIn(0f, 1f)
    return if (x <= 0.5f) lerp(CRed, COrange, x * 2f) else lerp(COrange, CGold, (x - 0.5f) * 2f)
}

/* ---------- Type ---------- */

/** Condensed display face for titles and all "scoreboard" numbers (clock, timer, stats). */
val Condensed = FontFamily(
    Font(R.font.barlow_condensed_medium, FontWeight.Medium),
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_bold, FontWeight.Bold),
    Font(R.font.barlow_condensed_extrabold, FontWeight.ExtraBold),
)

/** Scoreboard numbers / titles in the condensed face with tabular figures. */
fun display(size: TextUnit, color: Color = CText, weight: FontWeight = FontWeight.Bold): TextStyle =
    TextStyle(
        fontFamily = Condensed,
        fontSize = size,
        lineHeight = size,
        fontWeight = weight,
        color = color,
        fontFeatureSettings = "tnum",
    )

@Composable
fun MirraFitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = COrange,
            onPrimary = CDark,
            secondary = CGold,
            onSecondary = CDark,
            background = CBg,
            onBackground = CText,
            surface = CSurface,
            onSurface = CText,
            surfaceVariant = CField,
            onSurfaceVariant = CMuted,
            surfaceContainer = CSurfaceHi,
            surfaceContainerHigh = CSurfaceHi,
            surfaceContainerHighest = CField,
            surfaceContainerLow = CSurface,
            outline = CLine,
            outlineVariant = CLine,
            error = CDanger,
        ),
        content = content,
    )
}

/* ---------- Layout building blocks ---------- */

val PanelShape = RoundedCornerShape(20.dp)

/**
 * The one surface type of the app: a flat panel. [active] marks the thing that needs attention
 * right now (current exercise, running workout) with an orange edge on the left.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    active: Boolean = false,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(PanelShape)
            .background(if (active) CSurfaceHi else CSurface)
            .then(
                if (active) {
                    Modifier.drawBehind {
                        val inset = 18.dp.toPx()
                        val w = 3.dp.toPx()
                        drawRoundRect(
                            COrange,
                            topLeft = Offset(0f, inset),
                            size = Size(w, (size.height - 2 * inset).coerceAtLeast(w)),
                            cornerRadius = CornerRadius(w / 2f),
                        )
                    }
                } else {
                    Modifier
                },
            )
            .padding(padding),
        content = content,
    )
}

/** Thin divider between rows inside a panel. */
@Composable
fun PanelDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(CLine),
    )
}

/** Large left-aligned screen title with an optional line below and trailing actions. */
@Composable
fun ScreenTitle(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = display(34.sp, weight = FontWeight.ExtraBold))
            if (subtitle != null) {
                Text(subtitle, color = CMuted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

/** Section header above a panel. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 0.dp, top = 24.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = CText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
        if (actionLabel != null) {
            Text(
                actionLabel,
                color = COrange,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/** Scoreboard figure: big condensed number with a small label below. */
@Composable
fun Stat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    unit: String = "",
    color: Color = CText,
    size: TextUnit = 30.sp,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = display(size, color), maxLines = 1)
            if (unit.isNotEmpty()) {
                Text(
                    unit,
                    style = display(size * 0.55f, CMuted, FontWeight.SemiBold),
                    modifier = Modifier.padding(start = 3.dp, bottom = 2.dp),
                )
            }
        }
        Text(label, color = CMuted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
    }
}

/** Small icon + text line, e.g. "🔥 4 Wochen in Folge". */
@Composable
fun IconLabel(icon: ImageVector, text: String, tint: Color = CMuted, textColor: Color = CMuted, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
        Text(text, color = textColor, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 5.dp))
    }
}

/* ---------- Interaction ---------- */

/** Clickable with a small press-scale. */
@Composable
fun Modifier.pressable(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "press")
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = src,
            indication = LocalIndication.current,
            enabled = enabled,
            onClick = onClick,
        )
}

/* ---------- Buttons ---------- */

/** The main action of a screen – the only place the full logo gradient is used as a fill. */
@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .clip(shape)
            .background(BrandBrush)
            .padding(vertical = 15.dp, horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = CDark, modifier = Modifier.size(22.dp))
            Box(Modifier.width(8.dp))
        }
        Text(text, color = CDark, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val fg = if (danger) CDanger else CText
    Row(
        modifier
            .pressable(onClick = onClick)
            .clip(shape)
            .background(if (danger) CDanger.copy(alpha = 0.10f) else CField)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (danger) CDanger else CMuted, modifier = Modifier.size(19.dp))
            Box(Modifier.width(8.dp))
        }
        Text(
            text,
            color = fg,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Round icon button. */
@Composable
fun IconBtn(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = CMuted,
    bg: Color = CField,
    size: Dp = 38.dp,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .size(size)
            .pressable(enabled = enabled, onClick = onClick)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.25f),
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

/**
 * Checkbox for a set: empty = outlined box, done = green with a check,
 * personal record = gold with a trophy.
 */
@Composable
fun SetCheck(done: Boolean, pr: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        if (done) 1f else 0.92f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "setcheck",
    )
    val bg by animateColorAsState(
        when {
            pr -> CGold
            done -> CGreen
            else -> CField
        },
        label = "setcheckBg",
    )
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .size(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(bg)
            .then(if (done) Modifier else Modifier.border(1.5.dp, CFaint, shape))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (pr) Icons.Rounded.EmojiEvents else Icons.Rounded.Check,
            contentDescription = if (done) "Satz erledigt" else "Satz abhaken",
            tint = if (done) CDark else CFaint,
            modifier = Modifier.size(22.dp),
        )
    }
}

/* ---------- Progress ---------- */

/** Thin progress line in the logo gradient. */
@Composable
fun ProgressLine(frac: Float, modifier: Modifier = Modifier, height: Dp = 5.dp, track: Color = CField) {
    val shown by animateFloatAsState(frac.coerceIn(0f, 1f), animationSpec = tween(500), label = "line")
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = CornerRadius(size.height / 2f)
        drawRoundRect(track, cornerRadius = r)
        if (shown > 0f) drawRoundRect(BrandBrush, size = Size(size.width * shown, size.height), cornerRadius = r)
    }
}

/** Circular progress (rest timer). */
@Composable
fun ProgressRing(
    frac: Float,
    modifier: Modifier,
    stroke: Dp = 5.dp,
    track: Color = CField,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val f = frac.coerceIn(0f, 1f)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val sw = stroke.toPx()
            val inset = sw / 2
            val arcSize = Size(size.width - sw, size.height - sw)
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = sw),
            )
            if (f > 0f) {
                // Sweep gradients start at 3 o'clock, so rotate to let red start at 12 o'clock.
                rotate(-90f) {
                    drawArc(
                        brush = Brush.sweepGradient(listOf(CRed, COrange, CGold, CGold), center = center),
                        startAngle = 0f,
                        sweepAngle = 360f * f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = sw, cap = StrokeCap.Round),
                    )
                }
            }
        }
        content()
    }
}

/* ---------- Small parts ---------- */

/** Trophy + count for personal records. */
@Composable
fun PrBadge(count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CGold.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.EmojiEvents, contentDescription = "Bestwerte", tint = CGold, modifier = Modifier.size(14.dp))
        if (count > 1) Text("$count", style = display(14.sp, CGold), modifier = Modifier.padding(start = 3.dp))
    }
}

/** Rounded selectable chip. */
@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(10.dp)
    Text(
        text,
        color = if (selected) CDark else CText,
        fontSize = 13.5.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(shape)
            .background(if (selected) COrange else CField)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/* ---------- Input fields ---------- */

/** Number field for kg / reps on the workout screen. */
@Composable
fun NumField(
    value: String,
    placeholder: String,
    decimal: Boolean,
    done: Boolean,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
    doneAction: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(12.dp)
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    BasicTextField(
        value = value,
        onValueChange = { onChange(it.filter { c -> c.isDigit() || c == '.' || c == ',' }) },
        singleLine = true,
        interactionSource = src,
        textStyle = display(22.sp, if (done) CText else CText, FontWeight.Bold).copy(textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = imeAction,
        ),
        keyboardActions = doneAction?.let { action -> KeyboardActions(onDone = { action() }) } ?: KeyboardActions.Default,
        cursorBrush = SolidColor(COrange),
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(if (done) Color.Transparent else CField)
            .then(if (focused) Modifier.border(1.5.dp, COrange, shape) else Modifier)
            .padding(horizontal = 4.dp),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
                if (value.isEmpty()) {
                    Text(placeholder, style = display(20.sp, CFaint, FontWeight.SemiBold), maxLines = 1)
                }
                inner()
            }
        },
    )
}

/** Labelled text field for forms. */
@Composable
fun LabeledField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val shape = RoundedCornerShape(14.dp)
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    Column(modifier.padding(bottom = 14.dp)) {
        FieldLabel(label)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            interactionSource = src,
            textStyle = TextStyle(color = CText, fontSize = 16.sp),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
            cursorBrush = SolidColor(COrange),
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(CField)
                .then(if (focused) Modifier.border(1.5.dp, COrange, shape) else Modifier)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(placeholder, color = CFaint, fontSize = 16.sp)
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
fun FieldLabel(text: String) {
    Text(
        text,
        color = CMuted,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
    )
}

/** Choice between a few values (e.g. rest time). */
@Composable
fun Segmented(options: List<Pair<String, Int>>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .clip(shape)
            .background(CField)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (label, value) ->
            val on = value == selected
            Text(
                label,
                color = if (on) CDark else CMuted,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (on) COrange else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 11.dp, vertical = 7.dp),
            )
        }
    }
}

/** − value + stepper. */
@Composable
fun Stepper(value: Int, min: Int, max: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .clip(shape)
            .background(CField)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepKey("−", enabled = value > min) { onChange(value - 1) }
        Text(
            "$value",
            style = display(22.sp),
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        StepKey("+", enabled = value < max) { onChange(value + 1) }
    }
}

@Composable
private fun StepKey(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(42.dp)
            .pressable(enabled = enabled, onClick = onClick)
            .clip(RoundedCornerShape(11.dp))
            .background(CSurfaceHi),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (enabled) CText else CFaint, fontSize = 20.sp, fontWeight = FontWeight.Medium)
    }
}

fun Modifier.dashedBorder(color: Color, radius: Dp = 12.dp, width: Dp = 1.dp): Modifier = this.drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(
            width = width.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 9f), 0f),
        ),
    )
}

/* ---------- Navigation ---------- */

enum class NavTab { HOME, WORKOUT, STATS }
