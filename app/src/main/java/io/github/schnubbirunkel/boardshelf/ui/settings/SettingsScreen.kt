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
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.horizontalScroll
import io.github.schnubbirunkel.boardshelf.net.AiProvider
import io.github.schnubbirunkel.boardshelf.ui.theme.GlassCard
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
fun SettingsScreen(onBack: () -> Unit, onBggImport: () -> Unit = {}) {
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
    var aiTest by remember { mutableStateOf<String?>(null) }
    var aiTesting by remember { mutableStateOf(false) }

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
                colors = io.github.schnubbirunkel.boardshelf.ui.theme.glassTopBarColors(),
                title = { Text(stringResource(R.string.menu_settings)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Section(stringResource(R.string.import_title)) {
                Text(stringResource(R.string.import_explain_short), style = MaterialTheme.typography.bodySmall)
                Button(onClick = onBggImport) { Text(stringResource(R.string.import_open)) }
            }

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

            Section(stringResource(R.string.settings_ai)) {
                Text(stringResource(R.string.settings_ai_explain), style = MaterialTheme.typography.bodySmall)
                val providers = listOf(
                    AiProvider.NONE to stringResource(R.string.settings_ai_off),
                    AiProvider.CLAUDE to "Claude",
                    AiProvider.GEMINI to "Gemini",
                    AiProvider.OPENAI to "ChatGPT",
                    AiProvider.CUSTOM to stringResource(R.string.settings_ai_custom),
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    providers.forEach { (p, label) ->
                        FilterChip(
                            selected = settings.aiProvider == p,
                            onClick = { repo.settings.update { it.copy(aiProvider = p, aiModel = "") }; aiTest = null },
                            label = { Text(label) }
                        )
                    }
                }
                if (settings.aiProvider != AiProvider.NONE) {
                    if (settings.aiProvider == AiProvider.CUSTOM) {
                        OutlinedTextField(
                            settings.aiBaseUrl, { v -> repo.settings.update { it.copy(aiBaseUrl = v) } },
                            label = { Text(stringResource(R.string.settings_ai_url)) }, singleLine = true,
                            placeholder = { Text("http://192.168.1.10:11434/v1") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    OutlinedTextField(
                        settings.aiKey, { v -> repo.settings.update { it.copy(aiKey = v) } },
                        label = { Text(stringResource(if (settings.aiProvider == AiProvider.CUSTOM) R.string.settings_ai_key_optional else R.string.settings_ai_key)) },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        settings.aiModel, { v -> repo.settings.update { it.copy(aiModel = v) } },
                        label = { Text(stringResource(R.string.settings_ai_model)) }, singleLine = true,
                        placeholder = { Text(settings.aiProvider.defaultModel) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    val keyUrl = when (settings.aiProvider) {
                        AiProvider.CLAUDE -> "https://console.anthropic.com/settings/keys"
                        AiProvider.GEMINI -> "https://aistudio.google.com/apikey"
                        AiProvider.OPENAI -> "https://platform.openai.com/api-keys"
                        else -> "https://ollama.com/search?c=vision"
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(enabled = repo.ai.enabled && !aiTesting, onClick = {
                            aiTesting = true
                            scope.launch {
                                aiTest = try {
                                    context.getString(R.string.settings_ai_test_ok, repo.ai.test())
                                } catch (e: Exception) {
                                    context.errorText(e)
                                } finally {
                                    aiTesting = false
                                }
                            }
                        }) { Text(stringResource(R.string.settings_ai_test)) }
                        TextButton(onClick = { context.openUrl(keyUrl) }) {
                            Text(stringResource(if (settings.aiProvider == AiProvider.CUSTOM) R.string.settings_ai_models else R.string.settings_ai_get_key))
                        }
                    }
                    if (aiTesting) LinearProgressIndicator(Modifier.fillMaxWidth())
                    aiTest?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    val log by repo.aiLog.collectAsStateWithLifecycle()
                    if (log.isNotEmpty()) {
                        Text(stringResource(R.string.settings_ai_log), style = MaterialTheme.typography.titleSmall)
                        Text(log.take(25).joinToString("\n"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Section(stringResource(R.string.settings_sources)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_wikidata))
                        Text(stringResource(R.string.settings_wikidata_explain), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(settings.useWikidata, { v -> repo.settings.update { it.copy(useWikidata = v) } })
                }
                Text(stringResource(R.string.settings_gameupc_explain), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    settings.gameUpcKey, { v -> repo.settings.update { it.copy(gameUpcKey = v) } },
                    label = { Text(stringResource(R.string.settings_gameupc_key)) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = { context.openUrl("https://gameupc.com") }) { Text("gameupc.com") }
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
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
