package com.hardtekpt.crux.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hardtekpt.crux.data.backup.BackupFile
import com.hardtekpt.crux.data.backup.BackupFormatException
import com.hardtekpt.crux.data.backup.BackupMedia
import com.hardtekpt.crux.data.backup.BackupRepository
import com.hardtekpt.crux.data.backup.BackupSection
import com.hardtekpt.crux.data.backup.ConflictKey
import com.hardtekpt.crux.data.backup.ImportConflict
import com.hardtekpt.crux.data.backup.ImportResult
import com.hardtekpt.crux.data.backup.OpenedBackup
import com.hardtekpt.crux.data.backup.Resolution
import com.hardtekpt.crux.data.prefs.UserPreferencesRepository
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BackupUiState(
    val exportSections: Set<BackupSection> = BackupSection.entries.toSet(),
    /** Whether photos and videos go into exports and come out of imports. */
    val media: BackupMedia = BackupMedia(),
    /** A file picked for import, waiting for the climber to choose what to bring in. */
    val pendingImport: BackupFile? = null,
    val importSections: Set<BackupSection> = emptySet(),
    /** Records in the backup that are already here, and what the climber chose for each so far. */
    val conflicts: List<ImportConflict> = emptyList(),
    val decisions: Map<ConflictKey, Resolution> = emptyMap(),
    val busy: Boolean = false,
    /** One line about the last export or import, or what went wrong. */
    val message: String? = null,
) {
    /** The duplicate waiting for an answer. */
    val conflict: ImportConflict? get() = conflicts.firstOrNull { it.id !in decisions }

    /** How many others in the same section still wait for an answer. */
    val othersInSection: Int
        get() = conflict?.let { current -> conflicts.count { it.section == current.section && it.id !in decisions } - 1 } ?: 0
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backup: BackupRepository,
    private val preferences: UserPreferencesRepository,
    private val contentResolver: ContentResolver,
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    /** The picked backup, open until it is imported or the climber cancels. */
    private var opened: OpenedBackup? = null

    init {
        viewModelScope.launch {
            combine(preferences.backupPhotos, preferences.backupVideos, ::BackupMedia).collect { media -> _state.update { it.copy(media = media) } }
        }
    }

    val suggestedFileName: String get() = "crux-backup-${LocalDate.now(clock)}.zip"

    fun toggleExport(section: BackupSection) = _state.update { it.copy(exportSections = it.exportSections.toggled(section)) }

    fun setPhotos(enabled: Boolean) {
        viewModelScope.launch { preferences.setBackupPhotos(enabled) }
    }

    fun setVideos(enabled: Boolean) {
        viewModelScope.launch { preferences.setBackupVideos(enabled) }
    }

    fun export(uri: Uri) = run("Backup saved.") {
        withContext(Dispatchers.IO) {
            contentResolver.openOutputStream(uri, "wt")?.use { backup.export(_state.value.exportSections, it, _state.value.media) }
                ?: error("Could not open the file to write")
        }
        null
    }

    fun pickedImport(uri: Uri) = run(successMessage = null) {
        closeOpened()
        val picked = withContext(Dispatchers.IO) {
            contentResolver.openInputStream(uri)?.use { backup.open(it, _state.value.media) } ?: error("Could not open the file")
        }
        val present = BackupSection.entries.filter { (picked.file.count(it) ?: 0) > 0 }.toSet()
        if (present.isEmpty()) {
            picked.close()
            throw BackupFormatException("This backup has nothing in it to import.")
        }
        opened = picked
        // Settings replace the climber's own, so they come in only when asked for.
        _state.update { it.copy(pendingImport = picked.file, importSections = present - BackupSection.SETTINGS) }
        null
    }

    fun toggleImport(section: BackupSection) = _state.update { it.copy(importSections = it.importSections.toggled(section)) }

    fun cancelImport() {
        closeOpened()
        _state.update { it.copy(pendingImport = null, conflicts = emptyList(), decisions = emptyMap()) }
    }

    /** Looks for records already here first; the climber decides each before anything is written. */
    fun confirmImport() {
        val picked = opened ?: return
        val sections = _state.value.importSections
        _state.update { it.copy(pendingImport = null) }
        run(successMessage = null) {
            val conflicts = backup.conflicts(picked, sections)
            if (conflicts.isEmpty()) {
                finishImport(emptyMap())
            } else {
                _state.update { it.copy(conflicts = conflicts, decisions = emptyMap()) }
                null
            }
        }
    }

    /** The climber's answer for the duplicate shown, and for the others in its section when [forOthers]. */
    fun resolve(resolution: Resolution, forOthers: Boolean) {
        val current = _state.value.conflict ?: return
        val state = _state.updateAndGet { s ->
            val answered = if (forOthers) s.conflicts.filter { it.section == current.section && it.id !in s.decisions } else listOf(current)
            s.copy(decisions = s.decisions + answered.associate { it.id to resolution })
        }
        if (state.conflict == null) run(successMessage = null) { finishImport(state.decisions) }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    override fun onCleared() = closeOpened()

    private suspend fun finishImport(decisions: Map<ConflictKey, Resolution>): String {
        val picked = opened ?: error("The backup is no longer open")
        try {
            return describe(backup.import(picked, _state.value.importSections, decisions, _state.value.media))
        } finally {
            closeOpened()
            _state.update { it.copy(conflicts = emptyList(), decisions = emptyMap()) }
        }
    }

    private fun closeOpened() {
        opened?.close()
        opened = null
    }

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
        val added = result.added.filterValues { it > 0 }.entries.sortedBy { it.key.ordinal }
            .map { (section, count) -> if (section == BackupSection.SETTINGS) "your settings" else "$count ${section.noun}" }
        val others = listOfNotNull(
            result.replaced.values.sum().takeIf { it > 0 }?.let { "replaced $it" },
            result.copied.values.sum().takeIf { it > 0 }?.let { "kept $it as copies" },
            result.skipped.values.sum().takeIf { it > 0 }?.let { "skipped $it already here" },
        )
        return buildString {
            append(if (added.isEmpty()) "Nothing new imported." else "Imported ${added.joinToString(", ")}.")
            if (others.isNotEmpty()) append(" ").append(others.joinToString(", ").replaceFirstChar { it.uppercase() }).append(".")
            if (result.unreadable > 0) append(" Left out ${result.unreadable} this version can't read.")
        }
    }
}

private fun Set<BackupSection>.toggled(section: BackupSection) = if (section in this) this - section else this + section

/** Settings card: choose what goes into a backup, export it to a file, or import one. */
@Composable
fun BackupCard(state: BackupUiState, viewModel: BackupViewModel) {
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(viewModel::export)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::pickedImport)
    }
    CruxCard(modifier = Modifier.testTag("backup_card")) {
        Text("Backup", style = MaterialTheme.typography.titleMedium)
        Text(
            "Save your data to a file you keep, or bring a backup in. When something in it is already here, Crux asks what to do.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = CruxTheme.space.s2),
        )
        BackupSection.entries.forEach { section ->
            ToggleRow(
                label = section.label,
                description = section.description,
                checked = section in state.exportSections,
                onToggle = { viewModel.toggleExport(section) },
                tag = "export_${section.name}",
            )
        }
        Text(
            "Photos and videos",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = CruxTheme.space.s3),
        )
        Text(
            "For exports and imports alike.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ToggleRow(
            label = "Photos",
            description = "Climb photos, wall photos and maps",
            checked = state.media.photos,
            onToggle = { viewModel.setPhotos(!state.media.photos) },
            tag = "backup_photos",
        )
        ToggleRow(
            label = "Videos",
            description = "Climb videos. They can make a backup large.",
            checked = state.media.videos,
            onToggle = { viewModel.setVideos(!state.media.videos) },
            tag = "backup_videos",
        )
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
                onClick = {
                    importLauncher.launch(
                        arrayOf("application/zip", "application/x-zip-compressed", "application/json", "text/plain", "application/octet-stream"),
                    )
                },
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

    state.pendingImport?.let { file -> ImportDialog(file, state, viewModel) }
    state.conflict?.takeIf { !state.busy }?.let { conflict -> ConflictDialog(conflict, state, viewModel) }
}

@Composable
private fun ImportDialog(file: BackupFile, state: BackupUiState, viewModel: BackupViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::cancelImport,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text("Import backup", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Saved ${file.exportedAt.take(10)}. Choose what to bring in.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = CruxTheme.space.s2),
                )
                BackupSection.entries.forEach { section ->
                    val count = file.count(section) ?: 0
                    ToggleRow(
                        label = section.label + if (section == BackupSection.SETTINGS) "" else " · $count",
                        description = if (section ==
                            BackupSection.SETTINGS
                        ) {
                            "Replaces your grades, theme, units, timer and Home layout"
                        } else {
                            section.description
                        },
                        checked = section in state.importSections,
                        enabled = count > 0,
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

/** One record that is already here: what it is both ways, and what to do with it. */
@Composable
private fun ConflictDialog(conflict: ImportConflict, state: BackupUiState, viewModel: BackupViewModel) {
    var forOthers by remember(conflict.section) { mutableStateOf(false) }
    val others = state.othersInSection
    AlertDialog(
        onDismissRequest = {},
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text("Already in Crux", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).testTag("conflict_dialog")) {
                Text(
                    "${conflict.section.label} · ${state.decisions.size + 1} of ${state.conflicts.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(conflict.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = CruxTheme.space.s1))
                Version("In Crux", conflict.existing)
                Version("In the backup", conflict.incoming)
                if (conflict.identical) {
                    Text(
                        "They're the same.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = CruxTheme.space.s1),
                    )
                }
                Column(Modifier.padding(top = CruxTheme.space.s2)) {
                    Resolution.entries.forEach { resolution ->
                        Choice(resolution, onClick = { viewModel.resolve(resolution, forOthers && others > 0) })
                    }
                }
                if (others > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { forOthers = !forOthers }
                            .testTag("conflict_for_others"),
                    ) {
                        Checkbox(checked = forOthers, onCheckedChange = { forOthers = it })
                        Text(
                            "Do the same for the other $others in ${conflict.section.label.lowercase()}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = viewModel::cancelImport) { Text("Cancel import") } },
    )
}

@Composable
private fun Version(label: String, text: String) {
    Column(Modifier.padding(top = CruxTheme.space.s2)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text.ifEmpty { "—" }, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Choice(resolution: Resolution, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = CruxTheme.space.s2, horizontal = CruxTheme.space.s1)
            .testTag("resolve_${resolution.name}"),
    ) {
        Text(resolution.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Text(resolution.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ToggleRow(label: String, description: String, checked: Boolean, onToggle: () -> Unit, tag: String, enabled: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            Text(
                description,
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
