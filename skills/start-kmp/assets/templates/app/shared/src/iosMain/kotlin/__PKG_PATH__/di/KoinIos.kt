package __PKG__.di

import __PKG__.core.common.config.AppConfig

/** Swift: `KoinIosKt.doInitKoin(baseUrl: "https://api.example.com/", isDebug: true)` */
fun doInitKoin(baseUrl: String, isDebug: Boolean) {
    initKoin(AppConfig(baseUrl = baseUrl, isDebug = isDebug))
}
