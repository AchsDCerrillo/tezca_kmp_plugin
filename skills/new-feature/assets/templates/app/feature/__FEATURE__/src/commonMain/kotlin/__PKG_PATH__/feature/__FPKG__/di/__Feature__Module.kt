package __PKG__.feature.__FPKG__.di

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Every binding of the __FEATURE__ feature. Order inside the module doesn't matter for Koin; the
 * sections only keep it readable. The make-* skills add lines under each marker.
 */
val __feature__Module: Module = module {
    // tezca-kmp:data (APIs, databases, DAOs, data sources, repositories)

    // tezca-kmp:domain (use cases: factoryOf(::XUseCase))

    // tezca-kmp:presentation (viewModelOf(::XViewModel))
}
