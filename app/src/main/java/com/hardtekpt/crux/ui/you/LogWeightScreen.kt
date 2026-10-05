package com.hardtekpt.crux.ui.you

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hardtekpt.crux.data.BodyRepository
import com.hardtekpt.crux.ui.components.CruxButton
import com.hardtekpt.crux.ui.components.CruxButtonSize
import com.hardtekpt.crux.ui.components.CruxTextField
import com.hardtekpt.crux.ui.components.CruxTopAppBar
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.dayLabel
import com.hardtekpt.crux.ui.theme.CruxTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class LogWeightDraft(
    val text: String = "",
    val date: LocalDate,
    val error: String? = null,
    val saved: Boolean = false,
)

@HiltViewModel
class LogWeightViewModel @Inject constructor(
    private val bodyRepository: BodyRepository,
    clock: Clock,
) : ViewModel() {
    private val _draft = MutableStateFlow(LogWeightDraft(date = LocalDate.now(clock)))
    val draft: StateFlow<LogWeightDraft> = _draft.asStateFlow()

    fun setText(text: String) = _draft.update { it.copy(text = text, error = null) }

    fun save() {
        val draft = _draft.value
        if (draft.saved) return
        val kg = draft.text.replace(',', '.').trim().toDoubleOrNull()
        if (kg == null || kg <= MIN_KG || kg > MAX_KG) {
            _draft.update { it.copy(error = "Enter a weight between 20 and 300 kg") }
            return
        }
        viewModelScope.launch {
            bodyRepository.logWeight(kg, draft.date)
            _draft.update { it.copy(saved = true) }
        }
    }

    private companion object {
        const val MIN_KG = 20.0
        const val MAX_KG = 300.0
    }
}

@Composable
fun LogWeightScreen(
    onDone: () -> Unit,
    viewModel: LogWeightViewModel = hiltViewModel(),
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    LaunchedEffect(draft.saved) { if (draft.saved) onDone() }
    val space = CruxTheme.space

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .testTag("screen_LogWeight"),
    ) {
        CruxTopAppBar(title = "Log weight", onBack = onDone)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = space.s4),
            verticalArrangement = Arrangement.spacedBy(space.s3),
        ) {
            Eyebrow(draft.date.dayLabel())
            CruxTextField(
                label = "Bodyweight",
                value = draft.text,
                onValueChange = viewModel::setText,
                placeholder = "72.4",
                helper = "kg",
                error = draft.error,
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.testTag("field_weight"),
            )
        }
        CruxButton(
            text = "Log weight",
            onClick = viewModel::save,
            icon = Icons.Rounded.Check,
            size = CruxButtonSize.Large,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(space.s4)
                .testTag("save_weight"),
        )
    }
}
