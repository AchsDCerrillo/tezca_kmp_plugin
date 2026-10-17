package __PKG__.core.testing

import __PKG__.core.common.dispatchers.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher

/** Every dispatcher points at one test dispatcher, so `runTest` controls all coroutines. */
/* sdk:internal */ public class TestDispatcherProvider(
    public val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : DispatcherProvider {
    override val main: CoroutineDispatcher = testDispatcher
    override val io: CoroutineDispatcher = testDispatcher
    override val default: CoroutineDispatcher = testDispatcher
}
