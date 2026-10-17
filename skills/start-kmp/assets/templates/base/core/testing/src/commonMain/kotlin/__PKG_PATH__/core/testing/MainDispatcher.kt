package __PKG__.core.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * ViewModel tests: `viewModelScope` uses Dispatchers.Main. Call [setMainDispatcher] in
 * `@BeforeTest` and [resetMainDispatcher] in `@AfterTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
/* sdk:internal */ public fun setMainDispatcher(dispatcher: TestDispatcher) {
    Dispatchers.setMain(dispatcher)
}

@OptIn(ExperimentalCoroutinesApi::class)
/* sdk:internal */ public fun resetMainDispatcher() {
    Dispatchers.resetMain()
}
