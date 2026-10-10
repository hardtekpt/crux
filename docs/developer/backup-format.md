# Backup format

[`BackupRepository`](../../app/src/main/java/com/hardtekpt/crux/data/backup/BackupRepository.kt) writes
and reads backups; the format itself is in [`Backup.kt`](../../app/src/main/java/com/hardtekpt/crux/data/backup/Backup.kt).
Settings go through [`BackupSettings`](../../app/src/main/java/com/hardtekpt/crux/data/backup/BackupSettings.kt).

## Principles

- **Self-describing**: `format` is always `"crux-backup"`, and `version` is the format version
  (currently **2**).
- **No database ids.** Records refer to each other by name (a climb names its place, area and
  problem; a plan item embeds its exercise; a climb names its session by start time), so a file
  imports cleanly into any install.
- **Sections are optional.** A section that wasn't exported is absent (`null`), which is different
  from present but empty.
- **The climber decides duplicates.** Before anything is written, `conflicts()` lists the records
  that are already here, and the import applies one `Resolution` per record: `SKIP`, `REPLACE` or
  `KEEP_BOTH`. A record without a decision is skipped.
- **Forward compatible within a version.** New fields get defaults, so older files still parse.
  `BackupRepositoryTest` imports real v0.1.0 and v0.2.0 files from `app/src/test/resources/backups/`
  to make sure of it. The header (`format`, `version`) is read first, so a file with a *higher*
  `version` than the app knows is refused with "update the app" even if the rest no longer parses.

## Container

| Version | File | Media |
| --- | --- | --- |
| 2 (0.3.0 on) | A zip archive: `backup.json` first, then `media/<file name>` | Files, stored uncompressed; JSON fields name them (`photoFile`, `videoFile`, `imageFile`) |
| 1 (0.1.0, 0.2.0) | The JSON on its own | Base64 JPEG in `image` fields; no videos |

`open()` tells the two apart by the zip magic bytes, so both import. An archive's media are unpacked
to `cache/backup-import/` and removed when the `OpenedBackup` is closed. Only plain file names are
unpacked; anything else in the archive is ignored. Export streams media into the archive, so a
backup never sits in memory whole.

## Top level

```json
{
  "format": "crux-backup",
  "version": 2,
  "exportedAt": "2026-10-05T12:00:00Z",
  "exercises": [ … ],
  "plans": [ … ],
  "climbs": [ … ],
  "places": [ … ],
  "bodyMeasurements": [ … ],
  "records": [ … ],
  "notes": [ … ],
  "sessions": [ … ],
  "settings": { … }
}
```

| Key | Section in the app | Element | Same record when |
| --- | --- | --- | --- |
| `exercises` | Exercise list | `ExerciseDto`: `name`, `category`, `metric`, `notes`, `defaults` (`sets`, `reps`, `seconds`, `loadKg`, `restSeconds`, `repRestSeconds`, `prepSeconds`), `createdAt` | Same name |
| `plans` | Plan list | `PlanDto`: `name`, `description`, `blocks[]` → `name`, `items[]` → embedded `exercise` + target (`sets`, `reps`, `seconds`, `loadKg`, `restSeconds`, `repRestSeconds`) | Same name |
| `climbs` | Journal | `ClimbDto`: `discipline`, `gradeScale`, `grade` (label), `style`, `attempts`, `venue`, `date`, `loggedAt`, optional `name`, `notes`, `place` + `placeType`, `loggedPlace` (as typed), `section`, `area`, `problem`, `angle`, `effort`, `gradeIndex`/`gradeColour` (local grades), `session` (its start), `sends` (older files: one for a send), `photoFile`, `videoFile` (v1: `image`). On import a log joins its climb by `problem` (or `name`) at its place, or gets one as on a phone (`ClimbLinks`) | Same `loggedAt` |
| `sessions` | Session history | `SessionDto`: `name`, `startedAt`, `endedAt`, `plan`, `place` + `placeType`, `section`, `effort`, `notes`, `items[]` → embedded `exercise`, `block`, target, `done[]` (`set`, `reps`, `seconds`, `loadKg`, `skipped`, `completedAt`). Finished sessions only. | Same `startedAt` |
| `places` | Places | `PlaceDto`: `name`, `type`, `extraTypes`, `sections[]` (`type`, `name`, scales), `areas[]` (`name`, `angle`, `resetDate`, `section`, `imageFile`; v1: `image`), `problems[]` (`name`, `discipline`, grade, `area`, `tape`, `setDate`, `retired`, `notes`, `createdAt`), location (`latitude`, `longitude`, `address`), `favourite`, `localScale`, `notes`, `createdAt` | Same type and name |
| `bodyMeasurements` | Body stats | `MeasurementDto`: `type`, `value` (kg/cm), `date`, `loggedAt` | Same type, date and `loggedAt` |
| `records` | Personal records | `RecordDto`: embedded `exercise`, `date`, `reps`, `seconds`, `loadKg`, `notes`, `loggedAt` | Same exercise name and `loggedAt` |
| `notes` | Notes | `NoteDto`: `text`, `createdAt`, `updatedAt`, `pinned`, `tag` | Same `createdAt` |
| `settings` | Settings | `SettingsDto`: `theme`, `accent` and `textSize` (names), `units`, `boulderScale`, `routeScale`, `timerSounds`, `timerCompact`, `dashboard[]` (`type`, `size` as names) | — (always replaces) |

Names match ignoring case and surrounding spaces. Dates are ISO `yyyy-MM-dd`. `loggedAt`,
`createdAt`, `updatedAt`, `startedAt` and `completedAt` are epoch milliseconds. Enums are written
by name, except the dashboard's widget names, the accent and the text size, which are strings so a widget or accent
one app version has and another doesn't can't break the file.

Settings leave out what only means something on one install: the last place, facility and wall
(database ids), demo mode, and the backup's own photo and video switches.

## Import

Sections go in this order, so later ones can link to earlier ones: exercises, plans, places,
sessions, journal, body stats, records, notes, then settings (after the database transaction).
Plans, sessions and records bring the exercises they need. A link resolves first to what this
import made of the named record (so a *Keep both* copy gets its own climbs), then to what's already
here by name.

What each choice does:

| | Skip | Replace | Keep both |
| --- | --- | --- | --- |
| Exercise, plan | Links point at the existing one | Updated in place (a plan's blocks rewritten) | Added as "Name (2)" |
| Place | — | Details updated; facilities, walls and problems matched by name (problems also by discipline and set date) are updated, the rest added; nothing removed | Added as "Name (2)" |
| Session | — | Updated, its exercises and sets rewritten | Added |
| Climb | Joins its session if it had none | Updated; the backup's photo and video replace its own when the backup has them | Added |
| Body stat, record, note | — | Updated | Added |

The whole import is one transaction. Media files it wrote are deleted if it fails; files of
replaced media are deleted only after it succeeds. Records this version can't read (a grade no
scale has) are counted and left out.

## Media

Climb photos and wall images are JPEG, already scaled to at most 2048 px; videos are stored as
recorded. The **Photos** and **Videos** switches (`backup_photos`, `backup_videos` in preferences)
apply to export and import alike. Photo bytes that don't decode as an image are dropped on import.

## Changing the format

- Adding a field: give it a default, and note in its KDoc what older backups do without it.
- A change older apps can't read: bump `BackupFile.VERSION`.
- Never remove the v0.1.0 or v0.2.0 fixture tests. When the format changes, add a fixture made by
  the last release.
