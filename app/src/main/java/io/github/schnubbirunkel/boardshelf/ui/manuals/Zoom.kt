package io.github.schnubbirunkel.boardshelf.ui.manuals

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged

@Stable
class ZoomState {
    var scale by mutableFloatStateOf(1f)
    var offsetX by mutableFloatStateOf(0f)
    var offsetY by mutableFloatStateOf(0f)

    fun clamp(width: Float, height: Float, allowY: Boolean) {
        scale = scale.coerceIn(1f, 5f)
        val maxX = (scale - 1f) * width / 2f
        offsetX = offsetX.coerceIn(-maxX, maxX)
        if (allowY) {
            val maxY = (scale - 1f) * height / 2f
            offsetY = offsetY.coerceIn(-maxY, maxY)
        } else offsetY = 0f
    }
}

/**
 * Pinch-zoom for a scrolling list: two fingers zoom/pan, one finger pans horizontally when
 * zoomed and scrolls the list vertically (via [list]). Double tap toggles 2.5x.
 */
fun Modifier.zoomable(state: ZoomState, list: LazyListState? = null): Modifier = this
    .pointerInput(state) {
        detectTapGestures(onDoubleTap = {
            if (state.scale > 1.05f) { state.scale = 1f; state.offsetX = 0f; state.offsetY = 0f } else state.scale = 2.5f
            state.clamp(size.width.toFloat(), size.height.toFloat(), list == null)
        })
    }
    .pointerInput(state, list) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
                val event = awaitPointerEvent()
                val pressed = event.changes.count { it.pressed }
                if (pressed >= 2) {
                    state.scale *= event.calculateZoom()
                    val pan = event.calculatePan()
                    state.offsetX += pan.x
                    if (list == null) state.offsetY += pan.y
                    state.clamp(size.width.toFloat(), size.height.toFloat(), list == null)
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                } else if (pressed == 1 && state.scale > 1.01f) {
                    val pan = event.calculatePan()
                    state.offsetX += pan.x
                    if (list == null) state.offsetY += pan.y else list.dispatchRawDelta(-pan.y / state.scale)
                    state.clamp(size.width.toFloat(), size.height.toFloat(), list == null)
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }
    .graphicsLayer {
        scaleX = state.scale; scaleY = state.scale
        translationX = state.offsetX; translationY = state.offsetY
        transformOrigin = if (list != null) TransformOrigin(0.5f, 0f) else TransformOrigin.Center
    }
