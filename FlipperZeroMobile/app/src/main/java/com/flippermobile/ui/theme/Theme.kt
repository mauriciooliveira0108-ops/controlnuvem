package com.flippermobile.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object FlipperColors {
    val Orange = Color(0xFFFF8200)
    val OrangeDim = Color(0xFF8A4600)
    val OrangeDark = Color(0xFF2A1700)
    val Black = Color(0xFF0D0D0D)
    val Surface = Color(0xFF161616)
    val SurfaceHigh = Color(0xFF1F1F1F)
    val White = Color(0xFFF5F5F5)
    val Gray = Color(0xFF9E9E9E)
    val GrayDark = Color(0xFF4A4A4A)
    val Green = Color(0xFF55E07A)
    val Red = Color(0xFFFF4D4D)
    val Yellow = Color(0xFFFFD34D)
    val Blue = Color(0xFF6EC1FF)
}

private val Mono = FontFamily.Monospace

private val FlipperTypography = Typography(
    displaySmall = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = 1.sp),
    headlineSmall = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 1.sp),
    titleLarge = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 18.sp),
    titleMedium = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 15.sp),
    titleSmall = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp),
    bodyLarge = TextStyle(fontFamily = Mono, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = Mono, fontSize = 13.sp),
    bodySmall = TextStyle(fontFamily = Mono, fontSize = 11.sp),
    labelLarge = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp),
    labelMedium = TextStyle(fontFamily = Mono, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = Mono, fontSize = 10.sp),
)

private val FlipperScheme = darkColorScheme(
    primary = FlipperColors.Orange,
    onPrimary = FlipperColors.Black,
    primaryContainer = FlipperColors.OrangeDark,
    onPrimaryContainer = FlipperColors.Orange,
    secondary = FlipperColors.White,
    onSecondary = FlipperColors.Black,
    background = FlipperColors.Black,
    onBackground = FlipperColors.White,
    surface = FlipperColors.Surface,
    onSurface = FlipperColors.White,
    surfaceVariant = FlipperColors.SurfaceHigh,
    onSurfaceVariant = FlipperColors.Gray,
    outline = FlipperColors.OrangeDim,
    error = FlipperColors.Red,
    onError = FlipperColors.Black,
)

val LocalScanlines = staticCompositionLocalOf { true }

@Composable
fun FlipperTheme(scanlines: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = FlipperScheme, typography = FlipperTypography) {
        CompositionLocalProvider(LocalScanlines provides scanlines) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(FlipperColors.Black)
                    .drawWithContent {
                        drawContent()
                        if (scanlines) {
                            // Linhas de varredura discretas, efeito de monitor retrô.
                            val step = 3.dp2px(density)
                            var y = 0f
                            while (y < size.height) {
                                drawLine(Color.Black.copy(alpha = 0.18f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                                y += step
                            }
                        }
                    }
            ) { content() }
        }
    }
}

private fun Int.dp2px(density: Float): Float = this * density
