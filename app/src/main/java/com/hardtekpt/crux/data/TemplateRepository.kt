package com.hardtekpt.crux.data

import androidx.room.withTransaction
import com.hardtekpt.crux.data.local.CruxDatabases
import com.hardtekpt.crux.data.local.TemplateBlockEntity
import com.hardtekpt.crux.data.local.TemplateExerciseEntity
import com.hardtekpt.crux.data.local.TemplateWithBlocks
import com.hardtekpt.crux.data.local.WorkoutTemplateEntity
import com.hardtekpt.crux.data.model.ExerciseTarget
import com.hardtekpt.crux.data.model.PlanBlock
import com.hardtekpt.crux.data.model.PlanItem
import com.hardtekpt.crux.data.model.WorkoutTemplate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface TemplateRepository {
    fun observeTemplates(): Flow<List<WorkoutTemplate>>
    fun observeTemplate(id: Long): Flow<WorkoutTemplate?>
    suspend fun getTemplate(id: Long): WorkoutTemplate?
    /** Creates the plan when `id == 0`, otherwise replaces its contents. Returns its id. */
    suspend fun saveTemplate(template: WorkoutTemplate): Long
    suspend fun deleteTemplate(id: Long)
}

class OfflineTemplateRepository @Inject constructor(
    private val dbs: CruxDatabases,
) : TemplateRepository {
    override fun observeTemplates(): Flow<List<WorkoutTemplate>> =
        dbs.observe { it.templateDao().observeAll() }.map { it.map(TemplateWithBlocks::toModel) }

    override fun observeTemplate(id: Long): Flow<WorkoutTemplate?> =
        dbs.observe { it.templateDao().observe(id) }.map { it?.toModel() }

    override suspend fun getTemplate(id: Long): WorkoutTemplate? = dbs.current().templateDao().get(id)?.toModel()

    override suspend fun saveTemplate(template: WorkoutTemplate): Long {
        val db = dbs.current()
        val dao = db.templateDao()
        return db.withTransaction { insertOrReplace(dao, template) }
    }

    private suspend fun insertOrReplace(dao: com.hardtekpt.crux.data.local.TemplateDao, template: WorkoutTemplate): Long {
        val id = if (template.id == 0L) {
            dao.insertTemplate(
                WorkoutTemplateEntity(
                    name = template.name.trim(),
                    description = template.description.trim(),
                    position = dao.nextPosition(),
                ),
            )
        } else {
            val existing = dao.get(template.id)?.template
                ?: error("Plan ${template.id} no longer exists")
            dao.updateTemplate(existing.copy(name = template.name.trim(), description = template.description.trim()))
            dao.deleteBlocks(existing.id)
            existing.id
        }
        template.blocks.forEachIndexed { blockPosition, block ->
            val blockId = dao.insertBlock(
                TemplateBlockEntity(templateId = id, position = blockPosition, name = block.name.trim()),
            )
            dao.insertExercises(
                block.items.mapIndexed { position, item ->
                    TemplateExerciseEntity(
                        blockId = blockId,
                        exerciseId = item.exercise.id,
                        position = position,
                        sets = item.target.sets,
                        reps = item.target.reps,
                        seconds = item.target.seconds,
                        loadKg = item.target.loadKg,
                        restSeconds = item.target.restSeconds,
                    )
                },
            )
        }
        return id
    }

    override suspend fun deleteTemplate(id: Long) = dbs.current().templateDao().deleteTemplate(id)
}

private fun TemplateWithBlocks.toModel() = WorkoutTemplate(
    id = template.id,
    name = template.name,
    description = template.description,
    blocks = blocks.sortedBy { it.block.position }.map { block ->
        PlanBlock(
            name = block.block.name,
            items = block.exercises.sortedBy { it.item.position }.map {
                PlanItem(
                    exercise = it.exercise.toModel(),
                    target = ExerciseTarget(
                        sets = it.item.sets,
                        reps = it.item.reps,
                        seconds = it.item.seconds,
                        loadKg = it.item.loadKg,
                        restSeconds = it.item.restSeconds,
                    ),
                )
            },
        )
    },
)
