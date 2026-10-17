package __PKG__

import __PKG__.core.common.config.AppConfig
import __PKG__.di.SdkKoin

/** Swift: `__SDK_FACADE__.shared.initialize(config: AppConfig(baseUrl: "https://api.example.com/", isDebug: false))` */
public fun __SDK_FACADE__.initialize(config: AppConfig) {
    SdkKoin.start(config)
}
