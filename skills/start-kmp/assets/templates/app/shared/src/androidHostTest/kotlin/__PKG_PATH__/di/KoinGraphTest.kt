package __PKG__.di

import android.content.Context
import __PKG__.core.common.config.AppConfig
import io.ktor.client.engine.HttpClientEngine
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify
import kotlin.test.Test

/** Fails the build when a Koin definition cannot be resolved (missing binding, wrong type). */
class KoinGraphTest {
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun appModules_allDefinitionsResolvable() {
        module { includes(appModules(AppConfig(baseUrl = "https://api.test/", isDebug = true))) }
            .verify(extraTypes = listOf(Context::class, HttpClientEngine::class, String::class, Boolean::class))
    }
}
