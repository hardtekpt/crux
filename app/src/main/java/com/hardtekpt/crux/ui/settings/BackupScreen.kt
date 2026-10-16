package com.hardtekpt.crux.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hardtekpt.crux.data.backup.AutoBackups
import com.hardtekpt.crux.data.backup.BackupFile
import com.hardtekpt.crux.data.backup.BackupFormatException
import com.hardtekpt.crux.data.backup.BackupMedia
import com.hardtekpt.crux.data.backup.BackupRepository
import com.hardtekpt.crux.data.backup.BackupSection
import com.hardtekpt.crux.data.backup.ConflictKey
import com.hardtekpt.crux.data.backup.CsvExport
import com.hardtekpt.crux.data.backup.ImportConflict
import com.hardtekpt.crux.data.backup.ImportResult
import com.hardtekpt.crux.data.backup.OpenedBackup
import com.hardtekpt.crux.data.backup.Resolution
import com.hardtekpt.crux.data.prefs.AutoBackupFrequency
import com.hardtekpt.crux.data.prefs.AutoBackupPrefs
import com.hardtekpt.crux.data.prefs.UserPreferencesRepository
import com.hardtekpt.crux.ui.components.CruxButton
import com.hardtekpt.crux.ui.components.CruxButtonVariant
import com.hardtekpt.crux.ui.components.CruxCard
import com.hardtekpt.crux.ui.components.CruxFilterChip
import com.hardtekpt.crux.ui.components.CruxTopAppBar
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.navigation.LocalNavBarClearance
import com.hardtekpt.crux.ui.theme.CruxTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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

/** The automatic backups settings, apart from the file export and import above them. */
@HiltViewModel
class AutoBackupViewModel @Inject constructor(private val auto: AutoBackups) : ViewModel() {
    val state: StateFlow<AutoBackupPrefs> = auto.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AutoBackupPrefs())

    fun setFolder(uri: Uri?) {
        viewModelScope.launch { auto.setFolder(uri) }
    }

    fun setFrequency(frequency: AutoBackupFrequency) {
        viewModelScope.launch { auto.setFrequency(frequency) }
    }

    fun setKeep(keep: Int) {
        viewModelScope.launch { auto.setKeep(keep) }
    }

    fun backUpNow() = auto.runNow()
}

/** Climbs and sessions as a spreadsheet file, apart from the full backups. */
@HiltViewModel
class CsvExportViewModel @Inject constructor(private val backup: BackupRepository, private val contentResolver: ContentResolver, private val clock: Clock) :
    ViewModel() {
    private val _message = MutableStateFlow<String?>(null)

    /** One line about the last export, or what went wrong. */
    val message: StateFlow<String?> = _message.asStateFlow()

    fun fileName(kind: String): String = "crux-$kind-${LocalDate.now(clock)}.csv"

    fun exportClimbs(uri: Uri) = write(uri, "Climbs saved.", CsvExport::climbs)

    fun exportSessions(uri: Uri) = write(uri, "Sessions saved.") { CsvExport.sessions(it) }

    private fun write(uri: Uri, done: String, csv: (com.hardtekpt.crux.data.backup.BackupFile) -> String) {
        viewModelScope.launch {
            _message.value = try {
                withContext(Dispatchers.IO) {
                    val sections = setOf(BackupSection.JOURNAL, BackupSection.SESSIONS, BackupSection.PLACES)
                    val text = csv(backup.snapshot(sections, BackupMedia.NONE))
                    val out = contentResolver.openOutputStream(uri, "wt") ?: error("Could not open the file to write")
                    out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }
                done
            } catch (e: Exception) {
                "Something went wrong: ${e.message ?: "try again"}."
            }
        }
    }
}

private fun Set<BackupSection>.toggled(section: BackupSection) = if (section in this) this - section else this + section

/** What the backups page can ask for. */
data class BackupActions(
    val toggleExport: (BackupSection) -> Unit = {},
    val setPhotos: (Boolean) -> Unit = {},
    val setVideos: (Boolean) -> Unit = {},
    val export: () -> Unit = {},
    val import: () -> Unit = {},
    val toggleImport: (BackupSection) -> Unit = {},
    val confirmImport: () -> Unit = {},
    val cancelImport: () -> Unit = {},
    val resolve: (Resolution, Boolean) -> Unit = { _, _ -> },
    val chooseAutoFolder: () -> Unit = {},
    val stopAutoBackups: () -> Unit = {},
    val setAutoFrequency: (AutoBackupFrequency) -> Unit = {},
    val setAutoKeep: (Int) -> Unit = {},
    val backUpNow: () -> Unit = {},
    val exportCsvClimbs: () -> Unit = {},
    val exportCsvSessions: () -> Unit = {},
)

/** Settings → Backups: choose what goes into a backup, export it to a file, or import one. */
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
    autoViewModel: AutoBackupViewModel = hiltViewModel(),
    csvViewModel: CsvExportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val auto by autoViewModel.state.collectAsStateWithLifecycle()
    val csvMessage by csvViewModel.message.collectAsStateWithLifecycle()
    val climbsCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(csvViewModel::exportClimbs)
    }
    val sessionsCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(csvViewModel::exportSessions)
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(autoViewModel::setFolder)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(viewModel::export)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::pickedImport)
    }
    BackupContent(
        state = state,
        onBack = onBack,
        auto = auto,
        csvMessage = csvMessage,
        actions = BackupActions(
            toggleExport = viewModel::toggleExport,
            setPhotos = viewModel::setPhotos,
            setVideos = viewModel::setVideos,
            export = { exportLauncher.launch(viewModel.suggestedFileName) },
            import = {
                importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/json", "text/plain", "application/octet-stream"))
            },
            toggleImport = viewModel::toggleImport,
            confirmImport = viewModel::confirmImport,
            cancelImport = viewModel::cancelImport,
            resolve = viewModel::resolve,
            chooseAutoFolder = { folderLauncher.launch(null) },
            stopAutoBackups = { autoViewModel.setFolder(null) },
            setAutoFrequency = autoViewModel::setFrequency,
            setAutoKeep = autoViewModel::setKeep,
            backUpNow = autoViewModel::backUpNow,
            exportCsvClimbs = { climbsCsvLauncher.launch(csvViewModel.fileName("climbs")) },
            exportCsvSessions = { sessionsCsvLauncher.launch(csvViewModel.fileName("sessions")) },
        ),
    )
}

@Composable
fun BackupContent(
    state: BackupUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    auto: AutoBackupPrefs = AutoBackupPrefs(),
    csvMessage: String? = null,
    actions: BackupActions = BackupActions(),
) {
    val space = CruxTheme.space
    Column(modifier.fillMaxSize().testTag("screen_Backups")) {
        CruxTopAppBar(title = "Backups", onBack = onBack)
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = space.s4)
                .padding(bottom = space.s4 + LocalNavBarClearance.current),
            verticalArrangement = Arrangement.spacedBy(space.s3),
        ) {
            Text(
                "Save your data to a file you keep, or bring a backup in. When something in a backup is already here, Climb Logger asks what to do.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.message?.let {
                CruxCard(modifier = Modifier.testTag("backup_message")) {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Eyebrow("Export", Modifier.padding(top = space.s2))
            CruxCard {
                Text("What to include", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = space.s2))
                BackupSection.entries.forEach { section ->
                    ToggleRow(
                        label = section.label,
                        description = section.description,
                        checked = section in state.exportSections,
                        onToggle = { actions.toggleExport(section) },
                        tag = "export_${section.name}",
                    )
                }
                CruxButton(
                    text = "Export",
                    onClick = actions.export,
                    enabled = !state.busy && state.exportSections.isNotEmpty(),
                    icon = Icons.Rounded.Upload,
                    modifier = Modifier
                        .padding(top = space.s3)
                        .testTag("export_backup"),
                )
            }

            Eyebrow("Automatic backups", Modifier.padding(top = space.s4))
            AutoBackupCard(auto, actions)

            Eyebrow("Export as a spreadsheet", Modifier.padding(top = space.s4))
            CruxCard(modifier = Modifier.testTag("export_csv")) {
                Text(
                    "Your climbs or sessions as a table for a spreadsheet app. Photos and videos stay out.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.padding(top = space.s3), horizontalArrangement = Arrangement.spacedBy(space.s2)) {
                    CruxButton(
                        text = "Climbs",
                        onClick = actions.exportCsvClimbs,
                        variant = CruxButtonVariant.Tonal,
                        modifier = Modifier.testTag("export_csv_climbs"),
                    )
                    CruxButton(
                        text = "Sessions",
                        onClick = actions.exportCsvSessions,
                        variant = CruxButtonVariant.Tonal,
                        modifier = Modifier.testTag("export_csv_sessions"),
                    )
                }
                csvMessage?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .padding(top = space.s2)
                            .testTag("export_csv_message"),
                    )
                }
            }

            Eyebrow("Photos and videos", Modifier.padding(top = space.s4))
            CruxCard {
                ToggleRow(
                    label = "Photos",
                    description = "Climb photos, wall photos and maps",
                    checked = state.media.photos,
                    onToggle = { actions.setPhotos(!state.media.photos) },
                    tag = "backup_photos",
                )
                ToggleRow(
                    label = "Videos",
                    description = "Climb videos. They can make a backup large.",
                    checked = state.media.videos,
                    onToggle = { actions.setVideos(!state.media.videos) },
                    tag = "backup_videos",
                )
            }
            Text(
                "For exports and imports alike: switched off, they're left out of the file you save and of the backups you bring in.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Eyebrow("Import", Modifier.padding(top = space.s4))
            CruxCard {
                Text("Bring a backup in", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Pick a backup file, then choose what to bring in. Nothing changes until you've answered for anything that's already here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CruxButton(
                    text = "Import",
                    onClick = actions.import,
                    enabled = !state.busy,
                    variant = CruxButtonVariant.Outlined,
                    icon = Icons.Rounded.Download,
                    modifier = Modifier
                        .padding(top = space.s3)
                        .testTag("import_backup"),
                )
            }
        }
    }

    state.pendingImport?.let { file -> ImportDialog(file, state, actions) }
    state.conflict?.takeIf { !state.busy }?.let { conflict -> ConflictDialog(conflict, state, actions) }
}

@Composable
private fun AutoBackupCard(auto: AutoBackupPrefs, actions: BackupActions) {
    val space = CruxTheme.space
    val colors = MaterialTheme.colorScheme
    val hasFolder = auto.folder != null
    CruxCard(modifier = Modifier.testTag("auto_backups")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.s2)) {
            Column(Modifier.weight(1f)) {
                Text("Folder", style = MaterialTheme.typography.titleMedium)
                Text(
                    auto.folder?.let { Uri.parse(it).lastPathSegment?.substringAfter(':') } ?: "No folder yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.testTag("auto_backup_folder"),
                )
            }
            if (hasFolder) TextButton(onClick = actions.stopAutoBackups, modifier = Modifier.testTag("auto_backup_stop")) { Text("Stop") }
            CruxButton(
                text = "Choose folder",
                onClick = actions.chooseAutoFolder,
                variant = CruxButtonVariant.Tonal,
                modifier = Modifier.testTag("auto_backup_choose"),
            )
        }
        Text("How often", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = space.s3, bottom = space.s2))
        Row(horizontalArrangement = Arrangement.spacedBy(space.s2)) {
            AutoBackupFrequency.entries.forEach { frequency ->
                CruxFilterChip(
                    label = frequency.label,
                    selected = auto.frequency == frequency,
                    onClick = { actions.setAutoFrequency(frequency) },
                    modifier = Modifier.testTag("auto_backup_${frequency.name}"),
                )
            }
        }
        Text("Keep the newest", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = space.s3, bottom = space.s2))
        Row(horizontalArrangement = Arrangement.spacedBy(space.s2)) {
            listOf(3, 5, 10).forEach { keep ->
                CruxFilterChip(
                    label = "$keep",
                    selected = auto.keep == keep,
                    onClick = { actions.setAutoKeep(keep) },
                    modifier = Modifier.testTag("auto_keep_$keep"),
                )
            }
        }
        CruxButton(
            text = "Back up now",
            onClick = actions.backUpNow,
            enabled = hasFolder,
            modifier = Modifier
                .padding(top = space.s3)
                .testTag("auto_backup_now"),
        )
        val error = auto.lastError
        val lastAt = auto.lastAtMillis
        Text(
            when {
                error != null -> "Last try failed: $error"
                lastAt != null -> "Last backup: ${lastBackupLabel(lastAt)}"
                else -> "Not backed up yet"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (error != null) colors.error else colors.onSurfaceVariant,
            modifier = Modifier
                .padding(top = space.s2)
                .testTag("auto_backup_status"),
        )
    }
    Text(
        "Everything, with photos and videos, saved to the folder you choose. Nothing leaves your phone otherwise.",
        style = MaterialTheme.typography.bodySmall,
        color = colors.onSurfaceVariant,
    )
}

private val LAST_BACKUP_FORMAT = DateTimeFormatter.ofPattern("d MMM, HH:mm")

private fun lastBackupLabel(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(LAST_BACKUP_FORMAT)

@Composable
private fun ImportDialog(file: BackupFile, state: BackupUiState, actions: BackupActions) {
    AlertDialog(
        onDismissRequest = actions.cancelImport,
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
                        onToggle = { actions.toggleImport(section) },
                        tag = "import_${section.name}",
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = actions.confirmImport,
                enabled = state.importSections.isNotEmpty(),
                modifier = Modifier.testTag("confirm_import"),
            ) { Text("Import") }
        },
        dismissButton = { TextButton(onClick = actions.cancelImport) { Text("Cancel") } },
    )
}

/** One record that is already here: what it is both ways, and what to do with it. */
@Composable
private fun ConflictDialog(conflict: ImportConflict, state: BackupUiState, actions: BackupActions) {
    var forOthers by remember(conflict.section) { mutableStateOf(false) }
    var picked by remember(conflict) { mutableStateOf(Resolution.SKIP) }
    val others = state.othersInSection
    AlertDialog(
        onDismissRequest = {},
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text("Already in Climb Logger", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).testTag("conflict_dialog")) {
                Text(
                    "${conflict.section.label} · ${state.decisions.size + 1} of ${state.conflicts.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(conflict.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = CruxTheme.space.s1))
                Version("In Climb Logger", conflict.existing)
                Version("In the backup", conflict.incoming)
                if (conflict.identical) {
                    Text(
                        "They're the same.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = CruxTheme.space.s1),
                    )
                }
                // The same three chips as a dataset import asks with; the pick shows what it means.
                Row(horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2), modifier = Modifier.padding(top = CruxTheme.space.s3)) {
                    Resolution.entries.forEach { resolution ->
                        com.hardtekpt.crux.ui.components.CruxFilterChip(
                            resolution.label,
                            picked == resolution,
                            { picked = resolution },
                            Modifier.testTag("resolve_${resolution.name}"),
                        )
                    }
                }
                Text(
                    picked.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = CruxTheme.space.s1),
                )
                if (others > 0) {
                    com.hardtekpt.crux.ui.components.CruxFilterChip(
                        "Same for the other $others",
                        forOthers,
                        { forOthers = !forOthers },
                        Modifier.padding(top = CruxTheme.space.s2).testTag("conflict_for_others"),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { actions.resolve(picked, forOthers && others > 0) }, modifier = Modifier.testTag("resolve_next")) {
                Text(if (state.decisions.size + 1 >= state.conflicts.size || (forOthers && others > 0)) "Done" else "Next")
            }
        },
        dismissButton = { TextButton(onClick = actions.cancelImport) { Text("Cancel import") } },
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
