package __PKG__.core.database

import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * Creates Room database builders at the right platform location (Android databases dir, iOS
 * Documents). Each feature owns its own database and builds it through this factory:
 *
 * ```
 * single { get<DatabaseFactory>().create("onboarding.db") { OnboardingDatabaseConstructor.initialize() }.buildDefault() }
 * ```
 */
/* sdk:internal */ public expect class DatabaseFactory

/** Room's builders are reified, so this is an inline function with a platform implementation. */
/* sdk:internal */ public expect inline fun <reified T : RoomDatabase> DatabaseFactory.create(
    name: String,
    noinline factory: () -> T,
): RoomDatabase.Builder<T>

/** Bundled SQLite (same engine on both platforms, no -lsqlite3 needed) and queries off the main thread. */
/* sdk:internal */ public fun <T : RoomDatabase> RoomDatabase.Builder<T>.buildDefault(): T =
    setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
