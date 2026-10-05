package com.hardtekpt.crux.data

import com.hardtekpt.crux.data.local.TemplateDao
import com.hardtekpt.crux.data.local.TemplateWithBlocks
import com.hardtekpt.crux.data.model.TemplateBlock
import com.hardtekpt.crux.data.model.TemplateExercise
import com.hardtekpt.crux.data.model.WorkoutTemplate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface TemplateRepository {
    fun observeTemplates(): Flow<List<WorkoutTemplate>>
    fun observeTemplate(id: Long): Flow<WorkoutTemplate?>
}

class OfflineTemplateRepository @Inject constructor(
    private val dao: TemplateDao,
) : TemplateRepository {
    override fun observeTemplates(): Flow<List<WorkoutTemplate>> =
        dao.observeAll().map { it.map(TemplateWithBlocks::toModel) }

    override fun observeTemplate(id: Long): Flow<WorkoutTemplate?> =
        dao.observe(id).map { it?.toModel() }
}

private fun TemplateWithBlocks.toModel() = WorkoutTemplate(
    id = template.id,
    name = template.name,
    description = template.description,
    estimatedMinutes = template.estimatedMinutes,
    blocks = blocks.sortedBy { it.block.position }.map { block ->
        TemplateBlock(
            name = block.block.name,
            exercises = block.exercises.sortedBy { it.position }.map {
                TemplateExercise(name = it.name, target = it.target, rest = it.rest)
            },
        )
    },
)
