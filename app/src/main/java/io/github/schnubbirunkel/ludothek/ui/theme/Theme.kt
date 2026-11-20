package io.github.schnubbirunkel.ludothek.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Wood {
    val wall = Color(0xFF2B1E16)
    val wallLight = Color(0xFF3A2A1F)
    val back = Color(0xFF241810)
    val plank = Color(0xFFB98552)
    val plankLight = Color(0xFFD9A066)
    val plankDark = Color(0xFF7A5232)
    val grain = Color(0x22000000)
    val brass = Color(0xFFE0B04A)
}

private val scheme = darkColorScheme(
    primary = Color(0xFFE0B04A),
    onPrimary = Color(0xFF2B1E00),
    primaryContainer = Color(0xFF5C4310),
    onPrimaryContainer = Color(0xFFFFDEA0),
    secondary = Color(0xFFD9A066),
    onSecondary = Color(0xFF3A2410),
    secondaryContainer = Color(0xFF55391F),
    onSecondaryContainer = Color(0xFFFFDCC0),
    tertiary = Color(0xFF8FC9A4),
    background = Wood.wall,
    onBackground = Color(0xFFF1E4D8),
    surface = Color(0xFF2F2219),
    onSurface = Color(0xFFF1E4D8),
    surfaceVariant = Color(0xFF4A3628),
    onSurfaceVariant = Color(0xFFD8C2B0),
    surfaceContainer = Color(0xFF382A20),
    surfaceContainerHigh = Color(0xFF433227),
    surfaceContainerHighest = Color(0xFF4E3B2E),
    surfaceContainerLow = Color(0xFF33261C),
    outline = Color(0xFF9E8A79),
)

private val base = Typography()
private val typography = base.copy(
    headlineSmall = base.headlineSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold),
)

val ShelfLabelStyle = TextStyle(fontFamily = FontFamily.Serif, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)

@Composable
fun LudothekTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
