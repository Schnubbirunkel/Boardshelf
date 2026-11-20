package io.github.schnubbirunkel.ludothek.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.schnubbirunkel.ludothek.R
import io.github.schnubbirunkel.ludothek.data.Game

@Composable
fun EditGameDialog(g: Game, vm: GameDetailViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(g.name) }
    var year by remember { mutableStateOf(g.year?.toString() ?: "") }
    var minP by remember { mutableStateOf(g.minPlayers?.toString() ?: "") }
    var maxP by remember { mutableStateOf(g.maxPlayers?.toString() ?: "") }
    var time by remember { mutableStateOf(g.playingTime?.toString() ?: "") }
    var age by remember { mutableStateOf(g.minAge?.toString() ?: "") }
    var bw by remember { mutableStateOf(fmt(g.boxWidthCm)) }
    var bh by remember { mutableStateOf(fmt(g.boxHeightCm)) }
    var bd by remember { mutableStateOf(fmt(g.boxDepthCm)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.field_name)) }, singleLine = true)
                NumberField(year, { year = it }, R.string.field_year)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(minP, { minP = it }, R.string.field_min_players, Modifier.weight(1f))
                    NumberField(maxP, { maxP = it }, R.string.field_max_players, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(time, { time = it }, R.string.field_time, Modifier.weight(1f))
                    NumberField(age, { age = it }, R.string.field_age, Modifier.weight(1f))
                }
                Text(stringResource(R.string.field_box_size), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(bw, { bw = it }, R.string.field_width, Modifier.weight(1f), decimal = true)
                    NumberField(bh, { bh = it }, R.string.field_height, Modifier.weight(1f), decimal = true)
                    NumberField(bd, { bd = it }, R.string.field_depth, Modifier.weight(1f), decimal = true)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                fun f(s: String, old: Float) = s.replace(',', '.').toFloatOrNull()?.coerceIn(1f, 80f) ?: old
                vm.update {
                    it.copy(
                        name = name.ifBlank { it.name },
                        year = year.toIntOrNull(),
                        minPlayers = minP.toIntOrNull(),
                        maxPlayers = maxP.toIntOrNull(),
                        playingTime = time.toIntOrNull(),
                        minAge = age.toIntOrNull(),
                        boxWidthCm = f(bw, it.boxWidthCm),
                        boxHeightCm = f(bh, it.boxHeightCm),
                        boxDepthCm = f(bd, it.boxDepthCm),
                    )
                }
                onDismiss()
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private fun fmt(f: Float) = if (f % 1f == 0f) f.toInt().toString() else String.format(java.util.Locale.ROOT, "%.1f", f)

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: Int, modifier: Modifier = Modifier, decimal: Boolean = false) {
    OutlinedTextField(
        value, onChange, label = { Text(stringResource(label)) }, singleLine = true, modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number)
    )
}
