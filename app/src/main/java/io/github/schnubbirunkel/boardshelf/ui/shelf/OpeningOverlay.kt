package io.github.schnubbirunkel.boardshelf.ui.shelf

import android.graphics.RectF
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.schnubbirunkel.boardshelf.data.Game
import io.github.schnubbirunkel.boardshelf.ui.box3d.BoxRenderer
import io.github.schnubbirunkel.boardshelf.ui.box3d.TextureCache
import io.github.schnubbirunkel.boardshelf.ui.box3d.rememberBoxTextures
import io.github.schnubbirunkel.boardshelf.ui.detail.DETAIL_HEADER_HEIGHT
import io.github.schnubbirunkel.boardshelf.ui.detail.DETAIL_PITCH
import io.github.schnubbirunkel.boardshelf.ui.detail.DETAIL_YAW
import io.github.schnubbirunkel.boardshelf.ui.theme.Deco
import kotlinx.coroutines.delay

/** A game being taken out of the shelf; [start] is the box' rectangle in window coordinates. */
data class Opening(val game: Game, val start: Rect)

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun phase(p: Float, from: Float, to: Float) = ((p - from) / (to - from)).coerceIn(0f, 1f)
private fun smooth(t: Float) = t * t * (3f - 2f * t)

/**
 * Pulls the box out of the shelf towards the viewer, turns it so the cover shows and flies it
 * to exactly the place where the 3D viewer of the detail screen draws it, then navigates.
 */
@Composable
fun OpeningOverlay(o: Opening, onFinished: () -> Unit, onReset: () -> Unit) {
    val density = LocalDensity.current
    val statusBar = WindowInsets.statusBars.getTop(density).toFloat()
    val tex = rememberBoxTextures(o.game, 900, full = true)
    val progress = remember(o) { Animatable(0f) }
    val finished by rememberUpdatedState(onFinished)
    val reset by rememberUpdatedState(onReset)
    val origin = remember { FloatArray(2) }

    LaunchedEffect(o) {
        progress.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
        finished()
        delay(600)
        reset()
    }

    val g = o.game
    Canvas(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { c -> c.positionInWindow().let { origin[0] = it.x; origin[1] = it.y } }
    ) {
        val p = progress.value
        val pull = smooth(phase(p, 0f, 0.3f))   // slide out towards the viewer
        val fly = smooth(phase(p, 0.22f, 1f))  // turn and fly to the detail header

        // background fades to the detail screen's header
        val headerH = DETAIL_HEADER_HEIGHT.dp.toPx()
        drawRect(Deco.wall.copy(alpha = 0.92f * phase(p, 0.1f, 0.85f)))
        drawRect(
            Brush.radialGradient(
                listOf(Color(0xFF34404D), Deco.wall), center = Offset(size.width / 2f, headerH / 2f), radius = 900f
            ),
            size = androidx.compose.ui.geometry.Size(size.width, headerH),
            alpha = phase(p, 0.35f, 1f)
        )

        val w = g.boxWidthCm; val h = g.boxHeightCm; val d = g.boxDepthCm
        val start = o.start.translate(Offset(-origin[0], -origin[1]))
        val s0 = BoxRenderer.placement(
            w, h, d, SPINE_YAW, SHELF_PITCH,
            RectF(start.left, start.top, start.right, start.bottom), BoxRenderer.Fit.BOUNDS_BOTTOM
        )
        val pad = 24.dp.toPx()
        val target = RectF(pad, statusBar + 64.dp.toPx() - origin[1], size.width - pad, headerH - 8.dp.toPx() - origin[1])
        val s1 = BoxRenderer.placement(w, h, d, DETAIL_YAW, DETAIL_PITCH, target, BoxRenderer.Fit.SPHERE_CENTER)

        // while pulling out: grow a little and lift
        val pullScale = 1f + 0.18f * pull * (1f - fly)
        val lift = -10.dp.toPx() * pull * (1f - fly)
        val scale = lerp(s0[0], s1[0], fly) * pullScale
        val ox = lerp(s0[1], s1[1], fly)
        val oy = lerp(s0[2] + lift, s1[2], fly) - (if (fly < 1f) 40.dp.toPx() * kotlin.math.sin(fly * Math.PI).toFloat() else 0f)
        val yaw = lerp(SPINE_YAW, DETAIL_YAW, fly) + 6f * pull * (1f - fly)
        val pitch = lerp(SHELF_PITCH, DETAIL_PITCH, fly)

        drawIntoCanvas { c ->
            BoxRenderer.draw(
                c.nativeCanvas, tex, w, h, d, yaw, pitch,
                RectF(0f, 0f, size.width, size.height), BoxRenderer.Fit.EXPLICIT,
                fallbackColor = TextureCache.colorFromName(g.name),
                shadow = p > 0.05f,
                explicit = floatArrayOf(scale, ox, oy),
            )
        }
    }
}
