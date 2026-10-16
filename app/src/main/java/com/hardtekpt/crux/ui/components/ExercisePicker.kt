package com.hardtekpt.crux.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.hardtekpt.crux.data.model.Exercise
import com.hardtekpt.crux.ui.theme.CruxTheme

/**
 * Picking an exercise from the library, for a plan or a live session: a search field over the
 * library, each row with its category and how it's measured. Tapping one picks it.
 */
@Composable
fun ExercisePickerSheet(library: List<Exercise>, onPick: (Exercise) -> Unit, onDismiss: () -> Unit, title: String = "Add an exercise") {
    var query by rememberSaveable { mutableStateOf("") }
    val matches = library.filter { it.name.contains(query.trim(), ignoreCase = true) }
    CruxSheet(onDismiss = onDismiss, modifier = Modifier.testTag("exercise_picker"), title = title, scroll = false) {
        CruxTextField(
            label = "",
            value = query,
            onValueChange = { query = it },
            placeholder = "Search the library",
            modifier = Modifier.testTag("exercise_search"),
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s2),
            contentPadding = PaddingValues(bottom = CruxTheme.space.s4),
        ) {
            if (matches.isEmpty()) {
                item {
                    Text(
                        if (library.isEmpty()) "Your library is empty. Add exercises from Train › Exercises." else "Nothing matches \"$query\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(matches, key = { it.id }) { exercise ->
                CruxListRow(
                    title = exercise.name,
                    supporting = "${exercise.category.label} · ${exercise.metric.label}",
                    onClick = { onPick(exercise) },
                    modifier = Modifier.testTag("pick_${exercise.name}"),
                )
            }
        }
    }
}
