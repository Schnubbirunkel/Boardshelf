package io.github.schnubbirunkel.boardshelf.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.schnubbirunkel.boardshelf.BuildConfig
import io.github.schnubbirunkel.boardshelf.R
import io.github.schnubbirunkel.boardshelf.demo.DemoData
import io.github.schnubbirunkel.boardshelf.net.BggApi
import io.github.schnubbirunkel.boardshelf.ui.app
import io.github.schnubbirunkel.boardshelf.ui.errorText
import io.github.schnubbirunkel.boardshelf.ui.openUrl
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = context.app().repository
    val settings by repo.settings.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf(settings.bggToken) }
    var username by remember { mutableStateOf(settings.bggUsername) }
    var importStatus by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    var backupStatus by remember { mutableStateOf<String?>(null) }
    var restartNeeded by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            backupStatus = try {
                repo.exportBackup(uri); context.getString(R.string.backup_done)
            } catch (e: Exception) {
                context.errorText(e)
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                repo.importBackup(uri); restartNeeded = true
            } catch (e: Exception) {
                backupStatus = context.errorText(e)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_settings)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Section(stringResource(R.string.settings_bgg)) {
                Text(stringResource(R.string.settings_bgg_explain), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { context.openUrl(BggApi.APPLICATIONS_URL) }) { Text(stringResource(R.string.settings_bgg_get_token)) }
                OutlinedTextField(
                    token, { token = it; repo.settings.update { s -> s.copy(bggToken = it) } },
                    label = { Text(stringResource(R.string.settings_bgg_token)) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    username, { username = it; repo.settings.update { s -> s.copy(bggUsername = it) } },
                    label = { Text(stringResource(R.string.settings_bgg_user)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    enabled = !importing && username.isNotBlank() && token.isNotBlank(),
                    onClick = {
                        importing = true
                        scope.launch {
                            importStatus = try {
                                val r = repo.importBggCollection(username.trim()) { done, total ->
                                    importStatus = context.getString(R.string.settings_import_progress, done, total)
                                }
                                context.getString(R.string.settings_import_done, r.added, r.skipped)
                            } catch (e: Exception) {
                                context.errorText(e)
                            } finally {
                                importing = false
                            }
                        }
                    }
                ) { Text(stringResource(R.string.settings_import_collection)) }
                if (importing) LinearProgressIndicator(Modifier.fillMaxWidth())
                importStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text(
                    stringResource(R.string.powered_by_bgg), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Section(stringResource(R.string.settings_sources)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_wikidata))
                        Text(stringResource(R.string.settings_wikidata_explain), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(settings.useWikidata, { v -> repo.settings.update { it.copy(useWikidata = v) } })
                }
            }

            Section(stringResource(R.string.settings_language)) {
                val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
                listOf("" to R.string.settings_language_system, "de" to R.string.settings_language_de, "en" to R.string.settings_language_en)
                    .forEach { (tag, label) ->
                        val selected = if (tag.isEmpty()) current.isEmpty() else current.startsWith(tag)
                        Row(
                            Modifier.fillMaxWidth().selectable(selected) {
                                AppCompatDelegate.setApplicationLocales(
                                    if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
                                )
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected, null)
                            Text(stringResource(label), Modifier.padding(start = 8.dp))
                        }
                    }
            }

            Section(stringResource(R.string.settings_backup)) {
                Text(stringResource(R.string.settings_backup_explain), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { exportLauncher.launch("boardshelf-backup.zip") }) { Text(stringResource(R.string.settings_backup_export)) }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }) {
                        Text(stringResource(R.string.settings_backup_import))
                    }
                }
                backupStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }

            Section(stringResource(R.string.settings_demo)) {
                Text(stringResource(R.string.settings_demo_explain), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { scope.launch { DemoData.seed(context, repo) } }) { Text(stringResource(R.string.settings_demo_load)) }
            }

            Section(stringResource(R.string.settings_about)) {
                Text("Boardshelf ${BuildConfig.VERSION_NAME}")
                TextButton(onClick = { context.openUrl("https://github.com/Schnubbirunkel/Boardshelf") }) { Text("GitHub") }
            }
        }
    }

    if (restartNeeded) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.backup_restored)) },
            text = { Text(stringResource(R.string.backup_restart)) },
            confirmButton = {
                TextButton(onClick = {
                    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    context.startActivity(intent)
                    exitProcess(0)
                }) { Text(stringResource(R.string.action_restart)) }
            }
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
