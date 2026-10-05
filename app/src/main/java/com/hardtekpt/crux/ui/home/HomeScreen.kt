package com.hardtekpt.crux.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hardtekpt.crux.ui.theme.CruxTheme

@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(uiState = uiState, onLogClimb = viewModel::logSampleClimb)
}

/** Stateless home dashboard. Widgets are placeholders until the view drafts land. */
@Composable
fun HomeContent(
    uiState: HomeUiState,
    onLogClimb: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("screen_Home"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Home", style = MaterialTheme.typography.headlineMedium)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Climbs logged", style = MaterialTheme.typography.labelLarge)
                Text(
                    text = if (uiState.isLoading) "…" else uiState.totalClimbs.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.testTag("total_climbs"),
                )
            }
        }
        ExtendedFloatingActionButton(
            onClick = onLogClimb,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Log a climb") },
            modifier = Modifier.testTag("log_climb"),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeContentPreview() {
    CruxTheme(dynamicColor = false) {
        HomeContent(uiState = HomeUiState(isLoading = false, totalClimbs = 12), onLogClimb = {})
    }
}
