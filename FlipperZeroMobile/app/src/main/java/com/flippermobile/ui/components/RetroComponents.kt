package com.flippermobile.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flippermobile.ui.theme.FlipperColors

val RetroShape = RoundedCornerShape(6.dp)

@Composable
fun RetroCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    borderColor: Color = FlipperColors.OrangeDim,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val clickable = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Column(
        modifier
            .clip(RetroShape)
            .background(FlipperColors.Surface)
            .border(BorderStroke(2.dp, borderColor), RetroShape)
            .then(clickable)
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun RetroButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = true,
    color: Color = FlipperColors.Orange,
) {
    val bg = when {
        !enabled -> FlipperColors.GrayDark
        filled -> color
        else -> Color.Transparent
    }
    val fg = when {
        !enabled -> FlipperColors.Gray
        filled -> FlipperColors.Black
        else -> color
    }
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(RetroShape)
            .background(bg)
            .border(2.dp, if (enabled) color else FlipperColors.GrayDark, RetroShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.uppercase(), color = fg, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(FlipperColors.Orange))
        Spacer(Modifier.width(8.dp))
        Text(text.uppercase(), style = MaterialTheme.typography.titleSmall, color = FlipperColors.Orange)
    }
}

@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = FlipperColors.White) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = FlipperColors.Gray, modifier = Modifier.weight(0.45f))
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, color = valueColor, modifier = Modifier.weight(0.55f), textAlign = TextAlign.End)
    }
}

enum class Tone(val color: Color) {
    OK(FlipperColors.Green), WARN(FlipperColors.Yellow), BAD(FlipperColors.Red),
    ACTIVE(FlipperColors.Orange), NEUTRAL(FlipperColors.Gray)
}

@Composable
fun StatusPill(label: String, value: String, tone: Tone, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RetroShape)
            .background(FlipperColors.SurfaceHigh)
            .border(1.dp, tone.color.copy(alpha = 0.6f), RetroShape)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$label: $value" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(tone.color))
        Spacer(Modifier.width(6.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = FlipperColors.Gray, maxLines = 1)
            Text(value, style = MaterialTheme.typography.labelMedium, color = tone.color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Barra de progresso segmentada estilo LCD. progress = null -> indeterminada. */
@Composable
fun SegmentedProgress(progress: Float?, modifier: Modifier = Modifier, segments: Int = 20, color: Color = FlipperColors.Orange) {
    val transition = rememberInfiniteTransition(label = "seg")
    val phase by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "phase"
    )
    Canvas(
        modifier
            .fillMaxWidth()
            .height(12.dp)
            .semantics { contentDescription = if (progress == null) "Em andamento" else "Progresso ${(progress * 100).toInt()}%" }
    ) {
        val gap = 2.dp.toPx()
        val w = (size.width - gap * (segments - 1)) / segments
        for (i in 0 until segments) {
            val on = if (progress != null) {
                i < (progress.coerceIn(0f, 1f) * segments).toInt()
            } else {
                val head = (phase * (segments + 4)).toInt()
                i in (head - 4)..head
            }
            drawRect(
                if (on) color else color.copy(alpha = 0.15f),
                topLeft = Offset(i * (w + gap), 0f),
                size = Size(w, size.height),
            )
        }
    }
}

/** Indicador de sinal em barras (0..4). */
@Composable
fun SignalBars(level: Int, modifier: Modifier = Modifier, color: Color = FlipperColors.Orange) {
    Canvas(modifier.size(width = 22.dp, height = 16.dp).semantics { contentDescription = "Sinal $level de 4" }) {
        val bw = size.width / 4 - 2.dp.toPx()
        for (i in 0 until 4) {
            val h = size.height * (i + 1) / 4f
            drawRect(
                if (i < level) color else color.copy(alpha = 0.2f),
                topLeft = Offset(i * (bw + 2.dp.toPx()), size.height - h),
                size = Size(bw, h),
            )
        }
    }
}

@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier, icon: List<String> = PixelIcons.WARNING) {
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PixelArt(icon, size = 40.dp, color = FlipperColors.OrangeDim)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = FlipperColors.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = FlipperColors.Gray, textAlign = TextAlign.Center)
    }
}

@Composable
fun MessageBanner(message: String, modifier: Modifier = Modifier, tone: Tone = Tone.WARN, action: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RetroShape)
            .background(tone.color.copy(alpha = 0.12f))
            .border(1.dp, tone.color, RetroShape)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("!", color = tone.color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(10.dp))
        Text(message, color = FlipperColors.White, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        if (action != null) {
            Spacer(Modifier.width(8.dp))
            action()
        }
    }
}

@Composable
fun RetroChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RetroShape)
            .background(if (selected) FlipperColors.Orange else FlipperColors.SurfaceHigh)
            .border(1.dp, FlipperColors.Orange, RetroShape)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (selected) FlipperColors.Black else FlipperColors.Orange)
    }
}

@Composable
fun MonoBlock(text: String, modifier: Modifier = Modifier, color: Color = FlipperColors.Green) {
    Text(
        text,
        modifier = modifier
            .fillMaxWidth()
            .clip(RetroShape)
            .background(Color.Black)
            .border(1.dp, FlipperColors.GrayDark, RetroShape)
            .padding(8.dp),
        style = MaterialTheme.typography.bodySmall,
        color = color,
    )
}
