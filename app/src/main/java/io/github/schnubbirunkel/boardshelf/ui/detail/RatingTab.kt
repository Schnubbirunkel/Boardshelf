package io.github.schnubbirunkel.boardshelf.ui.detail

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.schnubbirunkel.boardshelf.R
import io.github.schnubbirunkel.boardshelf.data.Game
import io.github.schnubbirunkel.boardshelf.ui.theme.Wood
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun RatingTab(g: Game, vm: GameDetailViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.rating_overall), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (g.myRating > 0) String.format(Locale.getDefault(), "%.1f / 10", g.myRating) else stringResource(R.string.rating_none),
                    style = MaterialTheme.typography.headlineSmall, color = Wood.brass
                )
                // 5 stars, each half star = 1 point -> 0..10
                StarBar(value = g.myRating / 2f, stars = 5, size = 44.dp, half = true) { v ->
                    vm.update { it.copy(myRating = v * 2f) }
                }
                if (g.myRating > 0) TextButton(onClick = { vm.update { it.copy(myRating = 0f) } }) { Text(stringResource(R.string.rating_clear)) }
                g.bggRating?.let {
                    Text(stringResource(R.string.rating_bgg_compare, String.format(Locale.getDefault(), "%.1f", it)), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AspectRow(R.string.rating_fun, g.ratingFun) { v -> vm.update { it.copy(ratingFun = v) } }
                AspectRow(R.string.rating_replay, g.ratingReplay) { v -> vm.update { it.copy(ratingReplay = v) } }
                AspectRow(R.string.rating_looks, g.ratingLooks) { v -> vm.update { it.copy(ratingLooks = v) } }
                AspectRow(R.string.rating_rules, g.ratingRules) { v -> vm.update { it.copy(ratingRules = v) } }
            }
        }
        var review by remember(g.id) { mutableStateOf(g.review ?: "") }
        LaunchedEffect(review) {
            if (review != (g.review ?: "")) {
                delay(700); vm.update { it.copy(review = review.ifBlank { null }) }
            }
        }
        OutlinedTextField(
            value = review, onValueChange = { review = it },
            label = { Text(stringResource(R.string.rating_review)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(), minLines = 4
        )
    }
}

@Composable
private fun AspectRow(label: Int, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        StarBar(value = value.toFloat(), stars = 5, size = 28.dp, half = false) { v ->
            onChange(if (v.toInt() == value) 0 else v.toInt())
        }
    }
}

/** Tappable star bar. With [half] the left half of a star sets x.5. */
@Composable
fun StarBar(value: Float, stars: Int, size: Dp, half: Boolean, onChange: (Float) -> Unit) {
    val callback by rememberUpdatedState(onChange)
    Row {
        for (i in 1..stars) {
            val icon = when {
                value >= i -> Icons.Default.Star
                half && value >= i - 0.5f -> Icons.AutoMirrored.Filled.StarHalf
                else -> Icons.Default.StarOutline
            }
            Box(
                Modifier
                    .size(size)
                    .pointerInput(i, half) {
                        detectTapGestures { p ->
                            val v = if (half && p.x < this.size.width / 2) i - 0.5f else i.toFloat()
                            callback(v)
                        }
                    }
            ) {
                Icon(icon, null, tint = Wood.brass, modifier = Modifier.size(size))
            }
        }
    }
}
