package de.mirrafit.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/** One row of a reorderable list. [key] must be unique within the list. */
data class ReorderItem(val key: String, val title: String, val subtitle: String)

/**
 * Plain (non-lazy) list whose rows can be dragged by their handle. Rows swap live while dragging;
 * [onMove] is called with (from, to) for every step. [trailing] adds buttons per row (index).
 * [onRowClick] makes the text part of a row tappable.
 */
@Composable
fun ReorderList(
    items: List<ReorderItem>,
    onMove: (Int, Int) -> Unit,
    onRowClick: ((Int) -> Unit)? = null,
    trailing: @Composable RowScope.(Int) -> Unit = {},
) {
    val rowH = 64.dp
    val rowPx = with(LocalDensity.current) { rowH.toPx() }
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val currentItems by rememberUpdatedState(items)
    val move by rememberUpdatedState(onMove)
    val haptic = LocalHapticFeedback.current

    Column {
        items.forEachIndexed { i, item ->
            key(item.key) {
                val dragging = dragKey == item.key
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(rowH)
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (dragging) dragOffset else 0f
                            shadowElevation = if (dragging) 18f else 0f
                            shape = RoundedCornerShape(14.dp)
                            clip = dragging
                        }
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (dragging) CSurfaceHi else Color.Transparent),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(width = 40.dp, height = rowH)
                            .pointerInput(item.key) {
                                detectDragGestures(
                                    onDragStart = {
                                        dragKey = item.key
                                        dragOffset = 0f
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDragEnd = {
                                        dragKey = null
                                        dragOffset = 0f
                                    },
                                    onDragCancel = {
                                        dragKey = null
                                        dragOffset = 0f
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                        val list = currentItems
                                        val from = list.indexOfFirst { it.key == item.key }
                                        if (from >= 0) {
                                            val to = (from + (dragOffset / rowPx).roundToInt()).coerceIn(0, list.lastIndex)
                                            if (to != from) {
                                                move(from, to)
                                                // The row jumps to its new slot, so keep it under the finger.
                                                dragOffset -= (to - from) * rowPx
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                        }
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.DragIndicator,
                            contentDescription = "Ziehen zum Verschieben",
                            tint = if (dragging) COrange else CFaint,
                        )
                    }
                    Text(
                        "${i + 1}",
                        style = display(20.sp, if (dragging) COrange else CMuted),
                        modifier = Modifier.width(26.dp),
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .then(if (onRowClick != null) Modifier.clickable { onRowClick(i) } else Modifier)
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                    ) {
                        Text(
                            item.title,
                            color = CText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (item.subtitle.isNotEmpty()) {
                            Text(
                                item.subtitle,
                                color = CMuted,
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 1.dp),
                            )
                        }
                    }
                    Row(
                        Modifier.padding(end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) { trailing(i) }
                }
            }
        }
    }
}
