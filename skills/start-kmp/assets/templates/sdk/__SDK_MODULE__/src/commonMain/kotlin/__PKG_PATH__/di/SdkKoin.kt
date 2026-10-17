package __PKG__.di

import __PKG__.core.common.config.AppConfig
import __PKG__.core.common.di.commonModule
import __PKG__.core.database.di.databaseModule
import __PKG__.core.datastore.di.dataStoreModule
import __PKG__.core.network.di.networkModule
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/** Feature Koin modules. new-feature registers each feature here. */
internal val featureModules: List<Module> = listOf(
    // tezca-kmp:feature-modules (new-feature adds <feature>Module entries below this line)
)

internal fun sdkModules(config: AppConfig): List<Module> = listOf(
    module { single { config } },
    commonModule,
    networkModule,
    databaseModule,
    dataStoreModule,
) + featureModules

/**
 * Isolated Koin context: the SDK never calls the global startKoin, so a host app that uses Koin
 * itself keeps working and can't see or override SDK definitions.
 */
@OptIn(ExperimentalAtomicApi::class)
internal object SdkKoin {
    private val application = AtomicReference<KoinApplication?>(null)

    val isStarted: Boolean get() = application.load() != null

    val koin: Koin
        get() = checkNotNull(application.load()) { "Call __SDK_FACADE__.initialize(...) before using the SDK" }.koin

    /** Idempotent and thread safe: the first call wins, later calls return the running instance. */
    fun start(config: AppConfig, platformDeclaration: KoinAppDeclaration = {}): Koin {
        application.load()?.let { return it.koin }
        val created = koinApplication {
            platformDeclaration()
            modules(sdkModules(config))
        }
        return if (application.compareAndSet(null, created)) {
            created.koin
        } else {
            created.close()
            koin
        }
    }

    fun stop() {
        application.exchange(null)?.close()
    }
}
