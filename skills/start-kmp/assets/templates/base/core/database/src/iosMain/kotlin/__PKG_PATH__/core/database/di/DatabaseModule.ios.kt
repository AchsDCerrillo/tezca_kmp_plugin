package __PKG__.core.database.di

import __PKG__.core.database.DatabaseFactory
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val platformDatabaseModule: Module = module {
    single { DatabaseFactory() }
}
