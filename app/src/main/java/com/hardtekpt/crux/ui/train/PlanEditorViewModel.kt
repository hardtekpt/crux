package com.hardtekpt.crux.ui.train

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.hardtekpt.crux.data.ExerciseRepository
import com.hardtekpt.crux.data.TemplateRepository
import com.hardtekpt.crux.data.model.Exercise
import com.hardtekpt.crux.data.model.ExerciseTarget
import com.hardtekpt.crux.data.model.PlanBlock
import com.hardtekpt.crux.data.model.PlanItem
import com.hardtekpt.crux.data.model.WorkoutTemplate
import com.hardtekpt.crux.ui.navigation.PlanEditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Stable keys let Compose animate and track rows while blocks and items move. */
data class ItemDraft(val key: Long, val exercise: Exercise, val target: ExerciseTarget)

data class BlockDraft(val key: Long, val name: String, val items: List<ItemDraft> = emptyList())

/** Points at one item in the draft. */
data class ItemRef(val block: Int, val item: Int)

data class PlanDraft(
    val id: Long = 0,
    val name: String = "",
    val description: String = "",
    val blocks: List<BlockDraft> = emptyList(),
    val nameError: String? = null,
    val contentError: String? = null,
    /** The block the exercise picker adds to, when open. */
    val pickingFor: Int? = null,
    /** The item whose targets are being edited, when open. */
    val editing: ItemRef? = null,
    val confirmDelete: Boolean = false,
    val isSaving: Boolean = false,
    val done: Boolean = false,
) {
    val isNew: Boolean get() = id == 0L
    val editingItem: ItemDraft? get() = editing?.let { blocks.getOrNull(it.block)?.items?.getOrNull(it.item) }

    fun toTemplate() = WorkoutTemplate(
        id = id,
        name = name.trim(),
        description = description.trim(),
        blocks = blocks.filter { it.items.isNotEmpty() }.map { block ->
            PlanBlock(
                name = block.name.trim().ifEmpty { "Block" },
                items = block.items.map { PlanItem(it.exercise, it.target) },
            )
        },
    )
}

@HiltViewModel
class PlanEditorViewModel @Inject constructor(savedStateHandle: SavedStateHandle, private val templates: TemplateRepository, exercises: ExerciseRepository) :
    ViewModel() {
    private val templateId = savedStateHandle.toRoute<PlanEditorRoute>().templateId
    private var nextKey = 0L

    private val _draft = MutableStateFlow(
        PlanDraft(id = templateId, blocks = if (templateId == 0L) listOf(BlockDraft(key(), "Main")) else emptyList()),
    )
    val draft: StateFlow<PlanDraft> = _draft.asStateFlow()

    val library: StateFlow<List<Exercise>> = exercises.observeExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (templateId != 0L) {
            viewModelScope.launch {
                templates.getTemplate(templateId)?.let { template ->
                    _draft.update {
                        it.copy(
                            name = template.name,
                            description = template.description,
                            blocks = template.blocks.map { block ->
                                BlockDraft(key(), block.name, block.items.map { item -> ItemDraft(key(), item.exercise, item.target) })
                            },
                        )
                    }
                }
            }
        }
    }

    private fun key() = nextKey++

    fun setName(name: String) = _draft.update { it.copy(name = name, nameError = null) }
    fun setDescription(description: String) = _draft.update { it.copy(description = description) }

    fun addBlock() = _draft.update {
        it.copy(blocks = it.blocks + BlockDraft(key(), "Block ${it.blocks.size + 1}"), contentError = null)
    }

    fun renameBlock(block: Int, name: String) = updateBlock(block) { it.copy(name = name) }

    fun removeBlock(block: Int) = _draft.update { draft ->
        draft.copy(blocks = draft.blocks.filterIndexed { index, _ -> index != block }, editing = null)
    }

    fun moveBlock(block: Int, delta: Int) = _draft.update { it.copy(blocks = it.blocks.moved(block, delta)) }

    fun openPicker(block: Int) = _draft.update { it.copy(pickingFor = block) }
    fun closePicker() = _draft.update { it.copy(pickingFor = null) }

    /** Adds with sensible targets for the exercise's metric, then opens them for tweaking. */
    fun addExercise(exercise: Exercise) = _draft.update { draft ->
        val block = draft.pickingFor ?: return@update draft
        val items = draft.blocks[block].items + ItemDraft(key(), exercise, ExerciseTarget.defaultFor(exercise.metric))
        draft.copy(
            blocks = draft.blocks.replaced(block) { it.copy(items = items) },
            pickingFor = null,
            editing = ItemRef(block, items.lastIndex),
            contentError = null,
        )
    }

    fun removeItem(ref: ItemRef) = updateBlock(ref.block) { block ->
        block.copy(items = block.items.filterIndexed { index, _ -> index != ref.item })
    }.also { _draft.update { it.copy(editing = null) } }

    fun moveItem(ref: ItemRef, delta: Int) = updateBlock(ref.block) { it.copy(items = it.items.moved(ref.item, delta)) }

    fun editItem(ref: ItemRef) = _draft.update { it.copy(editing = ref) }
    fun closeEditor() = _draft.update { it.copy(editing = null) }

    fun updateTarget(target: ExerciseTarget) = _draft.update { draft ->
        val ref = draft.editing ?: return@update draft
        draft.copy(
            blocks = draft.blocks.replaced(ref.block) { block ->
                block.copy(items = block.items.replaced(ref.item) { it.copy(target = target) })
            },
        )
    }

    fun save() {
        val draft = _draft.value
        if (draft.isSaving) return
        val nameError = when {
            draft.name.isBlank() -> "Give the plan a name"
            draft.name.trim().length > MAX_NAME -> "Keep the name under $MAX_NAME characters"
            else -> null
        }
        val contentError = if (draft.blocks.all { it.items.isEmpty() }) "Add at least one exercise" else null
        if (nameError != null || contentError != null) {
            _draft.update { it.copy(nameError = nameError, contentError = contentError) }
            return
        }
        _draft.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            templates.saveTemplate(draft.toTemplate())
            _draft.update { it.copy(isSaving = false, done = true) }
        }
    }

    fun requestDelete() = _draft.update { it.copy(confirmDelete = true) }
    fun cancelDelete() = _draft.update { it.copy(confirmDelete = false) }

    fun confirmDelete() {
        viewModelScope.launch {
            templates.deleteTemplate(templateId)
            _draft.update { it.copy(confirmDelete = false, done = true) }
        }
    }

    private fun updateBlock(block: Int, change: (BlockDraft) -> BlockDraft) = _draft.update { it.copy(blocks = it.blocks.replaced(block, change)) }

    companion object {
        const val MAX_NAME = 40
    }
}

private fun <T> List<T>.replaced(index: Int, change: (T) -> T): List<T> = mapIndexed { i, value -> if (i == index) change(value) else value }

private fun <T> List<T>.moved(index: Int, delta: Int): List<T> {
    val target = index + delta
    if (index !in indices || target !in indices) return this
    return toMutableList().apply { add(target, removeAt(index)) }
}
