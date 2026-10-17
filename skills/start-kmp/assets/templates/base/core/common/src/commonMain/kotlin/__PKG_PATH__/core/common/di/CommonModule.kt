package __PKG__.core.common.di

import __PKG__.core.common.dispatchers.DefaultDispatcherProvider
import __PKG__.core.common.dispatchers.DispatcherProvider
import org.koin.dsl.module

/* sdk:internal */ public val commonModule = module {
    single<DispatcherProvider> { DefaultDispatcherProvider() }
}
