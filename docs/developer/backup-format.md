# Backup format

[`BackupRepository`](../../app/src/main/java/com/hardtekpt/crux/data/backup/Backup.kt) writes and
reads a single JSON file, made with kotlinx.serialization (pretty-printed, unknown keys ignored).

## Principles

- **Self-describing**: `format` is always `"crux-backup"`, and `version` is the format version
  (currently **1**).
- **No database ids.** Records refer to each other by name (a climb names its place, area and
  problem; a plan item embeds its exercise), so a file imports cleanly into any install.
- **Sections are optional.** A section that wasn't exported is absent (`null`), which is different
  from present but empty.
- **Import only adds.** Records already there (matched by identity: names, dates, grades) are
  skipped. Importing the same file twice adds nothing the second time.
- **Forward compatible within a version.** New fields get defaults, so older files still parse.
  `BackupRepositoryTest` imports a real v0.1.0 file from `app/src/test/resources/backups/` to make
  sure of it. A file with a *higher* `version` than the app knows is refused with "update the app".

## Top level

```json
{
  "format": "crux-backup",
  "version": 1,
  "exportedAt": "2026-10-05T12:00:00Z",
  "exercises": [ … ],
  "plans": [ … ],
  "climbs": [ … ],
  "places": [ … ],
  "bodyMeasurements": [ … ],
  "records": [ … ],
  "notes": [ … ],
  "sessions": null
}
```

| Key | Section in the app | Element |
| --- | --- | --- |
| `exercises` | Exercise list | `ExerciseDto`: `name`, `category`, `metric`, `notes`, `defaults` (`sets`, `reps`, `seconds`, `loadKg`, `restSeconds`, `repRestSeconds`, `prepSeconds`) |
| `plans` | Plan list | `PlanDto`: `name`, `description`, `blocks[]` → `name`, `items[]` → embedded `exercise` + target (`sets`, `reps`, `seconds`, `loadKg`, `restSeconds`, `repRestSeconds`) |
| `climbs` | Journal | `ClimbDto`: `discipline`, `gradeScale`, `grade` (label), `style`, `attempts`, `venue`, `date`, `loggedAt`, optional `name`, `notes`, `place` + `placeType`, `section`, `area`, `problem`, `angle`, `effort`, `gradeIndex`/`gradeColour` (local grades), `image` (base64 JPEG) |
| `places` | Places | `PlaceDto`: `name`, `type`, `extraTypes`, `sections[]` (`type`, `name`, scales), `areas[]` (`name`, `angle`, `resetDate`, `section`, `image`), `problems[]` (`name`, `discipline`, grade, `area`, `tape`, `setDate`, `retired`, `notes`), location (`latitude`, `longitude`, `address`), `favourite`, `localScale`, `notes` |
| `bodyMeasurements` | Body stats | `MeasurementDto`: `type`, `value` (kg/cm), `date`, `loggedAt` |
| `records` | Personal records | `RecordDto`: embedded `exercise`, `date`, `reps`, `seconds`, `loadKg`, `notes`, `loggedAt` |
| `notes` | Notes | `NoteDto`: `text`, `createdAt`, `updatedAt`, `pinned`, `tag` |
| `sessions` | Session history | Reserved, always `null` for now |

Dates are ISO `yyyy-MM-dd`. `loggedAt`, `createdAt` and `updatedAt` are epoch milliseconds.
Enums are written by name.

## Media

Climb photos and wall images are embedded as base64 JPEG (they're already scaled to at most 2048
px). Bytes that don't decode as an image are dropped on import. **Videos are never included.**

## Changing the format

- Adding a field: give it a default, and note in its KDoc what older backups do without it.
- A change older apps can't read: bump `BackupFile.VERSION`.
- Never remove the v0.1.0 fixture test.
