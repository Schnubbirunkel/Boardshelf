package io.github.schnubbirunkel.boardshelf.ui.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.schnubbirunkel.boardshelf.R
import io.github.schnubbirunkel.boardshelf.data.Game
import io.github.schnubbirunkel.boardshelf.data.playersText
import io.github.schnubbirunkel.boardshelf.net.BggApi
import io.github.schnubbirunkel.boardshelf.ui.openUrl
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InfoTab(g: Game, vm: GameDetailViewModel, onSearchCover: () -> Unit, onCoverPhoto: () -> Unit) {
    val context = LocalContext.current
    val shelves by vm.shelves.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (g.coverPath == null) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.cover_missing), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onSearchCover) { Text(stringResource(R.string.action_cover_web)) }
                        OutlinedButton(onClick = onCoverPhoto) { Text(stringResource(R.string.action_cover_photo)) }
                    }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            g.playersText?.let { InfoChip(Icons.Default.Groups, stringResource(R.string.box_players, it)) }
            g.playingTime?.let { InfoChip(Icons.Default.Schedule, stringResource(R.string.box_minutes, it)) }
            g.minAge?.let { InfoChip(Icons.Default.Cake, stringResource(R.string.box_age, it)) }
            g.year?.let { InfoChip(Icons.Default.CalendarMonth, it.toString()) }
            g.bggWeight?.let { InfoChip(Icons.Default.FitnessCenter, stringResource(R.string.info_weight, String.format(Locale.getDefault(), "%.1f", it))) }
            g.bggRating?.let { InfoChip(Icons.Default.Star, "BGG " + String.format(Locale.getDefault(), "%.1f", it)) }
            g.bggRank?.let { InfoChip(Icons.Default.EmojiEvents, stringResource(R.string.info_rank, it)) }
            InfoChip(
                Icons.Default.Inventory2,
                String.format(Locale.getDefault(), "%.0f × %.0f × %.0f cm", g.boxWidthCm, g.boxHeightCm, g.boxDepthCm)
            )
        }

        // shelf selector
        var shelfMenu by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.info_on_shelf), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { shelfMenu = true }) {
                Text(shelves.firstOrNull { it.id == g.shelfId }?.name ?: "—")
            }
            DropdownMenu(expanded = shelfMenu, onDismissRequest = { shelfMenu = false }) {
                shelves.forEach { s ->
                    DropdownMenuItem(text = { Text(s.name) }, onClick = { shelfMenu = false; vm.moveToShelf(s.id) })
                }
            }
        }

        LabeledValue(R.string.info_designers, g.designers)
        LabeledValue(R.string.info_publishers, g.publishers)
        LabeledValue(R.string.info_categories, g.categories)
        LabeledValue(R.string.info_mechanics, g.mechanics)

        g.description?.let { desc ->
            var expanded by remember { mutableStateOf(false) }
            ElevatedCard(Modifier.fillMaxWidth().animateContentSize().clickable { expanded = !expanded }) {
                Column(Modifier.padding(14.dp)) {
                    Text(stringResource(R.string.info_description), style = MaterialTheme.typography.titleSmall)
                    Text(
                        desc, style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (expanded) Int.MAX_VALUE else 6, overflow = TextOverflow.Ellipsis
                    )
                    if (!expanded) Text(stringResource(R.string.info_more), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        if (g.wikiExtract != null && g.wikiExtract != g.description) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Wikipedia", style = MaterialTheme.typography.titleSmall)
                    Text(g.wikiExtract, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // personal notes (auto-saved)
        var notes by remember(g.id) { mutableStateOf(g.notes ?: "") }
        LaunchedEffect(notes) {
            if (notes != (g.notes ?: "")) {
                delay(700); vm.update { it.copy(notes = notes.ifBlank { null }) }
            }
        }
        OutlinedTextField(
            value = notes, onValueChange = { notes = it },
            label = { Text(stringResource(R.string.info_notes)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(), minLines = 3
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            g.bggId?.let { id ->
                OutlinedButton(onClick = { context.openUrl(BggApi.gameUrl(id)) }) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp)); Text(" BoardGameGeek")
                }
            }
            g.wikiUrl?.let { url ->
                OutlinedButton(onClick = { context.openUrl(url) }) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp)); Text(" Wikipedia")
                }
            }
        }
        if (g.bggId != null) {
            Text(
                stringResource(R.string.powered_by_bgg), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { context.openUrl("https://boardgamegeek.com") }
            )
        }
    }
}

@Composable
private fun InfoChip(icon: ImageVector, text: String) {
    AssistChip(onClick = {}, label = { Text(text) }, leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) })
}

@Composable
private fun LabeledValue(label: Int, value: String?) {
    if (value.isNullOrBlank()) return
    Row {
        Text(stringResource(label), fontWeight = FontWeight.SemiBold, modifier = Modifier.width(110.dp), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
