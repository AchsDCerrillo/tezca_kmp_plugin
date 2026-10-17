package __PKG__.core.datastore.di

import org.koin.core.module.Module
import org.koin.dsl.module

/** Provides the single `DataStore<Preferences>` at the platform's files location. */
internal expect val platformDataStoreModule: Module

/* sdk:internal */ public val dataStoreModule: Module = module {
    includes(platformDataStoreModule)
}
