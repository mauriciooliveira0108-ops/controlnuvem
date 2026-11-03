package com.flippermobile.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flippermobile.ui.theme.FlipperColors
import kotlinx.coroutines.delay

/** Mascote original: golfinho em pixel art (24x16), com flutuação e piscar. */
private val DOLPHIN_OPEN = listOf(
    "...........##...........",
    "..........###...........",
    "........######..........",
    "......##########........",
    "....##############......",
    "...######o#########.....",
    "..##################....",
    ".####################...",
    "######..wwwwww#######...",
    "####...wwwwwwwww######..",
    "##......wwwwwww...#####.",
    ".........wwww......####.",
    "....................###.",
    "...................####.",
    "..................##..##",
    ".................##....#",
)

private val DOLPHIN_BLINK = DOLPHIN_OPEN.mapIndexed { i, row -> if (i == 5) row.replace('o', '#') else row }

@Composable
fun DolphinMascot(modifier: Modifier = Modifier, size: Dp = 96.dp, animated: Boolean = true) {
    var blink by remember { mutableStateOf(false) }
    if (animated) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(2800)
                blink = true
                delay(160)
                blink = false
            }
        }
    }
    val transition = rememberInfiniteTransition(label = "dolphin")
    val bob by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (animated) 4f else 0f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "bob",
    )
    PixelArt(
        grid = if (blink) DOLPHIN_BLINK else DOLPHIN_OPEN,
        modifier = modifier.offset(y = bob.dp),
        size = size,
        color = FlipperColors.Orange,
        accent = FlipperColors.Black,
        contentDescription = "Mascote golfinho",
    )
}
