# make-database templates

The examples use the feature `profile` (app-mode package `com.acme.gym.feature.profile`) and the domain model `User` from make-api. In SDK mode, drop the `feature.` segment.

## Entity: `data/local/UserEntity.kt`
```kotlin
package com.acme.gym.feature.profile.data.local

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "users",
    indices = [Index(value = ["full_name"])],
)
internal data class UserEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "full_name") val fullName: String,
    val email: String,
    @ColumnInfo(name = "avatar_url") val avatarUrl: String?,
    val plan: String,                     // enum stored by name
    @ColumnInfo(name = "created_at") val createdAtEpochMs: Long,
)
```
Column names are snake_case through `@ColumnInfo`, so SQL in the DAO reads naturally.

## DAO: `data/local/UserDao.kt`
```kotlin
package com.acme.gym.feature.profile.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
internal interface UserDao {

    @Query("SELECT * FROM users ORDER BY full_name")
    fun observeAll(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE id = :id")
    fun observeById(id: Long): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE full_name LIKE '%' || :query || '%' ORDER BY full_name")
    fun search(query: String): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getById(id: Long): UserEntity?

    // Cache tables: REPLACE is the upsert. Prefer it over @Upsert, which detects conflicts from the
    // SQLite error message; the Android stubs in JVM host tests drop that message and @Upsert then
    // throws on existing rows. Use @Upsert only for parents of ON DELETE CASCADE foreign keys
    // (REPLACE deletes the old row, which would cascade) and test those DAOs on the iOS simulator.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(users: List<UserEntity>)

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM users")
    suspend fun deleteAll()

    /** Replace the cached list atomically, so observers never see an empty table in between. */
    @Transaction
    suspend fun replaceAll(users: List<UserEntity>) {
        deleteAll()
        upsertAll(users)
    }
}
```

## Database: `data/local/ProfileDatabase.kt`
```kotlin
package com.acme.gym.feature.profile.data.local

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor

@Database(
    entities = [UserEntity::class],
    version = 1,
    // autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@ConstructedBy(ProfileDatabaseConstructor::class)
internal abstract class ProfileDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
}

// The Room compiler generates the actual implementations.
@Suppress("KotlinNoActualForExpect")
internal expect object ProfileDatabaseConstructor : RoomDatabaseConstructor<ProfileDatabase> {
    override fun initialize(): ProfileDatabase
}
```

## Mappers: `data/mapper/UserMappers.kt` (add to the file make-api created)
```kotlin
import kotlin.time.Instant

internal fun UserEntity.toDomain(): User = User(
    id = id,
    fullName = fullName,
    email = email,
    avatarUrl = avatarUrl,
    plan = Plan.valueOf(plan),
    createdAt = Instant.fromEpochMilliseconds(createdAtEpochMs),
)

internal fun User.toEntity(): UserEntity = UserEntity(
    id = id,
    fullName = fullName,
    email = email,
    avatarUrl = avatarUrl,
    plan = plan.name,
    createdAtEpochMs = createdAt.toEpochMilliseconds(),
)
```
`Plan.valueOf` throws on unknown values. Use `Plan.entries.firstOrNull { it.name == plan } ?: Plan.FREE` when old rows could hold removed values.

## Local data source: `data/local/UserLocalDataSource.kt`
```kotlin
package com.acme.gym.feature.profile.data.local

import com.acme.gym.feature.profile.data.mapper.toDomain
import com.acme.gym.feature.profile.data.mapper.toEntity
import com.acme.gym.feature.profile.domain.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal interface UserLocalDataSource {
    fun observeUsers(): Flow<List<User>>
    fun observeUser(id: Long): Flow<User?>
    suspend fun getUser(id: Long): User?
    suspend fun upsert(user: User)
    suspend fun replaceAll(users: List<User>)
    suspend fun delete(id: Long)
}

internal class UserLocalDataSourceImpl(private val dao: UserDao) : UserLocalDataSource {
    override fun observeUsers(): Flow<List<User>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }
    override fun observeUser(id: Long): Flow<User?> = dao.observeById(id).map { it?.toDomain() }
    override suspend fun getUser(id: Long): User? = dao.getById(id)?.toDomain()
    override suspend fun upsert(user: User) = dao.upsert(user.toEntity())
    override suspend fun replaceAll(users: List<User>) = dao.replaceAll(users.map { it.toEntity() })
    override suspend fun delete(id: Long) = dao.deleteById(id)
}
```

## Koin: `di/ProfileModule.kt`
```kotlin
import com.acme.gym.core.database.DatabaseFactory
import com.acme.gym.core.database.buildDefault
import com.acme.gym.core.database.create
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf

// tezca-kmp:data
single { get<DatabaseFactory>().create("profile.db") { ProfileDatabaseConstructor.initialize() }.buildDefault() }
single { get<ProfileDatabase>().userDao() }
singleOf(::UserLocalDataSourceImpl) { bind<UserLocalDataSource>() }
```

## Migrations
Auto (Room diffs the exported schemas in `schemas/`):
```kotlin
@Database(entities = [UserEntity::class, BadgeEntity::class], version = 2,
          autoMigrations = [AutoMigration(from = 1, to = 2)])
```
Renames and deletions need a spec:
```kotlin
@RenameColumn(tableName = "users", fromColumnName = "name", toColumnName = "full_name")
internal class RenameNameSpec : AutoMigrationSpec

autoMigrations = [AutoMigration(from = 2, to = 3, spec = RenameNameSpec::class)]
```
Manual migration (data transformation). `Migration` / `SQLiteConnection` come from `androidx.room3.migration` and `androidx.sqlite`:
```kotlin
internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("UPDATE users SET plan = 'FREE' WHERE plan IS NULL")
    }
}
// Koin: .create("profile.db") { ... }.addMigrations(MIGRATION_3_4).buildDefault()
```

## Relations (one-to-many)
```kotlin
internal data class UserWithBadges(
    @Embedded val user: UserEntity,
    @Relation(parentColumn = "id", entityColumn = "user_id") val badges: List<BadgeEntity>,
)
@Transaction @Query("SELECT * FROM users WHERE id = :id") fun observeWithBadges(id: Long): Flow<UserWithBadges?>
```

## Common KSP / build errors
| Error | Fix |
|---|---|
| `Schema export directory was not provided` | the module is missing `alias(libs.plugins.kmp.room)` |
| `Expected object 'XConstructor' has no actual declaration` (IDE only) | harmless; keep `@Suppress("KotlinNoActualForExpect")` |
| `Cannot find setter/getter for field` | Use `val` constructor params with supported types: primitives, String, ByteArray, or anything with a `@TypeConverter` |
| `Unresolved reference: create` in Koin | import `<base>.core.database.create` (an extension, not a member) |
| `Migration didn't properly handle` | add the missing AutoMigration or spec, or check that the schema JSON for the previous version is committed |
| Queries on the main thread | can't happen with `buildDefault()`: queries run on `Dispatchers.IO` |
