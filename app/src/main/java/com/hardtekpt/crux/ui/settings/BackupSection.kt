package com.hardtekpt.crux.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hardtekpt.crux.data.backup.BackupFile
import com.hardtekpt.crux.data.backup.BackupFormatException
import com.hardtekpt.crux.data.backup.BackupRepository
import com.hardtekpt.crux.data.backup.BackupSection
import com.hardtekpt.crux.data.backup.ImportResult
import com.hardtekpt.crux.ui.components.CruxButton
import com.hardtekpt.crux.ui.components.CruxButtonVariant
import com.hardtekpt.crux.ui.components.CruxCard
import com.hardtekpt.crux.ui.theme.CruxTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BackupUiState(
    val exportSections: Set<BackupSection> = BackupSection.entries.filter { it.available }.toSet(),
    /** A file picked for import, waiting for the climber to choose what to bring in. */
    val pendingImport: BackupFile? = null,
    val importSections: Set<BackupSection> = emptySet(),
    val busy: Boolean = false,
    /** One line about the last export or import, or what went wrong. */
    val message: String? = null,
)

@HiltViewModel
class BackupViewModel @Inject constructor(private val backup: BackupRepository, private val contentResolver: ContentResolver, private val clock: Clock) :
    ViewModel() {
    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    val suggestedFileName: String get() = "crux-backup-${LocalDate.now(clock)}.json"

    fun toggleExport(section: BackupSection) = _state.update { it.copy(exportSections = it.exportSections.toggled(section)) }

    fun export(uri: Uri) = run("Backup saved.") {
        val text = backup.export(_state.value.exportSections)
        withContext(Dispatchers.IO) {
            contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
                ?: error("Could not open the file to write")
        }
        null
    }

    fun pickedImport(uri: Uri) = run(successMessage = null) {
        val text = withContext(Dispatchers.IO) {
            contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: error("Could not open the file")
        }
        val file = backup.parse(text)
        val present = BackupSection.entries.filter { it.available && (file.count(it) ?: 0) > 0 }.toSet()
        if (present.isEmpty()) throw BackupFormatException("This backup has nothing in it to import.")
        _state.update { it.copy(pendingImport = file, importSections = present) }
        null
    }

    fun toggleImport(section: BackupSection) = _state.update { it.copy(importSections = it.importSections.toggled(section)) }

    fun cancelImport() = _state.update { it.copy(pendingImport = null) }

    fun confirmImport() {
        val file = _state.value.pendingImport ?: return
        val sections = _state.value.importSections
        _state.update { it.copy(pendingImport = null) }
        run(successMessage = null) { describe(backup.import(file, sections)) }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    /** Runs a backup step, turning failures into a message the climber can act on. */
    private fun run(successMessage: String?, block: suspend () -> String?) {
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val message = try {
                block() ?: successMessage
            } catch (e: BackupFormatException) {
                e.message
            } catch (e: Exception) {
                "Something went wrong: ${e.message ?: "try again"}."
            }
            _state.update { it.copy(busy = false, message = message) }
        }
    }

    private fun describe(result: ImportResult): String {
        val added = result.added.entries.filter { it.value > 0 }
            .joinToString(", ") { "${it.value} ${it.key.label.lowercase()}" }
        val skipped = result.skipped.values.sum()
        return buildString {
            append(if (added.isEmpty()) "Nothing new to import" else "Imported $added")
            if (skipped > 0) append(". Skipped $skipped already here")
            append(".")
        }
    }
}

private fun Set<BackupSection>.toggled(section: BackupSection) = if (section in this) this - section else this + section

/** Settings card: choose what goes into a backup, export it to a file, or import one. */
@Composable
fun BackupCard(state: BackupUiState, viewModel: BackupViewModel) {
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::export)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::pickedImport)
    }
    CruxCard(modifier = Modifier.testTag("backup_card")) {
        Text("Backup", style = MaterialTheme.typography.titleMedium)
        Text(
            "Save your data to a file you keep, or bring a backup in. Importing only adds; nothing here is deleted.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = CruxTheme.space.s2),
        )
        BackupSection.entries.forEach { section ->
            SectionToggle(
                section = section,
                checked = section in state.exportSections,
                count = null,
                onToggle = { viewModel.toggleExport(section) },
                tag = "export_${section.name}",
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2),
            modifier = Modifier.padding(top = CruxTheme.space.s3),
        ) {
            CruxButton(
                text = "Export",
                onClick = { exportLauncher.launch(viewModel.suggestedFileName) },
                enabled = !state.busy && state.exportSections.isNotEmpty(),
                icon = Icons.Rounded.Upload,
                modifier = Modifier.testTag("export_backup"),
            )
            CruxButton(
                text = "Import",
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                enabled = !state.busy,
                variant = CruxButtonVariant.Outlined,
                icon = Icons.Rounded.Download,
                modifier = Modifier.testTag("import_backup"),
            )
        }
        state.message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(top = CruxTheme.space.s2)
                    .testTag("backup_message"),
            )
        }
    }

    state.pendingImport?.let { file ->
        AlertDialog(
            onDismissRequest = viewModel::cancelImport,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text("Import backup", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Column {
                    Text(
                        "Saved ${file.exportedAt.take(10)}. Choose what to bring in.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = CruxTheme.space.s2),
                    )
                    BackupSection.entries.forEach { section ->
                        val count = file.count(section) ?: 0
                        SectionToggle(
                            section = section,
                            checked = section in state.importSections,
                            count = count,
                            enabled = section.available && count > 0,
                            onToggle = { viewModel.toggleImport(section) },
                            tag = "import_${section.name}",
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmImport,
                    enabled = state.importSections.isNotEmpty(),
                    modifier = Modifier.testTag("confirm_import"),
                ) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelImport) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionToggle(section: BackupSection, checked: Boolean, count: Int?, onToggle: () -> Unit, tag: String, enabled: Boolean = section.available) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Column(Modifier.weight(1f)) {
            Text(
                section.label + (count?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            Text(
                section.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked && enabled,
            onCheckedChange = { onToggle() },
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.testTag(tag),
        )
    }
}
