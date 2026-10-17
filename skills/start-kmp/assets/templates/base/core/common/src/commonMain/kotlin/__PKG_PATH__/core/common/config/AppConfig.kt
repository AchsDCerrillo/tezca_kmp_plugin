package __PKG__.core.common.config

/**
 * Runtime configuration handed in by the platform entry point (Android Application / iOS app or
 * the SDK consumer). [baseUrl] must end with "/" so relative API paths resolve under it.
 */
public data class AppConfig(
    val baseUrl: String,
    val isDebug: Boolean,
)
