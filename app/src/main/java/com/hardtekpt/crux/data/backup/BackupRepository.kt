package com.hardtekpt.crux.data.backup

import androidx.room.withTransaction
import com.hardtekpt.crux.data.ExerciseRecordEntity
import com.hardtekpt.crux.data.NoteEntity
import com.hardtekpt.crux.data.images.AreaImageStore
import com.hardtekpt.crux.data.local.AreaEntity
import com.hardtekpt.crux.data.local.BodyMeasurementEntity
import com.hardtekpt.crux.data.local.ClimbEntity
import com.hardtekpt.crux.data.local.ClimbMediaEntity
import com.hardtekpt.crux.data.local.CruxDatabase
import com.hardtekpt.crux.data.local.CruxDatabases
import com.hardtekpt.crux.data.local.ExerciseEntity
import com.hardtekpt.crux.data.local.MediaKind
import com.hardtekpt.crux.data.local.PlaceEntity
import com.hardtekpt.crux.data.local.ProblemEntity
import com.hardtekpt.crux.data.local.SectionEntity
import com.hardtekpt.crux.data.local.SessionEntity
import com.hardtekpt.crux.data.local.SessionItemEntity
import com.hardtekpt.crux.data.local.SessionSetEntity
import com.hardtekpt.crux.data.local.SessionStatus
import com.hardtekpt.crux.data.local.TemplateBlockEntity
import com.hardtekpt.crux.data.local.TemplateExerciseEntity
import com.hardtekpt.crux.data.local.WorkoutTemplateEntity
import com.hardtekpt.crux.data.model.ExerciseTarget
import com.hardtekpt.crux.data.model.GradeScale
import com.hardtekpt.crux.data.model.LocalScale
import com.hardtekpt.crux.data.model.PlaceType
import com.hardtekpt.crux.data.model.gradeLabel
import com.hardtekpt.crux.data.parseTypes
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

@Singleton
class BackupRepository(
    /** The data set to back up or restore into: whichever is active. */
    private val database: suspend () -> CruxDatabase,
    private val clock: Clock,
    /** Photos and videos travel inside the archive; without a store they are left out. */
    private val images: AreaImageStore? = null,
    /** App settings; without them the settings section stays empty. */
    private val settings: BackupSettings? = null,
) {
    @Inject
    constructor(dbs: CruxDatabases, clock: Clock, images: AreaImageStore, settings: BackupSettings) :
        this({ dbs.current() }, clock, images, settings)

    constructor(db: CruxDatabase, clock: Clock) : this({ db }, clock)

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Writes a backup archive of the chosen sections to [out], which the caller closes. */
    suspend fun export(sections: Set<BackupSection>, out: OutputStream, media: BackupMedia = BackupMedia()) {
        val file = snapshot(sections, media)
        val names = buildSet {
            file.climbs?.forEach { climb -> listOfNotNull(climb.photoFile, climb.videoFile).forEach(::add) }
            file.places?.forEach { place -> place.areas.mapNotNull { it.imageFile }.forEach(::add) }
        }
        withContext(Dispatchers.IO) {
            val buffered = out.buffered()
            val zip = ZipOutputStream(buffered)
            zip.putNextEntry(ZipEntry(ARCHIVE_JSON))
            zip.write(json.encodeToString(file).toByteArray())
            zip.closeEntry()
            // Photos and videos are compressed already; deflating them again only costs time.
            zip.setLevel(Deflater.NO_COMPRESSION)
            names.forEach { name ->
                val source = images?.file(name)?.takeIf { it.isFile } ?: return@forEach
                zip.putNextEntry(ZipEntry(MEDIA_DIR + name))
                source.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            zip.finish()
            buffered.flush()
        }
    }

    /**
     * What the chosen sections hold now, as a backup file. Media are named, not included:
     * [export] adds the files beside it.
     */
    suspend fun snapshot(sections: Set<BackupSection>, media: BackupMedia = BackupMedia()): BackupFile {
        val db = database()
        val exercises = db.exerciseDao().getAll()
        val byId = exercises.associateBy { it.id }
        val placeDao = db.placeDao()
        val places = placeDao.getPlaces()
        val placesById = places.associateBy { it.id }
        val allSections = placeDao.getAllSections()
        val sectionNames = allSections.associate { it.id to it.name }
        val allAreas = placeDao.getAllAreas()
        val allProblems = placeDao.getAllProblems()
        val finished = db.sessionDao().getFinished()
        val sessionStarts = finished.associate { it.id to it.startedAtMillis }

        fun photo(name: String?) = name?.takeIf { media.photos && images?.file(it)?.isFile == true }
        fun video(name: String?) = name?.takeIf { media.videos && images?.file(it)?.isFile == true }

        return BackupFile(
            exportedAt = Instant.now(clock).toString(),
            exercises = if (BackupSection.EXERCISES in sections) exercises.map { it.toDto() } else null,
            plans = if (BackupSection.PLANS in sections) {
                db.templateDao().getAll().map { plan ->
                    PlanDto(
                        name = plan.template.name,
                        description = plan.template.description,
                        blocks = plan.blocks.sortedBy { it.block.position }.map { block ->
                            PlanBlockDto(
                                name = block.block.name,
                                items = block.exercises.sortedBy { it.item.position }.map {
                                    PlanItemDto(
                                        exercise = (byId[it.item.exerciseId] ?: it.exercise).toDto(),
                                        sets = it.item.sets,
                                        reps = it.item.reps,
                                        seconds = it.item.seconds,
                                        loadKg = it.item.loadKg,
                                        restSeconds = it.item.restSeconds,
                                        repRestSeconds = it.item.repRestSeconds,
                                    )
                                },
                            )
                        },
                    )
                }
            } else {
                null
            },
            climbs = if (BackupSection.JOURNAL in sections) {
                val areas = allAreas.associateBy { it.id }
                val problems = allProblems.associateBy { it.id }
                val climbMedia = db.climbMediaDao().getAll()
                val photos = climbMedia.filter { it.kind == MediaKind.IMAGE }.associate { it.climbId to it.path }
                val videos = climbMedia.filter { it.kind == MediaKind.VIDEO }.associate { it.climbId to it.path }
                db.climbDao().getAll().map { climb ->
                    val place = climb.placeId?.let(placesById::get)
                    climb.toDto().copy(
                        place = place?.name ?: climb.place,
                        loggedPlace = climb.place,
                        placeType = place?.type,
                        area = climb.areaId?.let(areas::get)?.name,
                        section = climb.sectionId?.let(sectionNames::get),
                        problem = climb.problemId?.let(problems::get)?.name,
                        session = climb.sessionId?.let(sessionStarts::get),
                        sends = climb.sends,
                        photoFile = photo(photos[climb.id]),
                        videoFile = video(videos[climb.id]),
                    )
                }
            } else {
                null
            },
            sessions = if (BackupSection.SESSIONS in sections) {
                val plans = db.templateDao().getAll().associate { it.template.id to it.template.name }
                val items = db.sessionDao().getAllItems().groupBy { it.sessionId }
                val sets = db.sessionDao().getAllSets().groupBy { it.itemId }
                finished.map { session ->
                    val place = session.placeId?.let(placesById::get)
                    SessionDto(
                        name = session.name,
                        startedAt = session.startedAtMillis,
                        endedAt = session.endedAtMillis,
                        plan = session.templateId?.let(plans::get),
                        place = place?.name,
                        placeType = place?.type,
                        section = session.sectionId?.takeIf { place != null }?.let(sectionNames::get),
                        effort = session.effort,
                        notes = session.notes,
                        items = items[session.id].orEmpty().mapNotNull { item ->
                            val exercise = byId[item.exerciseId] ?: return@mapNotNull null
                            SessionItemDto(
                                exercise = exercise.toDto(),
                                block = item.blockName,
                                sets = item.sets,
                                reps = item.reps,
                                seconds = item.seconds,
                                loadKg = item.loadKg,
                                restSeconds = item.restSeconds,
                                repRestSeconds = item.repRestSeconds,
                                done = sets[item.id].orEmpty().map {
                                    SessionSetDto(it.setIndex, it.reps, it.seconds, it.loadKg, it.skipped, it.completedAtMillis)
                                },
                            )
                        },
                    )
                }
            } else {
                null
            },
            places = if (BackupSection.PLACES in sections) {
                val areas = allAreas.groupBy { it.placeId }
                val problems = allProblems.groupBy { it.placeId }
                val placeSections = allSections.groupBy { it.placeId }
                places.map { place ->
                    val parts = placeSections[place.id].orEmpty().sortedWith(compareBy({ it.position }, { it.id }))
                    val partNames = parts.associate { it.id to it.name }
                    val placeAreas = areas[place.id].orEmpty().sortedBy { it.position }
                    val areaNames = placeAreas.associate { it.id to it.name }
                    place.toDto(
                        areas = placeAreas.map { area ->
                            area.toDto().copy(imageFile = photo(area.imagePath), section = area.sectionId?.let(partNames::get))
                        },
                        problems = problems[place.id].orEmpty().map { it.toDto(it.areaId?.let(areaNames::get)) },
                    ).copy(sections = parts.map { SectionDto(it.type, it.name, it.boulderScale, it.routeScale, LocalScale.decode(it.localScale)) })
                }
            } else {
                null
            },
            bodyMeasurements = if (BackupSection.BODY in sections) db.bodyMeasurementDao().getAll().map { it.toDto() } else null,
            records = if (BackupSection.RECORDS in sections) {
                db.exerciseRecordDao().getAll().mapNotNull { r ->
                    byId[r.exerciseId]?.let { ex ->
                        RecordDto(ex.toDto(), LocalDate.ofEpochDay(r.dateEpochDay).toString(), r.reps, r.seconds, r.loadKg, r.notes, r.createdAtMillis)
                    }
                }
            } else {
                null
            },
            notes = if (BackupSection.NOTES in sections) {
                db.noteDao().getAll().map { NoteDto(it.text, it.createdAtMillis, it.updatedAtMillis, it.pinned, it.tag) }
            } else {
                null
            },
            settings = if (BackupSection.SETTINGS in sections) settings?.read() else null,
        )
    }

    /**
     * Reads a backup: a zip archive, or a version 1 JSON file. The archive's photos and videos
     * wait in the cache until the backup is imported or closed; [media] says which to unpack.
     */
    suspend fun open(input: InputStream, media: BackupMedia = BackupMedia()): OpenedBackup = withContext(Dispatchers.IO) {
        val stream = input.buffered()
        stream.mark(2)
        val isZip = stream.read() == 'P'.code && stream.read() == 'K'.code
        stream.reset()
        if (isZip) readArchive(stream, media) else OpenedBackup(parse(stream.readBytes().decodeToString()))
    }

    private fun readArchive(input: InputStream, media: BackupMedia): OpenedBackup {
        val dir = images?.takeIf { media.photos || media.videos }?.newStagingDir()
        try {
            var text: String? = null
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name.removePrefix(MEDIA_DIR)
                    val wanted = if (name.endsWith(AreaImageStore.VIDEO_EXT)) media.videos else media.photos
                    when {
                        entry.isDirectory -> Unit

                        entry.name == ARCHIVE_JSON -> text = zip.readBytes().decodeToString()

                        dir != null && wanted && entry.name.startsWith(MEDIA_DIR) && SAFE_NAME.matches(name) ->
                            File(dir, name).outputStream().use { zip.copyTo(it) }
                    }
                }
            }
            return OpenedBackup(parse(text ?: throw BackupFormatException(NOT_A_BACKUP)), dir)
        } catch (e: Exception) {
            dir?.deleteRecursively()
            throw e as? BackupFormatException ?: BackupFormatException(DAMAGED)
        }
    }

    /** Reads a backup's JSON, refusing anything that is not one or comes from a newer app. */
    fun parse(text: String): BackupFile {
        // The header first, so a newer file is told apart from a broken one even if its shape changed.
        val header = runCatching { json.decodeFromString<BackupHeader>(text) }.getOrNull()
        if (header?.format != BackupFile.FORMAT) throw BackupFormatException(NOT_A_BACKUP)
        if (header.version > BackupFile.VERSION) throw BackupFormatException(NEWER)
        return runCatching { json.decodeFromString<BackupFile>(text) }.getOrElse { throw BackupFormatException(DAMAGED) }
    }

    /**
     * Records in the chosen sections of [backup] that are already here: same name for exercises,
     * plans and places; same moment logged for everything else. The climber decides each one.
     */
    suspend fun conflicts(backup: OpenedBackup, sections: Set<BackupSection>): List<ImportConflict> {
        val here = snapshot(sections - BackupSection.SETTINGS, BackupMedia.NONE)
        val file = backup.file
        val describe = Describer(clock)

        fun <T> find(
            section: BackupSection,
            incoming: List<T>?,
            existing: List<T>?,
            key: (T) -> String,
            title: (T) -> String,
            text: (T) -> String,
            same: (T) -> Any,
        ): List<ImportConflict> {
            if (section !in sections || incoming == null) return emptyList()
            val mine = existing.orEmpty().groupBy(key)
            return incoming.mapNotNull { dto ->
                val old = mine[key(dto)]?.first() ?: return@mapNotNull null
                ImportConflict(ConflictKey(section, key(dto)), title(dto), text(old), text(dto), same(old) == same(dto))
            }.distinctBy { it.id }
        }

        return find(BackupSection.EXERCISES, file.exercises, here.exercises, ExerciseDto::key, { it.name }, describe::exercise, { it.comparable() }) +
            find(BackupSection.PLANS, file.plans, here.plans, PlanDto::key, { it.name }, describe::plan, { it.comparable() }) +
            find(BackupSection.PLACES, file.places, here.places, PlaceDto::key, { it.name }, describe::place, { it.comparable() }) +
            find(BackupSection.SESSIONS, file.sessions, here.sessions, SessionDto::key, { it.name }, describe::session, { it.comparable() }) +
            find(BackupSection.JOURNAL, file.climbs, here.climbs, ClimbDto::key, describe::climbTitle, describe::climb, { it.comparable() }) +
            find(BackupSection.BODY, file.bodyMeasurements, here.bodyMeasurements, MeasurementDto::key, { it.type.label }, describe::measurement, { it }) +
            find(BackupSection.RECORDS, file.records, here.records, RecordDto::key, {
                it.exercise.name
            }, describe::record, { it.copy(exercise = it.exercise.comparable()) }) +
            find(BackupSection.NOTES, file.notes, here.notes, NoteDto::key, describe::noteTitle, describe::note, { it })
    }

    suspend fun import(file: BackupFile, sections: Set<BackupSection>): ImportResult = import(OpenedBackup(file), sections)

    /**
     * Brings in what [backup] holds for the chosen sections. Records already here go as
     * [decisions] says, and are skipped where it says nothing. Nothing else is touched, except
     * settings, which replace the current ones.
     */
    suspend fun import(
        backup: OpenedBackup,
        sections: Set<BackupSection>,
        decisions: Map<ConflictKey, Resolution> = emptyMap(),
        media: BackupMedia = BackupMedia(),
    ): ImportResult {
        val db = database()
        val importer = Importer(db, backup, sections, decisions, media)
        val result = try {
            db.withTransaction { importer.run() }
        } catch (e: Throwable) {
            importer.newFiles.forEach { images?.delete(it) }
            throw e
        }
        // Only once the data no longer points at them.
        importer.oldFiles.forEach { images?.delete(it) }
        val settingsFromFile = backup.file.settings?.takeIf { BackupSection.SETTINGS in sections }
        if (settingsFromFile != null && settings != null) {
            settings.write(settingsFromFile)
            return result.copy(added = result.added + (BackupSection.SETTINGS to 1))
        }
        return result
    }

    /** One import run: what it matched and added so far, so later sections link to it. */
    private inner class Importer(
        private val db: CruxDatabase,
        private val backup: OpenedBackup,
        private val sections: Set<BackupSection>,
        private val decisions: Map<ConflictKey, Resolution>,
        private val media: BackupMedia,
    ) {
        private val file = backup.file
        private val now = clock.millis()
        private val added = mutableMapOf<BackupSection, Int>()
        private val skipped = mutableMapOf<BackupSection, Int>()
        private val replaced = mutableMapOf<BackupSection, Int>()
        private val copied = mutableMapOf<BackupSection, Int>()
        private var unreadable = 0

        /** Media files this import wrote, and files of media it replaced. */
        val newFiles = mutableListOf<String>()
        val oldFiles = mutableListOf<String>()

        // Exercises here by name, including those this import adds.
        private val exercises = mutableMapOf<String, ExerciseEntity>()

        // What each record in the file became here: the one it matched, or the one imported.
        private val exerciseFromFile = mutableMapOf<String, Long>()
        private val planFromFile = mutableMapOf<String, Long>()
        private val placeFromFile = mutableMapOf<String, Long>()
        private val sessionFromFile = mutableMapOf<Long, Long>()

        suspend fun run(): ImportResult {
            db.exerciseDao().getAll().forEach { exercises.putIfAbsent(nameKey(it.name), it) }
            if (BackupSection.EXERCISES in sections) importExercises()
            if (BackupSection.PLANS in sections) importPlans()
            // Places, plans and sessions go in before the journal so climbs can link to them.
            if (BackupSection.PLACES in sections) importPlaces()
            if (BackupSection.SESSIONS in sections) importSessions()
            if (BackupSection.JOURNAL in sections) importClimbs()
            if (BackupSection.BODY in sections) importBody()
            if (BackupSection.RECORDS in sections) importRecords()
            if (BackupSection.NOTES in sections) importNotes()
            return ImportResult(added, skipped, replaced, copied, unreadable)
        }

        private fun added(section: BackupSection) = added.merge(section, 1, Int::plus)

        /** What the climber chose for a record that is already here; skip when they weren't asked. */
        private fun choice(section: BackupSection, key: String): Resolution {
            val choice = decisions[ConflictKey(section, key)] ?: Resolution.SKIP
            val counter = when (choice) {
                Resolution.SKIP -> skipped
                Resolution.REPLACE -> replaced
                Resolution.KEEP_BOTH -> copied
            }
            counter.merge(section, 1, Int::plus)
            return choice
        }

        private suspend fun insertExercise(dto: ExerciseDto, name: String): Long {
            val entity = ExerciseEntity(
                name = name,
                category = dto.category,
                metric = dto.metric,
                notes = dto.notes,
                createdAtMillis = dto.createdAt ?: now,
            ).withDefaults(dto.defaults?.target(), dto.defaults?.prepSeconds)
            val id = db.exerciseDao().insert(entity)
            exercises.putIfAbsent(nameKey(name), entity.copy(id = id))
            return id
        }

        /** The exercise a plan, session or record means: as imported, as already here, or added now. */
        private suspend fun exerciseId(dto: ExerciseDto): Long {
            val key = dto.key()
            return exerciseFromFile[key] ?: exercises[key]?.id ?: insertExercise(dto, dto.name.trim()).also { exerciseFromFile[key] = it }
        }

        private suspend fun importExercises() {
            file.exercises?.forEach { dto ->
                val key = dto.key()
                val existing = exercises[key]
                exerciseFromFile[key] = when {
                    existing == null -> insertExercise(dto, dto.name.trim()).also { added(BackupSection.EXERCISES) }

                    else -> when (choice(BackupSection.EXERCISES, key)) {
                        Resolution.SKIP -> existing.id

                        Resolution.REPLACE -> existing.id.also {
                            db.exerciseDao().update(
                                existing.copy(name = dto.name.trim(), category = dto.category, metric = dto.metric, notes = dto.notes)
                                    .withDefaults(dto.defaults?.target(), dto.defaults?.prepSeconds),
                            )
                        }

                        Resolution.KEEP_BOTH -> insertExercise(dto, uniqueName(dto.name.trim(), exercises.keys))
                    }
                }
            }
        }

        private suspend fun importPlans() {
            val dao = db.templateDao()
            val plans = dao.getAll().groupBy { nameKey(it.template.name) }.mapValues { it.value.first().template }
            val names = plans.keys.toMutableSet()

            suspend fun insertBlocks(templateId: Long, plan: PlanDto) = plan.blocks.forEachIndexed { blockPosition, block ->
                val blockId = dao.insertBlock(TemplateBlockEntity(templateId = templateId, position = blockPosition, name = block.name))
                dao.insertExercises(
                    block.items.mapIndexed { position, item ->
                        TemplateExerciseEntity(
                            blockId = blockId,
                            // A plan brings the exercises it needs, even when the library is not imported.
                            exerciseId = exerciseId(item.exercise),
                            position = position,
                            sets = item.sets,
                            reps = item.reps,
                            seconds = item.seconds,
                            loadKg = item.loadKg,
                            restSeconds = item.restSeconds,
                            repRestSeconds = item.repRestSeconds,
                        )
                    },
                )
            }

            suspend fun insertPlan(plan: PlanDto, name: String): Long {
                val id = dao.insertTemplate(WorkoutTemplateEntity(name = name, description = plan.description, position = dao.nextPosition()))
                insertBlocks(id, plan)
                names += nameKey(name)
                return id
            }

            file.plans?.forEach { plan ->
                val key = plan.key()
                val existing = plans[key]
                planFromFile[key] = when {
                    existing == null -> insertPlan(plan, plan.name.trim()).also { added(BackupSection.PLANS) }

                    else -> when (choice(BackupSection.PLANS, key)) {
                        Resolution.SKIP -> existing.id

                        Resolution.REPLACE -> existing.id.also {
                            dao.updateTemplate(existing.copy(name = plan.name.trim(), description = plan.description))
                            dao.deleteBlocks(existing.id)
                            insertBlocks(existing.id, plan)
                        }

                        Resolution.KEEP_BOTH -> insertPlan(plan, uniqueName(plan.name.trim(), names))
                    }
                }
            }
        }

        private suspend fun importPlaces() {
            val dao = db.placeDao()
            val places = dao.getPlaces()
            val byKey = places.groupBy { placeKey(it.type, it.name) }.mapValues { it.value.first() }
            val names = places.map { nameKey(it.name) }.toMutableSet()
            file.places?.forEach { dto ->
                val key = dto.key()
                val existing = byKey[key]
                placeFromFile[key] = when {
                    existing == null -> insertPlace(dto, dto.name.trim()).also {
                        names += nameKey(dto.name)
                        added(BackupSection.PLACES)
                    }

                    else -> when (choice(BackupSection.PLACES, key)) {
                        Resolution.SKIP -> existing.id

                        // The backup's details; its facilities, walls and problems update those with the same name or join them.
                        Resolution.REPLACE -> existing.id.also {
                            dao.updatePlace(
                                existing.copy(
                                    name = dto.name.trim(),
                                    location = dto.location,
                                    defaultAngle = dto.defaultAngle,
                                    notes = dto.notes,
                                    favourite = dto.favourite,
                                    latitude = dto.latitude,
                                    longitude = dto.longitude,
                                    address = dto.address,
                                    extraTypes = dto.extraTypeNames(),
                                ),
                            )
                            writeContents(existing.id, dto)
                        }

                        Resolution.KEEP_BOTH -> uniqueName(dto.name.trim(), names).let { name ->
                            names += nameKey(name)
                            insertPlace(dto, name)
                        }
                    }
                }
            }
        }

        private suspend fun insertPlace(dto: PlaceDto, name: String): Long {
            val id = db.placeDao().insertPlace(
                PlaceEntity(
                    name = name,
                    type = dto.type,
                    location = dto.location,
                    defaultAngle = dto.defaultAngle,
                    notes = dto.notes,
                    createdAtMillis = dto.createdAt ?: now,
                    favourite = dto.favourite,
                    latitude = dto.latitude,
                    longitude = dto.longitude,
                    address = dto.address,
                    extraTypes = dto.extraTypeNames(),
                ),
            )
            writeContents(id, dto)
            return id
        }

        /** Writes a place's facilities, walls and problems: updating those with the same name, adding the rest. */
        private suspend fun writeContents(placeId: Long, dto: PlaceDto) {
            val dao = db.placeDao()
            val haveSections = dao.getSections(placeId)
            var sectionPosition = (haveSections.maxOfOrNull { it.position } ?: -1) + 1
            // Sections: as backed up, or one per kind for older backups.
            val sectionDtos = dto.sections.ifEmpty {
                (listOf(dto.type) + dto.extraTypes.filter { it != dto.type }).distinct().map { SectionDto(it, it.label) }
            }
            // Grades: each part's own, or the whole place's from backups made before schema 20.
            val oldGrades = dto.sections.none { it.boulderScale != null || it.routeScale != null }
            val sectionIds = sectionDtos.map { section ->
                val boulder = if (oldGrades) dto.boulderScale else section.boulderScale
                val route = (if (oldGrades) dto.routeScale else section.routeScale).takeIf { section.type != PlaceType.BOARD }
                val local = (if (oldGrades) dto.localScale else section.localScale).takeIf { boulder?.isLocal == true || route?.isLocal == true }
                val match = haveSections.firstOrNull { it.name.equals(section.name, ignoreCase = true) }
                section to if (match != null) {
                    dao.updateSection(match.copy(type = section.type, boulderScale = boulder, routeScale = route, localScale = local?.encode()))
                    match.id
                } else {
                    dao.insertSection(
                        SectionEntity(
                            placeId = placeId,
                            type = section.type,
                            name = section.name,
                            position = sectionPosition++,
                            boulderScale = boulder,
                            routeScale = route,
                            localScale = local?.encode(),
                        ),
                    )
                }
            }
            fun sectionFor(name: String?, type: PlaceType?): Long? =
                sectionIds.firstOrNull { (section, _) -> name != null && section.name.equals(name, ignoreCase = true) }?.second
                    ?: sectionIds.firstOrNull { (section, _) -> section.type == (type ?: dto.type) }?.second
                    ?: sectionIds.firstOrNull()?.second

            val haveAreas = dao.getAllAreas().filter { it.placeId == placeId }
            var areaPosition = dao.nextAreaPosition(placeId)
            val areaIds = haveAreas.associate { nameKey(it.name) to it.id }.toMutableMap()
            dto.areas.forEach { area ->
                val image = importPhoto(area.image, area.imageFile)
                val match = haveAreas.firstOrNull { it.name.equals(area.name, ignoreCase = true) }
                val entity = AreaEntity(
                    placeId = placeId,
                    name = area.name,
                    angle = area.angle,
                    resetEpochDay = area.resetDate?.let { LocalDate.parse(it).toEpochDay() },
                    position = match?.position ?: areaPosition++,
                    imagePath = image ?: match?.imagePath,
                    type = area.type?.takeIf { it != dto.type },
                    sectionId = sectionFor(area.section, area.type),
                )
                areaIds[nameKey(area.name)] = if (match != null) {
                    if (image != null) match.imagePath?.let(oldFiles::add)
                    dao.updateArea(entity.copy(id = match.id))
                    match.id
                } else {
                    dao.insertArea(entity)
                }
            }

            // A problem is the same one when its name, discipline and set date match.
            val haveProblems = dao.getAllProblems().filter { it.placeId == placeId }.toMutableList()
            dto.problems.forEach { problem ->
                val index = gradeIndex(problem.gradeScale, problem.grade, problem.gradeIndex) ?: run {
                    unreadable++
                    return@forEach
                }
                val setDay = problem.setDate?.let { LocalDate.parse(it).toEpochDay() }
                val match = haveProblems.firstOrNull {
                    it.name.equals(problem.name, ignoreCase = true) && it.discipline == problem.discipline && it.setEpochDay == setDay
                }?.also { haveProblems.remove(it) }
                val entity = ProblemEntity(
                    placeId = placeId,
                    areaId = problem.area?.let { areaIds[nameKey(it)] },
                    name = problem.name,
                    discipline = problem.discipline,
                    gradeScale = problem.gradeScale,
                    gradeIndex = index,
                    tape = problem.tape,
                    setEpochDay = setDay,
                    retired = problem.retired,
                    notes = problem.notes,
                    createdAtMillis = problem.createdAt ?: match?.createdAtMillis ?: now,
                    gradeLabel = problem.grade.takeIf { problem.gradeScale.isLocal },
                    gradeColour = problem.gradeColour,
                )
                if (match != null) dao.updateProblem(entity.copy(id = match.id)) else dao.insertProblem(entity)
            }
        }

        // Saved places, plans and sessions as they stand once places, plans and sessions are in.
        private var links: Links? = null

        private suspend fun links(): Links = links ?: run {
            val dao = db.placeDao()
            Links(
                places = dao.getPlaces(),
                sections = dao.getAllSections().groupBy { it.placeId },
                areas = dao.getAllAreas().groupBy { it.placeId },
                problems = dao.getAllProblems().filter { it.placeId != null }.groupBy { it.placeId!! },
                plans = db.templateDao().getAll().groupBy { nameKey(it.template.name) }.mapValues { it.value.first().template.id },
            ).also { links = it }
        }

        private fun Links.place(type: PlaceType?, name: String?): PlaceEntity? {
            if (type == null || name == null) return null
            val key = placeKey(type, name)
            return placeFromFile[key]?.let { id -> places.firstOrNull { it.id == id } } ?: places.firstOrNull { placeKey(it.type, it.name) == key }
        }

        private fun Links.plan(name: String?): Long? = name?.let { planFromFile[nameKey(it)] ?: plans[nameKey(it)] }

        private suspend fun importSessions() {
            val dao = db.sessionDao()
            val have = dao.getFinished().groupBy { it.startedAtMillis }.mapValues { it.value.first() }
            val links = links()

            suspend fun insertItems(sessionId: Long, dto: SessionDto) = dto.items.forEachIndexed { position, item ->
                val itemId = dao.insertItem(
                    SessionItemEntity(
                        sessionId = sessionId,
                        exerciseId = exerciseId(item.exercise),
                        blockName = item.block,
                        position = position,
                        sets = item.sets,
                        reps = item.reps,
                        seconds = item.seconds,
                        loadKg = item.loadKg,
                        restSeconds = item.restSeconds,
                        repRestSeconds = item.repRestSeconds,
                    ),
                )
                item.done.forEach {
                    dao.insertSet(
                        SessionSetEntity(
                            itemId = itemId,
                            setIndex = it.set,
                            reps = it.reps,
                            seconds = it.seconds,
                            loadKg = it.loadKg,
                            skipped = it.skipped,
                            completedAtMillis = it.completedAt,
                        ),
                    )
                }
            }

            suspend fun insertSession(entity: SessionEntity, dto: SessionDto): Long = dao.insertSession(entity).also { insertItems(it, dto) }

            file.sessions?.forEach { dto ->
                val place = links.place(dto.placeType, dto.place)
                val entity = SessionEntity(
                    name = dto.name,
                    templateId = links.plan(dto.plan),
                    placeId = place?.id,
                    sectionId = place?.let { p -> links.sections[p.id].orEmpty().firstOrNull { it.name.equals(dto.section, ignoreCase = true) }?.id },
                    startedAtMillis = dto.startedAt,
                    endedAtMillis = dto.endedAt,
                    status = SessionStatus.FINISHED,
                    effort = dto.effort,
                    notes = dto.notes,
                )
                val existing = have[dto.startedAt]
                sessionFromFile[dto.startedAt] = when {
                    existing == null -> insertSession(entity, dto).also { added(BackupSection.SESSIONS) }

                    else -> when (choice(BackupSection.SESSIONS, dto.key())) {
                        Resolution.SKIP -> existing.id

                        Resolution.REPLACE -> existing.id.also {
                            dao.updateSession(entity.copy(id = existing.id))
                            dao.deleteItems(existing.id)
                            insertItems(existing.id, dto)
                        }

                        Resolution.KEEP_BOTH -> insertSession(entity, dto)
                    }
                }
            }
        }

        private suspend fun importClimbs() {
            // Climbs relink by name to whichever saved places, walls and problems exist now.
            val links = links()
            val dao = db.climbDao()
            val have = dao.getAll().groupBy { it.createdAtMillis }.mapValues { it.value.first() }
            val sessionsHere = db.sessionDao().getFinished().associate { it.startedAtMillis to it.id }
            file.climbs?.forEach { dto ->
                val index = gradeIndex(dto.gradeScale, dto.grade, dto.gradeIndex) ?: run {
                    unreadable++
                    return@forEach
                }
                val place = links.place(dto.placeType, dto.place)
                val sessionId = dto.session?.let { sessionFromFile[it] ?: sessionsHere[it] }
                val entity = ClimbEntity(
                    discipline = dto.discipline,
                    gradeScale = dto.gradeScale,
                    gradeIndex = index,
                    style = dto.style,
                    attempts = dto.attempts,
                    sends = (dto.sends ?: if (dto.style.isSend) 1 else 0).coerceIn(0, dto.attempts),
                    venue = dto.venue,
                    dateEpochDay = LocalDate.parse(dto.date).toEpochDay(),
                    createdAtMillis = dto.loggedAt,
                    name = dto.name,
                    place = dto.loggedPlace ?: dto.place,
                    notes = dto.notes,
                    placeId = place?.id,
                    sectionId = place?.let { p ->
                        val here = links.sections[p.id].orEmpty()
                        here.firstOrNull { dto.section != null && it.name.equals(dto.section, ignoreCase = true) }?.id
                            ?: here.firstOrNull { it.type.venue == dto.venue }?.id
                    },
                    areaId = dto.area?.let { name -> links.areas[place?.id].orEmpty().firstOrNull { it.name.equals(name, ignoreCase = true) }?.id },
                    // Its climb at that place by name; logs without one get theirs once all are in.
                    problemId = (dto.problem ?: dto.name)?.let { name ->
                        place?.let {
                            links.problems[it.id]
                        }.orEmpty().firstOrNull { it.name.equals(name, ignoreCase = true) && it.discipline == dto.discipline }?.id
                    },
                    angle = dto.angle,
                    effort = dto.effort,
                    gradeLabel = dto.grade.takeIf { dto.gradeScale.isLocal },
                    gradeColour = dto.gradeColour,
                    sessionId = sessionId,
                )
                val existing = have[dto.loggedAt]
                when {
                    existing == null -> {
                        attachMedia(dao.insert(entity), dto)
                        added(BackupSection.JOURNAL)
                    }

                    else -> when (choice(BackupSection.JOURNAL, dto.key())) {
                        // A skipped climb still joins its session if it wasn't in one.
                        Resolution.SKIP -> if (existing.sessionId == null && sessionId != null) dao.update(existing.copy(sessionId = sessionId))

                        Resolution.REPLACE -> {
                            dao.update(entity.copy(id = existing.id, sessionId = sessionId ?: existing.sessionId))
                            attachMedia(existing.id, dto)
                        }

                        Resolution.KEEP_BOTH -> attachMedia(dao.insert(entity), dto)
                    }
                }
            }
            // Every log belongs to a climb: the rest join one by name and place, or start one.
            com.hardtekpt.crux.data.local.ClimbLinks.linkUnlinked(db.openHelper.writableDatabase, clock.millis())
        }

        /** The climb's photo and video from the backup, in place of any it has. Without them it keeps its own. */
        private suspend fun attachMedia(climbId: Long, dto: ClimbDto) {
            val dao = db.climbMediaDao()
            suspend fun attach(kind: MediaKind, path: String?) {
                path ?: return
                dao.get(climbId, kind)?.let {
                    oldFiles += it.path
                    dao.delete(climbId, kind)
                }
                dao.insert(ClimbMediaEntity(climbId = climbId, kind = kind, path = path, createdAtMillis = now))
            }
            attach(MediaKind.IMAGE, importPhoto(dto.image, dto.photoFile))
            attach(MediaKind.VIDEO, importVideo(dto.videoFile))
        }

        /** A photo from the archive, or from a version 1 file's base64; null when there's none or photos are off. */
        private suspend fun importPhoto(base64: String?, name: String?): String? {
            val store = images?.takeIf { media.photos } ?: return null
            val bytes = backup.media(name)?.readBytes()
                ?: base64?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
                ?: return null
            return store.importBytes(bytes)?.also(newFiles::add)
        }

        private suspend fun importVideo(name: String?): String? {
            val store = images?.takeIf { media.videos } ?: return null
            val source = backup.media(name) ?: return null
            return store.importVideoFile(source).also(newFiles::add)
        }

        private suspend fun importBody() {
            val dao = db.bodyMeasurementDao()
            val have = dao.getAll().groupBy { it.toDto().key() }.mapValues { it.value.first() }
            file.bodyMeasurements?.forEach { dto ->
                val entity = BodyMeasurementEntity(
                    type = dto.type,
                    value = dto.value,
                    dateEpochDay = LocalDate.parse(dto.date).toEpochDay(),
                    createdAtMillis = dto.loggedAt,
                )
                val existing = have[dto.key()]
                when {
                    existing == null -> dao.insert(entity).also { added(BackupSection.BODY) }

                    else -> when (choice(BackupSection.BODY, dto.key())) {
                        Resolution.SKIP -> Unit
                        Resolution.REPLACE -> dao.update(entity.copy(id = existing.id))
                        Resolution.KEEP_BOTH -> dao.insert(entity)
                    }
                }
            }
        }

        private suspend fun importRecords() {
            val dao = db.exerciseRecordDao()
            val names = exercises.values.associate { it.id to it.name }
            val have = dao.getAll().groupBy { "${nameKey(names[it.exerciseId].orEmpty())}:${it.createdAtMillis}" }.mapValues { it.value.first() }
            file.records?.forEach { dto ->
                // The exercise comes along if the library doesn't have it yet.
                val entity = ExerciseRecordEntity(
                    exerciseId = exerciseId(dto.exercise),
                    dateEpochDay = LocalDate.parse(dto.date).toEpochDay(),
                    reps = dto.reps,
                    seconds = dto.seconds,
                    loadKg = dto.loadKg,
                    notes = dto.notes,
                    createdAtMillis = dto.loggedAt,
                )
                val existing = have[dto.key()]
                when {
                    existing == null -> dao.insert(entity).also { added(BackupSection.RECORDS) }

                    else -> when (choice(BackupSection.RECORDS, dto.key())) {
                        Resolution.SKIP -> Unit
                        Resolution.REPLACE -> dao.update(entity.copy(id = existing.id))
                        Resolution.KEEP_BOTH -> dao.insert(entity)
                    }
                }
            }
        }

        private suspend fun importNotes() {
            val dao = db.noteDao()
            val have = dao.getAll().groupBy { it.createdAtMillis }.mapValues { it.value.first() }
            file.notes?.forEach { dto ->
                val entity = NoteEntity(text = dto.text, createdAtMillis = dto.createdAt, updatedAtMillis = dto.updatedAt, pinned = dto.pinned, tag = dto.tag)
                val existing = have[dto.createdAt]
                when {
                    existing == null -> dao.insert(entity).also { added(BackupSection.NOTES) }

                    else -> when (choice(BackupSection.NOTES, dto.key())) {
                        Resolution.SKIP -> Unit
                        Resolution.REPLACE -> dao.update(entity.copy(id = existing.id))
                        Resolution.KEEP_BOTH -> dao.insert(entity)
                    }
                }
            }
        }
    }

    private class Links(
        val places: List<PlaceEntity>,
        val sections: Map<Long, List<SectionEntity>>,
        val areas: Map<Long, List<AreaEntity>>,
        val problems: Map<Long, List<ProblemEntity>>,
        val plans: Map<String, Long>,
    )

    private companion object {
        const val ARCHIVE_JSON = "backup.json"
        const val MEDIA_DIR = "media/"

        /** Media names inside an archive: a plain file name, nothing that climbs out of the folder. */
        val SAFE_NAME = Regex("[A-Za-z0-9_-][A-Za-z0-9._-]*")

        const val NOT_A_BACKUP = "This file is not a Crux backup."
        const val NEWER = "This backup comes from a newer version of Crux. Update the app to import it."
        const val DAMAGED = "This backup can't be read. It may be damaged or incomplete."
    }
}

// How a record in a backup is told apart from the others in its section. Names for things
// the climber names; for things logged, the moment they were logged.

private fun nameKey(name: String) = name.trim().lowercase()

private fun placeKey(type: PlaceType, name: String) = "${type.name}:${nameKey(name)}"

private fun ExerciseDto.key() = nameKey(name)

private fun PlanDto.key() = nameKey(name)

private fun PlaceDto.key() = placeKey(type, name)

private fun SessionDto.key() = startedAt.toString()

private fun ClimbDto.key() = loggedAt.toString()

private fun MeasurementDto.key() = "${type.name}:$date:$loggedAt"

private fun RecordDto.key() = "${exercise.key()}:$loggedAt"

private fun NoteDto.key() = createdAt.toString()

// What a record looks like with the parts that differ between installs left out, to tell
// whether a duplicate is the very same.

private fun ExerciseDto.comparable() = copy(name = name.trim(), createdAt = null)

private fun PlanDto.comparable() = copy(blocks = blocks.map { block -> block.copy(items = block.items.map { it.copy(exercise = it.exercise.comparable()) }) })

private fun PlaceDto.comparable() = copy(
    createdAt = null,
    areas = areas.map { it.copy(image = null, imageFile = null) },
    problems = problems.map { it.copy(createdAt = null) },
)

private fun SessionDto.comparable() = copy(items = items.map { it.copy(exercise = it.exercise.comparable()) })

private fun ClimbDto.comparable() = copy(image = null, photoFile = null, videoFile = null, loggedPlace = null)

private fun uniqueName(name: String, taken: Collection<String>): String = generateSequence(2) { it + 1 }.map { "$name ($it)" }.first { nameKey(it) !in taken }

private fun gradeIndex(scale: GradeScale, grade: String, localIndex: Int?): Int? =
    if (scale.isLocal) localIndex ?: 0 else scale.grades.indexOf(grade).takeIf { it >= 0 }

private fun ExerciseDefaultsDto.target() = ExerciseTarget(sets, reps, seconds, loadKg, restSeconds, repRestSeconds)

private fun PlaceDto.extraTypeNames() = extraTypes.filter { it != type }.joinToString(",") { it.name }

/** One line about a record, for the climber to compare what's here with what's in the backup. */
private class Describer(private val clock: Clock) {
    private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    private fun day(iso: String) = runCatching { LocalDate.parse(iso).format(dateFormat) }.getOrDefault(iso)

    private fun day(millis: Long) = Instant.ofEpochMilli(millis).atZone(clock.zone).toLocalDate().format(dateFormat)

    private fun String.snippet() = lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.let { if (it.length > 40) it.take(39) + "…" else it }

    private fun Double.plain() = if (this % 1.0 == 0.0) toLong().toString() else toString()

    private fun parts(vararg parts: String?) = parts.filterNot { it.isNullOrBlank() }.joinToString(" · ")

    fun exercise(dto: ExerciseDto) = parts(
        dto.category.label,
        dto.metric.label,
        dto.defaults?.let { "${it.sets} × ${it.reps}" },
        dto.notes?.snippet(),
    )

    fun plan(dto: PlanDto) = parts(
        "${dto.blocks.size} blocks",
        "${dto.blocks.sumOf { it.items.size }} exercises",
        dto.description.snippet(),
    )

    fun place(dto: PlaceDto) = parts(
        dto.type.label,
        dto.location,
        "${dto.areas.size} walls",
        "${dto.problems.size} climbs",
        dto.notes?.snippet(),
    )

    fun session(dto: SessionDto) = parts(
        day(dto.startedAt),
        dto.endedAt?.let { "${(it - dto.startedAt) / 60_000} min" },
        "${dto.items.sumOf { item -> item.done.count { !it.skipped } }} sets",
        dto.place,
        dto.effort?.let { "effort $it" },
        dto.notes?.snippet(),
    )

    fun climbTitle(dto: ClimbDto) = dto.name ?: "${dto.discipline.label} on ${day(dto.date)}"

    fun climb(dto: ClimbDto) = parts(
        dto.grade,
        dto.style.label,
        dto.attempts.takeIf { it > 1 }?.let { "$it tries" },
        day(dto.date),
        dto.place,
        dto.notes?.snippet(),
    )

    fun measurement(dto: MeasurementDto) = parts("${dto.value.plain()} ${dto.type.unit}", day(dto.date))

    fun record(dto: RecordDto) = parts(
        day(dto.date),
        dto.reps?.let { "$it reps" },
        dto.seconds?.let { "$it s" },
        dto.loadKg?.let { "${it.plain()} kg" },
        dto.notes?.snippet(),
    )

    fun noteTitle(dto: NoteDto) = dto.text.snippet() ?: "Note"

    fun note(dto: NoteDto) = parts(
        dto.text.trim().lines().drop(1).joinToString(" ").snippet(),
        dto.tag?.let { "#$it" },
        "pinned".takeIf { dto.pinned },
        "edited ${day(dto.updatedAt)}",
    )
}

private fun ExerciseEntity.toDto() = ExerciseDto(
    name,
    category,
    metric,
    notes,
    defaults?.let { ExerciseDefaultsDto(it.sets, it.reps, it.seconds, it.loadKg, it.restSeconds, it.repRestSeconds, prepSeconds) },
    createdAtMillis,
)

private fun ClimbEntity.toDto() = ClimbDto(
    discipline = discipline,
    gradeScale = gradeScale,
    grade = gradeLabel(gradeScale, gradeIndex, gradeLabel),
    style = style,
    attempts = attempts,
    venue = venue,
    date = LocalDate.ofEpochDay(dateEpochDay).toString(),
    loggedAt = createdAtMillis,
    name = name,
    place = place,
    notes = notes,
    angle = angle,
    effort = effort,
    gradeIndex = gradeIndex.takeIf { gradeScale.isLocal },
    gradeColour = gradeColour,
)

private fun PlaceEntity.toDto(areas: List<AreaDto>, problems: List<ProblemDto>) = PlaceDto(
    name = name,
    type = type,
    location = location,
    boulderScale = boulderScale,
    routeScale = routeScale,
    defaultAngle = defaultAngle,
    notes = notes,
    areas = areas,
    problems = problems,
    localScale = LocalScale.decode(localScale),
    favourite = favourite,
    latitude = latitude,
    longitude = longitude,
    address = address,
    extraTypes = PlaceEntity.parseTypes(extraTypes),
    createdAt = createdAtMillis,
)

private fun AreaEntity.toDto() = AreaDto(
    name = name,
    type = type,
    angle = angle,
    resetDate = resetEpochDay?.let { LocalDate.ofEpochDay(it).toString() },
)

private fun ProblemEntity.toDto(area: String?) = ProblemDto(
    name = name,
    discipline = discipline,
    gradeScale = gradeScale,
    grade = gradeLabel(gradeScale, gradeIndex, gradeLabel),
    area = area,
    tape = tape,
    setDate = setEpochDay?.let { LocalDate.ofEpochDay(it).toString() },
    retired = retired,
    notes = notes,
    gradeIndex = gradeIndex.takeIf { gradeScale.isLocal },
    gradeColour = gradeColour,
    createdAt = createdAtMillis,
)

private fun BodyMeasurementEntity.toDto() = MeasurementDto(
    type = type,
    value = value,
    date = LocalDate.ofEpochDay(dateEpochDay).toString(),
    loggedAt = createdAtMillis,
)
