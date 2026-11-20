package io.github.schnubbirunkel.boardshelf.ui.box3d

import android.graphics.RectF
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntSize
import io.github.schnubbirunkel.boardshelf.ui.detail.DETAIL_PITCH
import io.github.schnubbirunkel.boardshelf.ui.detail.DETAIL_YAW
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import io.github.schnubbirunkel.boardshelf.BoardshelfApp
import io.github.schnubbirunkel.boardshelf.data.Game
import kotlinx.coroutines.Job
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp

@Composable
fun rememberBoxTextures(game: Game, size: Int, full: Boolean): BoxTextures? {
    val cache = (LocalContext.current.applicationContext as BoardshelfApp).textures
    val initial = remember(game, size, full) { cache.peek(game, size, full) }
    val state = produceState(initial, game.id, game.coverPath, game.name, game.spineColor, game.year,
        game.minPlayers, game.maxPlayers, game.playingTime, game.minAge, game.designers,
        game.boxWidthCm, game.boxHeightCm, game.boxDepthCm, size, full) {
        value = cache.get(game, size, full)
    }
    return state.value
}

/** A box standing on the shelf: only its spine, straight and flat, no perspective. */
@Composable
fun ShelfBox(game: Game, @Suppress("UNUSED_PARAMETER") yaw: Float, @Suppress("UNUSED_PARAMETER") pitch: Float, modifier: Modifier, dim: Float = 1f) {
    val tex = rememberBoxTextures(game, 300, full = false)
    Canvas(modifier) {
        val spine = tex?.right
        if (spine != null) {
            drawImage(
                spine.asImageBitmap(),
                dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
                alpha = dim,
                filterQuality = FilterQuality.Medium,
            )
        } else {
            drawRect(Color(TextureCache.colorFromName(game.name)), alpha = dim)
        }
        // subtle edges so neighbouring spines stay distinguishable
        drawRect(Color.Black.copy(alpha = 0.35f), style = Stroke(width = 1.5f))
        drawRect(Color.White.copy(alpha = 0.12f), size = androidx.compose.ui.geometry.Size(size.width * 0.18f, size.height))
    }
}

/** Interactive viewer: drag to rotate (with inertia), double tap to reset. */
@Composable
fun Box3DViewer(game: Game, modifier: Modifier = Modifier, intro: Boolean = true) {
    val tex = rememberBoxTextures(game, 900, full = true)
    var yaw by remember { mutableFloatStateOf(if (intro) -70f else DETAIL_YAW) }
    var pitch by remember { mutableFloatStateOf(DETAIL_PITCH) }
    val scope = rememberCoroutineScope()
    val spinHolder = remember { arrayOfNulls<Job>(1) }

    LaunchedEffect(game.id) {
        if (!intro) return@LaunchedEffect
        // small intro turn
        val a = Animatable(-70f)
        a.animateTo(DETAIL_YAW, tween(900)) { yaw = value }
    }

    Canvas(
        modifier
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    spinHolder[0]?.cancel()
                    spinHolder[0] = scope.launch {
                        val startYaw = ((yaw % 360f) + 540f) % 360f - 180f
                        val startPitch = pitch
                        val a = Animatable(0f)
                        a.animateTo(1f, tween(500)) {
                            yaw = startYaw + (DETAIL_YAW - startYaw) * value
                            pitch = startPitch + (DETAIL_PITCH - startPitch) * value
                        }
                    }
                })
            }
            .pointerInput(Unit) {
                val tracker = VelocityTracker()
                detectDragGestures(
                    onDragStart = { spinHolder[0]?.cancel(); tracker.resetTracking() },
                    onDragEnd = {
                        val v = tracker.calculateVelocity()
                        var vel = v.x * 0.35f / 1000f // degrees per ms
                        spinHolder[0] = scope.launch {
                            var last = awaitFrame()
                            while (abs(vel) > 0.005f) {
                                val now = awaitFrame()
                                val dt = (now - last) / 1_000_000f
                                last = now
                                yaw += vel * dt
                                vel *= exp(-dt / 450f)
                            }
                        }
                    },
                ) { change, drag ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    yaw += drag.x * 0.35f
                    pitch = (pitch - drag.y * 0.3f).coerceIn(-85f, 85f)
                }
            }
    ) {
        drawIntoCanvas { c ->
            BoxRenderer.draw(
                c.nativeCanvas, tex, game.boxWidthCm, game.boxHeightCm, game.boxDepthCm,
                yaw, pitch, RectF(0f, 0f, size.width, size.height), BoxRenderer.Fit.SPHERE_CENTER,
                fallbackColor = TextureCache.colorFromName(game.name),
            )
        }
    }
}
