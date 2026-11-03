package com.flippermobile.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flippermobile.ui.theme.FlipperColors

/**
 * Desenha pixel art a partir de linhas de texto:
 * '#' = cor principal, 'o' = cor de destaque, 'w' = branco, '.' ou ' ' = transparente.
 */
@Composable
fun PixelArt(
    grid: List<String>,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    color: Color = FlipperColors.Orange,
    accent: Color = FlipperColors.White,
    contentDescription: String? = null,
) {
    val rows = grid.size
    val cols = grid.maxOfOrNull { it.length } ?: 0
    val semantic = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
    Canvas(modifier.size(size).then(semantic)) {
        if (rows == 0 || cols == 0) return@Canvas
        val cell = minOf(this.size.width / cols, this.size.height / rows)
        val ox = (this.size.width - cell * cols) / 2f
        val oy = (this.size.height - cell * rows) / 2f
        grid.forEachIndexed { y, line ->
            line.forEachIndexed { x, c ->
                val fill = when (c) {
                    '#' -> color
                    'o' -> accent
                    'w' -> FlipperColors.White
                    'k' -> FlipperColors.Black
                    else -> null
                }
                if (fill != null) {
                    // +0.5 evita frestas entre pixels por arredondamento
                    drawRect(fill, Offset(ox + x * cell, oy + y * cell), Size(cell + 0.5f, cell + 0.5f))
                }
            }
        }
    }
}

/** Ícones pixel-art originais (12x12). */
object PixelIcons {
    val NFC = listOf(
        "............",
        ".##########.",
        ".#........#.",
        ".#.#....#.#.",
        ".#.##...#.#.",
        ".#.#.#..#.#.",
        ".#.#..#.#.#.",
        ".#.#...##.#.",
        ".#.#....#.#.",
        ".#........#.",
        ".##########.",
        "............",
    )
    val BLUETOOTH = listOf(
        ".....#......",
        ".....##.....",
        ".....#.#....",
        ".#...#..#...",
        "..#..#.#....",
        "...#.##.....",
        "...#.##.....",
        "..#..#.#....",
        ".#...#..#...",
        ".....#.#....",
        ".....##.....",
        ".....#......",
    )
    val WIFI = listOf(
        "............",
        "...######...",
        "..#......#..",
        ".#..####..#.",
        "#..#....#..#",
        "..#..##..#..",
        "....#..#....",
        "............",
        ".....##.....",
        ".....##.....",
        "............",
        "............",
    )
    val INFRARED = listOf(
        "............",
        "..####......",
        ".#....#..#..",
        ".#.oo.#...#.",
        ".#.oo.#.#..#",
        ".#....#..#.#",
        ".#....#.#..#",
        ".#.##.#...#.",
        ".#.##.#..#..",
        ".#....#.....",
        "..####......",
        "............",
    )
    val DEVICE = listOf(
        "...######...",
        "..#......#..",
        "..#.####.#..",
        "..#.#..#.#..",
        "..#.#..#.#..",
        "..#.#..#.#..",
        "..#.####.#..",
        "..#......#..",
        "..#......#..",
        "..#..##..#..",
        "..#......#..",
        "...######...",
    )
    val TERMINAL = listOf(
        "############",
        "#..........#",
        "#.#........#",
        "#..#.......#",
        "#...#......#",
        "#..#.......#",
        "#.#..####..#",
        "#..........#",
        "#..........#",
        "############",
        "............",
        "............",
    )
    val SAVED = listOf(
        "#########...",
        "#.......##..",
        "#.#####.#.#.",
        "#.#####.#..#",
        "#..........#",
        "#..........#",
        "#.########.#",
        "#.#......#.#",
        "#.#......#.#",
        "#.#......#.#",
        "#.########.#",
        "############",
    )
    val SETTINGS = listOf(
        ".....##.....",
        "..#..##..#..",
        ".###.##.###.",
        "..########..",
        "...##..##...",
        "####....####",
        "####....####",
        "...##..##...",
        "..########..",
        ".###.##.###.",
        "..#..##..#..",
        ".....##.....",
    )
    val WARNING = listOf(
        ".....##.....",
        "....####....",
        "....#..#....",
        "...##..##...",
        "...#.##.#...",
        "..##.##.##..",
        "..#..##..#..",
        ".##......##.",
        ".#...##...#.",
        "##...##...##",
        "############",
        "............",
    )
    val CHECK = listOf(
        "............",
        "..........##",
        ".........##.",
        "........##..",
        ".......##...",
        "##....##....",
        ".##..##.....",
        "..####......",
        "...##.......",
        "............",
        "............",
        "............",
    )
}
