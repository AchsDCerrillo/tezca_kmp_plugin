package __PKG__.di

import __PKG__.core.common.config.AppConfig
import __PKG__.core.common.di.commonModule
import __PKG__.core.database.di.databaseModule
import __PKG__.core.datastore.di.dataStoreModule
import __PKG__.core.network.di.networkModule
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/** Feature Koin modules. new-feature registers each feature here. */
internal val featureModules: List<Module> = listOf(
    // tezca-kmp:feature-modules (new-feature adds <feature>Module entries below this line)
)

fun appModules(config: AppConfig): List<Module> = listOf(
    module { single { config } },
    commonModule,
    networkModule,
    databaseModule,
    dataStoreModule,
) + featureModules

/**
 * Starts Koin once per process. Android calls it from the Application (passing androidContext in
 * [appDeclaration]); iOS calls [doInitKoin] from the SwiftUI App init.
 */
fun initKoin(config: AppConfig, appDeclaration: KoinAppDeclaration = {}): KoinApplication = startKoin {
    appDeclaration()
    modules(appModules(config))
}
