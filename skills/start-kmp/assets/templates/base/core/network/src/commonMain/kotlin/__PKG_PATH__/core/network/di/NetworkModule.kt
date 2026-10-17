package __PKG__.core.network.di

import __PKG__.core.network.HttpClientFactory
import __PKG__.core.network.NoTokenProvider
import __PKG__.core.network.TokenProvider
import org.koin.core.module.Module
import org.koin.dsl.module

/** Provides the platform HttpClientEngine (OkHttp / Darwin). */
internal expect val platformNetworkModule: Module

/* sdk:internal */ public val networkModule: Module = module {
    includes(platformNetworkModule)
    single<TokenProvider> { NoTokenProvider() }
    single { HttpClientFactory.create(engine = get(), config = get(), tokenProvider = get()) }
}
