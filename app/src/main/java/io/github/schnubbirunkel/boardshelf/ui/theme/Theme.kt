package io.github.schnubbirunkel.boardshelf.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Deco {
    val wall = Color(0xFF14181D)
    val wallLight = Color(0xFF1C2127)
    val back = Color(0xFF15181C)
    val plank = Color(0xFF2B3036)
    val plankLight = Color(0xFF3D434B)
    val plankDark = Color(0xFF1B1F23)
    val grain = Color(0x14FFFFFF)
    val brass = Color(0xFFFFC845)
    val accent = Color(0xFF5ED3B8)
    val label = Color(0xFF454D57)
}

private val scheme = darkColorScheme(
    primary = Color(0xFF5ED3B8),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF0E5143),
    onPrimaryContainer = Color(0xFFA8F2DD),
    secondary = Color(0xFF9DB4CC),
    onSecondary = Color(0xFF1A2836),
    secondaryContainer = Color(0xFF2F3E4E),
    onSecondaryContainer = Color(0xFFD3E4F7),
    tertiary = Color(0xFFFFC845),
    background = Deco.wall,
    onBackground = Color(0xFFE6E9ED),
    surface = Color(0xFF1A1F25),
    onSurface = Color(0xFFE6E9ED),
    surfaceVariant = Color(0xFF2C333B),
    onSurfaceVariant = Color(0xFFB7C0CA),
    surfaceContainer = Color(0xFF20262D),
    surfaceContainerHigh = Color(0xFF272E36),
    surfaceContainerHighest = Color(0xFF303841),
    surfaceContainerLow = Color(0xFF1C2228),
    outline = Color(0xFF7D8894),
)
private val base = Typography()
private val typography = base.copy(
    headlineSmall = base.headlineSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold),
)

val ShelfLabelStyle = TextStyle(fontFamily = FontFamily.Serif, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)

@Composable
fun BoardshelfTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
