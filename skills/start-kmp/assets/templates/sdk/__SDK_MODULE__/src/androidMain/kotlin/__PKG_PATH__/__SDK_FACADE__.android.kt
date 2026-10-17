package __PKG__

import android.content.Context
import __PKG__.core.common.config.AppConfig
import __PKG__.di.SdkKoin
import org.koin.android.ext.koin.androidContext

/** Call once, e.g. in `Application.onCreate`. */
public fun __SDK_FACADE__.initialize(context: Context, config: AppConfig) {
    SdkKoin.start(config) { androidContext(context.applicationContext) }
}
