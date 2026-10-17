package __PKG__.core.network

import __PKG__.core.common.config.AppConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.authProvider
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.plugins.resources.Resources
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/* sdk:internal */ public val NetworkJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    coerceInputValues = true
}

/**
 * Single place where the Ktor client is configured. The engine is injected (OkHttp on Android,
 * Darwin on iOS, MockEngine in tests) and passed to the constructor; configuring it inside
 * `engine {}` would silently fall back to the default engine.
 */
/* sdk:internal */ public object HttpClientFactory {

    private val idempotentMethods = setOf(HttpMethod.Get, HttpMethod.Head, HttpMethod.Options, HttpMethod.Put, HttpMethod.Delete)

    public fun create(
        engine: HttpClientEngine,
        config: AppConfig,
        tokenProvider: TokenProvider,
        maxRetries: Int = 2,
    ): HttpClient = HttpClient(engine) {
        // Status codes are mapped to AppError in safeCall, so never throw on non-2xx.
        expectSuccess = false

        install(ContentNegotiation) { json(NetworkJson) }
        install(Resources)

        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
        }

        if (maxRetries > 0) install(HttpRequestRetry) {
            // Only retry requests that are safe to repeat, on 5xx / 429 or I/O failures.
            retryIf(maxRetries = maxRetries) { request, response ->
                request.method in idempotentMethods &&
                    (response.status.value in 500..599 || response.status == HttpStatusCode.TooManyRequests)
            }
            retryOnExceptionIf(maxRetries = maxRetries) { request, _ -> request.method in idempotentMethods }
            exponentialDelay()
        }

        install(Logging) {
            logger = Logger.SIMPLE
            level = if (config.isDebug) LogLevel.BODY else LogLevel.NONE
            sanitizeHeader { header -> header == HttpHeaders.Authorization }
        }

        install(Auth) {
            bearer {
                loadTokens { tokenProvider.currentTokens()?.toBearer() }
                refreshTokens { tokenProvider.refreshTokens()?.toBearer() }
                // Send the token up front to our own API instead of waiting for a 401 challenge.
                sendWithoutRequest { request -> request.url.host == Url(config.baseUrl).host }
            }
        }

        defaultRequest {
            url(config.baseUrl)
            contentType(ContentType.Application.Json)
        }
    }

    private fun AuthTokens.toBearer() = BearerTokens(accessToken = accessToken, refreshToken = refreshToken)
}

/**
 * Ktor's bearer provider caches the tokens it loaded. Call this after logout or a user switch so
 * the next request asks [TokenProvider] again instead of sending the previous user's token.
 */
/* sdk:internal */ public fun HttpClient.clearAuthTokens() {
    authProvider<BearerAuthProvider>()?.clearToken()
}
