package com.hardtekpt.crux.ui.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.ViewModel
import com.hardtekpt.crux.data.diagnostics.CrashReports
import com.hardtekpt.crux.ui.components.CruxButton
import com.hardtekpt.crux.ui.components.CruxButtonVariant
import com.hardtekpt.crux.ui.components.CruxCard
import com.hardtekpt.crux.ui.theme.CruxTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class CrashReportsViewModel @Inject constructor(private val reports: CrashReports) : ViewModel() {
    val count: StateFlow<Int> = reports.count

    suspend fun latestReport(): String? = withContext(Dispatchers.IO) { reports.latest()?.readText() }

    fun clear() = reports.clear()
}

/** Settings card: crash reports saved on this phone, to share with a bug report. */
@Composable
fun CrashReportsCard(count: Int, onShare: () -> Unit, onClear: () -> Unit) {
    CruxCard(modifier = Modifier.testTag("crash_reports_card")) {
        Text("Crash reports", style = MaterialTheme.typography.titleMedium)
        Text(
            if (count == 0) {
                "None so far. If Crux ever closes unexpectedly, a report is saved here, on this phone only."
            } else {
                "$count saved on this phone. Share the latest to attach it to a bug report; nothing is sent unless you share it."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (count > 0) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2),
                modifier = Modifier.padding(top = CruxTheme.space.s3),
            ) {
                CruxButton(
                    text = "Share latest",
                    onClick = onShare,
                    icon = Icons.Rounded.Share,
                    modifier = Modifier.testTag("share_crash_report"),
                )
                CruxButton(
                    text = "Clear",
                    onClick = onClear,
                    variant = CruxButtonVariant.Outlined,
                    icon = Icons.Rounded.Delete,
                    modifier = Modifier.testTag("clear_crash_reports"),
                )
            }
        }
    }
}

/** The card wired to its view model; sharing opens the system share sheet with the report as text. */
@Composable
fun CrashReportsCard(count: Int, viewModel: CrashReportsViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    CrashReportsCard(
        count = count,
        onShare = { scope.launch { viewModel.latestReport()?.let { context.shareCrashReport(it) } } },
        onClear = viewModel::clear,
    )
}

private fun Context.shareCrashReport(report: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Crux crash report")
        putExtra(Intent.EXTRA_TEXT, report)
    }
    startActivity(Intent.createChooser(send, "Share crash report"))
}
