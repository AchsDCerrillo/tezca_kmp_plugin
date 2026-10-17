package __PKG__.__FPKG__

import org.koin.core.Koin

/**
 * Public surface of the __FEATURE__ feature: `__SDK_FACADE__.__feature__.<useCase>(...)`.
 * Swift (SKIE): `try await __SDK_FACADE__.shared.__feature__.<useCase>.invoke(...)`.
 */
public class __Feature__Feature internal constructor(private val koin: Koin) {
    // tezca-kmp:sdk-usecases (make-usecase adds `public val getX: GetXUseCase get() = koin.get()` below)
}
