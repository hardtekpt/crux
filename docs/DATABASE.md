# Changing the database

The climber's database is **never wiped**. Every schema change has to migrate existing data,
because people update the app over months of climbing history.

## Checklist for a schema change

1. **Change the entities** and bump `CruxDatabase.VERSION`
   ([CruxDatabase.kt](../app/src/main/java/com/hardtekpt/crux/data/local/CruxDatabase.kt)).
2. **Add an `AutoMigration(from = N, to = N + 1)`** to the `@Database` list.
   - Use a spec class when Room can't infer the change: renamed or deleted columns and tables
     need `@RenameColumn`, `@DeleteColumn` and so on.
   - Use `onPostMigrate` when data has to move. See `PlacesMigration` and `SectionsMigration`.
   - Hand-written `Migration`s go through `addMigrations(...)` in `DatabaseModule`, for the REAL
     database only.
3. **Build once** (`./gradlew assembleDebug`). Room exports `app/schemas/.../N+1.json`, and you
   commit that file. `SchemaExportTest` fails if an export is missing.
4. **Test the migration.**
   - `MigrationTest.everyOldSchemaMigratesToTheLatest` covers every version automatically.
   - If the migration moves data, add a step test like `migrate15To16…` that inserts rows at
     version N and checks them at N + 1.
   - Run `.\crux device-test`, or push to a `ci/` branch (`git push origin HEAD:ci/device-tests`).
5. **Check backups.**
   - Is the new data worth backing up? Add it to `BackupFile`, giving new fields a default so older
     files still parse.
   - If you change the format in a way older apps can't read, bump `BackupFile.VERSION`.
   - `BackupRepositoryTest` must keep importing the v0.1.0 fixture.
6. **Demo data:** if the sample data should show the new feature, update `StarterDataSeeder`.

## Never

- **Never add `fallbackToDestructiveMigration` to the climber's database.** Only the demo
  database has it.
- Don't edit or delete committed schema JSON files. Migration tests build old databases from them.
- Don't reuse a version number that's already been released.

## If a migration goes wrong on a phone

Before Room migrates, the old file is copied to `files/db-backups/crux-user-v<old>.db`, and the
newest three copies are kept. With a debug build, you can pull it with
`adb shell run-as com.hardtekpt.crux.debug cat files/db-backups/crux-user-v15.db > old.db`.
Fix the migration, ship it, and the copy can be restored by hand if needed.
