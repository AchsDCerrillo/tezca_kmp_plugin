---
name: make-database
description: Create or update Room 3 (KMP) local storage for a feature in a Kotlin Multiplatform project. Generates @Entity classes, @Dao interfaces (Flow reads, suspend upserts, transactions), the feature's @Database with its RoomDatabaseConstructor, a LocalDataSource interface + Impl that maps entities to domain models, the Koin bindings, and schema migrations (version bump + AutoMigration). Use it whenever the user wants to store, cache or persist data locally, add a table/column/index/relation, work offline, write a DAO or query, or change the local database in a KMP/Compose Multiplatform app or SDK, even if they say "SQLite", "cache" or "save it on the device" instead of Room.
---

# make-database: Room DAO and local data source

Room 3 is the local database. It uses package `androidx.room3`, KSP for every target and the bundled SQLite driver. `:core:database` provides `DatabaseFactory` and `buildDefault()`, so each feature only declares its own `@Database`. Keeping one database per feature keeps modules independent, because Room needs every entity of a database at compile time. Cross-feature bookkeeping (sync timestamps) lives in `CoreDatabase`.

## Before you start
1. Read `../kmp-architecture/SKILL.md` and `.tezca-kmp.json`, then locate the feature root (see make-api).
2. Find out what to store. That means the domain model (it often exists already from `make-api`), the queries the UI needs (list, by id, search, sorted, paged), and how stale data gets replaced. Ask only what you can't infer.
3. Look for an existing `data/local/` in the feature. If there's a `<Feature>Database`, **update** it: add the entity to `entities`, add the DAO accessor, bump the version, and add a migration. Don't create a second database for the same feature.

## Steps
Templates are in `references/templates.md`.
1. **Room plugin (app mode, first database in the feature).** Add `alias(libs.plugins.kmp.room)` to `feature/<name>/build.gradle.kts`. It replaces the commented line the template left. The plugin applies KSP and Room for Android, iosArm64 and iosSimulatorArm64, and exports schemas to `feature/<name>/schemas/`. Commit that directory. In SDK mode the SDK module already applies Room.
2. **Entity** goes in `data/local/<X>Entity.kt`:
   - `@Entity(tableName = "<plural_snake>") internal data class`.
   - Columns are primitive or `String` only: `Instant` as epoch-ms `Long`, enums as `String`. That avoids type converters unless the user wants them.
   - Add indices for columns you filter or sort by.
   - Use a composite primary key, or a foreign key with `onDelete = CASCADE`, only where the data really is relational.
3. **DAO** goes in `data/local/<X>Dao.kt` as `@Dao internal interface`:
   - Reads return `Flow<List<XEntity>>` / `Flow<XEntity?>`, so the UI updates on every change.
   - One-shot reads used inside sync logic are `suspend`.
   - Writes are `@Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert…` (see the template comment for why not `@Upsert`), and deletes are `@Query("DELETE …") suspend fun`.
   - For "replace the cached list" use a `@Transaction` function with a body that deletes and then upserts (template). Don't use two separate calls from the data source.
4. **Database** goes in `data/local/<Feature>Database.kt`: `@Database(entities = [...], version = N)` plus `@ConstructedBy(<Feature>DatabaseConstructor::class)`, and the `expect object <Feature>DatabaseConstructor`. Room generates the actuals.
5. **Mappers** go in `data/mapper/<X>Mappers.kt`: `XEntity.toDomain()` and `X.toEntity()`.
6. **Local data source** goes in `data/local/<X>LocalDataSource.kt`: an interface plus `internal class <X>LocalDataSourceImpl(private val dao: <X>Dao)`.
   - It exposes `Flow<Domain>`, plus `suspend` writes that take domain models.
   - It catches `SQLiteException` only where a write can legitimately fail (constraints), and returns `DataResult.Failure(AppError.Local.Database(...))` in that case.
7. **Koin**: under `// tezca-kmp:data` in `di/<Feature>Module.kt`:
   ```kotlin
   single { get<DatabaseFactory>().create("<feature>.db") { <Feature>DatabaseConstructor.initialize() }.buildDefault() }
   single { get<<Feature>Database>().<x>Dao() }
   singleOf(::<X>LocalDataSourceImpl) { bind<<X>LocalDataSource>() }
   ```
   Imports: `<base>.core.database.DatabaseFactory`, `<base>.core.database.create`, `<base>.core.database.buildDefault`.
8. **Schema change on an existing database:**
   - Bump `version`.
   - Add `autoMigrations = [AutoMigration(from = N-1, to = N)]` when Room can infer the change (added table or column with a default, added index).
   - For renames and deletions, add an `AutoMigrationSpec` (`@RenameColumn`, `@DeleteColumn`) or a manual `Migration` passed with `.addMigrations(...)` before `buildDefault()`.
   - Only use `.fallbackToDestructiveMigration(dropAllTables = true)` if the user explicitly accepts losing local data. That's sometimes acceptable for a pure cache, but say so.

## Verify
- Run `./gradlew :<module>:compileAndroidMain :<module>:compileKotlinIosSimulatorArm64`. KSP errors such as "Cannot find getter", "Missing @ConstructedBy" or "Schema export directory" show up here. `references/templates.md` has fixes for the common ones.
- Offer DAO tests through `add-tests`. They use an in-memory database on the iOS simulator and the JVM, and catch wrong queries early.
- Report the files changed and the new database version.
