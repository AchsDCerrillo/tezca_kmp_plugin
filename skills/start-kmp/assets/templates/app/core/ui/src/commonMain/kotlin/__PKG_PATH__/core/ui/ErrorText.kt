package __PKG__.core.ui

import __PKG__.core.common.result.AppError
import __PKG__.core.ui.generated.resources.Res
import __PKG__.core.ui.generated.resources.error_forbidden
import __PKG__.core.ui.generated.resources.error_no_internet
import __PKG__.core.ui.generated.resources.error_not_found
import __PKG__.core.ui.generated.resources.error_server
import __PKG__.core.ui.generated.resources.error_timeout
import __PKG__.core.ui.generated.resources.error_unauthorized
import __PKG__.core.ui.generated.resources.error_unknown

/**
 * Default user-facing message for an [AppError]. Features map their own [AppError.Domain] codes
 * (and any error they want to word differently) before falling back to this.
 */
fun AppError.toUiText(): UiText = UiText.Resource(
    when (this) {
        AppError.Network.NoInternet -> Res.string.error_no_internet
        AppError.Network.Timeout -> Res.string.error_timeout
        AppError.Network.Unauthorized -> Res.string.error_unauthorized
        AppError.Network.Forbidden -> Res.string.error_forbidden
        AppError.Network.NotFound, AppError.Local.NotFound -> Res.string.error_not_found
        is AppError.Network.Server -> Res.string.error_server
        AppError.Network.Serialization,
        is AppError.Network.Client,
        is AppError.Local.Database,
        is AppError.Domain,
        is AppError.Unknown -> Res.string.error_unknown
    },
)
