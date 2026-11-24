package io.github.schnubbirunkel.boardshelf.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** Corner radius used for cards and sheets (One UI style: large and soft). */
val GlassShape = RoundedCornerShape(26.dp)

/** Frosted glass surface: translucent fill, soft top highlight and a light rim. */
fun Modifier.glass(shape: Shape = GlassShape, strength: Float = 1f): Modifier = this
    .clip(shape)
    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f * strength), Color.White.copy(alpha = 0.045f * strength))))
    .border(
        1.dp,
        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.34f), Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.14f))),
        shape
    )

/** Click with a quick spring "press" – the action fires immediately, the bounce is only visual. */
@Composable
fun Modifier.bouncyClick(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow), label = "press")
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

/** Drop-in replacement for ElevatedCard: a glass panel. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = GlassShape,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .then(if (onClick != null) Modifier.bouncyClick(onClick = onClick) else Modifier)
            .glass(shape),
        content = content
    )
}

/** Slowly drifting colour blobs – the "liquid" behind all glass. */
@Composable
fun LiquidBackground(modifier: Modifier = Modifier) {
    val colors = listOf(Deco.accent.copy(alpha = 0.20f), Color(0xFF5B7CFA).copy(alpha = 0.18f), Color(0xFFB06BF0).copy(alpha = 0.14f))
    val t = rememberInfiniteTransition(label = "liquid")
    val p by t.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(28000, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    Canvas(modifier.fillMaxSize().background(Deco.wall)) {
        val w = size.width
        val h = size.height
        val r = maxOf(w, h) * 0.6f
        val centers = listOf(
            Offset(w * (0.15f + 0.12f * cos(p)), h * (0.10f + 0.06f * sin(p * 2))),
            Offset(w * (0.92f + 0.10f * sin(p)), h * (0.45f + 0.10f * cos(p))),
            Offset(w * (0.30f + 0.15f * sin(p + 1.3f)), h * (0.95f + 0.05f * cos(p * 2))),
        )
        centers.forEachIndexed { i, c ->
            drawCircle(Brush.radialGradient(listOf(colors[i], Color.Transparent), center = c, radius = r), radius = r, center = c)
        }
        drawRect(Brush.verticalGradient(listOf(Deco.wall.copy(alpha = 0.2f), Deco.wall.copy(alpha = 0.5f), Deco.wall.copy(alpha = 0.3f))))
    }
}

/** Top bars float over the liquid background; scrolled content gets a dark glass tint. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun glassTopBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = Color.Transparent,
    scrolledContainerColor = Color(0xE6161A20),
)

fun glassBorder() = BorderStroke(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.04f))))

/** One UI style segmented tabs: a glass pill with a sliding highlight. */
@Composable
fun GlassTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier.fillMaxWidth().height(48.dp).glass(RoundedCornerShape(24.dp)).padding(4.dp)
    ) {
        val w = maxWidth / labels.size
        val x by androidx.compose.animation.core.animateDpAsState(w * selected, spring(dampingRatio = 0.75f, stiffness = 500f), label = "tabX")
        Box(
            Modifier.offset(x = x).width(w).fillMaxHeight().clip(RoundedCornerShape(20.dp))
                .background(Brush.verticalGradient(listOf(Deco.accent.copy(alpha = 0.32f), Deco.accent.copy(alpha = 0.18f))))
                .border(1.dp, Deco.accent.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
        )
        Row(Modifier.fillMaxSize()) {
            labels.forEachIndexed { i, label ->
                val sel = i == selected
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(20.dp)).clickable { onSelect(i) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label, maxLines = 1, style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (sel) Deco.accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    )
                }
            }
        }
    }
}
