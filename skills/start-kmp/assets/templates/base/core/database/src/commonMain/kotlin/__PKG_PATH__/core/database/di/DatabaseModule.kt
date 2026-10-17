package __PKG__.core.database.di

import __PKG__.core.database.CoreDatabase
import __PKG__.core.database.CoreDatabaseConstructor
import __PKG__.core.database.DatabaseFactory
import __PKG__.core.database.buildDefault
import __PKG__.core.database.create
import __PKG__.core.database.sync.SyncMetadataStore
import org.koin.core.module.Module
import org.koin.dsl.module

/** Provides [DatabaseFactory] (needs the Android Context on Android). */
internal expect val platformDatabaseModule: Module

/* sdk:internal */ public val databaseModule: Module = module {
    includes(platformDatabaseModule)
    single<CoreDatabase> {
        get<DatabaseFactory>().create("core.db") { CoreDatabaseConstructor.initialize() }.buildDefault()
    }
    single { get<CoreDatabase>().syncMetadataDao() }
    single { SyncMetadataStore(get()) }
}
