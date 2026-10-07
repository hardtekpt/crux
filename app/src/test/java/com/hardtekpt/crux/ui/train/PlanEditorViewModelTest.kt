package com.hardtekpt.crux.ui.train

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hardtekpt.crux.MainDispatcherRule
import com.hardtekpt.crux.data.ExerciseInput
import com.hardtekpt.crux.data.FakeExerciseRepository
import com.hardtekpt.crux.data.FakeTemplateRepository
import com.hardtekpt.crux.data.model.Exercise
import com.hardtekpt.crux.data.model.ExerciseCategory
import com.hardtekpt.crux.data.model.ExerciseTarget
import com.hardtekpt.crux.data.model.MetricType
import com.hardtekpt.crux.data.model.PlanBlock
import com.hardtekpt.crux.data.model.PlanItem
import com.hardtekpt.crux.data.model.WorkoutTemplate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Robolectric only for SavedStateHandle's route decoding. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PlanEditorViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val templates = FakeTemplateRepository()
    private val exercises = FakeExerciseRepository()
    private val hang = Exercise(1, "Half-crimp hang", ExerciseCategory.FINGERS, MetricType.WEIGHTED_TIME, null)
    private val pullUps = Exercise(2, "Pull-ups", ExerciseCategory.PULLING, MetricType.REPS, null)

    private fun viewModel(templateId: Long = 0) = PlanEditorViewModel(SavedStateHandle(mapOf("templateId" to templateId)), templates, exercises)

    @Test
    fun `a new plan needs a name and an exercise`() {
        val vm = viewModel()
        vm.save()

        assertNotNull(vm.draft.value.nameError)
        assertNotNull(vm.draft.value.contentError)
        assertTrue(templates.templates.value.isEmpty())
    }

    @Test
    fun `adding an exercise uses defaults for its metric and opens its targets`() {
        val vm = viewModel()
        vm.openPicker(0)
        vm.addExercise(hang)

        val draft = vm.draft.value
        assertEquals(ExerciseTarget.defaultFor(MetricType.WEIGHTED_TIME), draft.blocks[0].items.single().target)
        assertEquals(ItemRef(0, 0), draft.editing)
        assertNull(draft.pickingFor)
    }

    @Test
    fun `items move within a block and targets update`() {
        val vm = viewModel()
        vm.openPicker(0)
        vm.addExercise(hang)
        vm.openPicker(0)
        vm.addExercise(pullUps)
        vm.moveItem(ItemRef(0, 1), -1)
        vm.editItem(ItemRef(0, 0))
        vm.updateTarget(ExerciseTarget(sets = 4, reps = 6))

        val items = vm.draft.value.blocks[0].items
        assertEquals(listOf("Pull-ups", "Half-crimp hang"), items.map { it.exercise.name })
        assertEquals(4, items[0].target.sets)
    }

    @Test
    fun `saving drops empty blocks and stores the plan`() = runTest {
        val vm = viewModel()
        vm.setName("  Finger day ")
        vm.openPicker(0)
        vm.addExercise(hang)
        vm.addBlock()
        vm.save()

        val saved = templates.templates.value.single()
        assertEquals("Finger day", saved.name)
        assertEquals(1, saved.blocks.size)
        assertTrue(vm.draft.value.done)
    }

    @Test
    fun `editing loads the existing plan`() = runTest {
        exercises.saveExercise(ExerciseInput(name = "Pull-ups", category = ExerciseCategory.PULLING, metric = MetricType.REPS, notes = null))
        templates.templates.value = listOf(
            WorkoutTemplate(7, "Strength", "", listOf(PlanBlock("Pull", listOf(PlanItem(pullUps, ExerciseTarget(sets = 5)))))),
        )
        val vm = viewModel(templateId = 7)

        val draft = vm.draft.value
        assertEquals("Strength", draft.name)
        assertEquals(5, draft.blocks.single().items.single().target.sets)
    }
}
