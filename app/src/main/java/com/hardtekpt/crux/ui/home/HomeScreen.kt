package com.hardtekpt.crux.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hardtekpt.crux.ui.components.CruxTopAppBar
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.components.InlineEmptyState
import com.hardtekpt.crux.ui.components.StatTile
import com.hardtekpt.crux.ui.theme.CruxTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(uiState = uiState, onLogClimb = viewModel::logSampleClimb)
}

/** Stateless home dashboard. Widgets follow the design system; more arrive with the blueprint views. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    uiState: HomeUiState,
    onLogClimb: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val today = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
    }
    val space = CruxTheme.space

    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .testTag("screen_Home"),
    ) {
        CruxTopAppBar(title = "Home", scrollBehavior = scrollBehavior)
        LazyColumn(
            contentPadding = PaddingValues(
                start = space.s4,
                end = space.s4,
                top = space.s2,
                bottom = space.s12,
            ),
            verticalArrangement = Arrangement.spacedBy(space.s3),
        ) {
            item { Eyebrow("$today · This week") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(space.s3)) {
                    StatTile(
                        label = "Climbs",
                        value = uiState.figure(uiState.climbsThisWeek),
                        delta = "this week",
                        modifier = Modifier.weight(1f),
                        valueModifier = Modifier.testTag("week_climbs"),
                    )
                    StatTile(
                        label = "Logged",
                        value = uiState.figure(uiState.totalClimbs),
                        delta = "all time",
                        modifier = Modifier.weight(1f),
                        valueModifier = Modifier.testTag("total_climbs"),
                    )
                }
            }
            if (!uiState.isLoading && uiState.totalClimbs == 0) {
                item {
                    InlineEmptyState(
                        icon = Icons.AutoMirrored.Rounded.MenuBook,
                        text = "No climbs logged yet. Tap Log climb to start the journal.",
                    )
                }
            }
            item {
                Button(
                    onClick = onLogClimb,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = space.s6),
                    modifier = Modifier
                        .padding(top = space.s3)
                        .fillMaxWidth()
                        .heightIn(min = CruxTheme.size.touchTarget)
                        .testTag("log_climb"),
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(CruxTheme.size.iconMd),
                    )
                    Text(
                        "Log climb",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = space.s2),
                    )
                }
            }
        }
    }
}

private fun HomeUiState.figure(value: Int) = if (isLoading) "–" else value.toString()

@Preview(showBackground = true, backgroundColor = 0xFF0F1413)
@Composable
private fun HomeContentPreview() {
    CruxTheme {
        HomeContent(
            uiState = HomeUiState(isLoading = false, totalClimbs = 42, climbsThisWeek = 7),
            onLogClimb = {},
        )
    }
}
