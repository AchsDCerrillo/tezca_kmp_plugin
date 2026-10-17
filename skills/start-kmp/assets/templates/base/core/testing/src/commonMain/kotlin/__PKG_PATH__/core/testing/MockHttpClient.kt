package __PKG__.core.testing

import __PKG__.core.common.config.AppConfig
import __PKG__.core.network.HttpClientFactory
import __PKG__.core.network.NoTokenProvider
import __PKG__.core.network.TokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

/* sdk:internal */ public const val TEST_BASE_URL: String = "https://api.test/"

/**
 * An HttpClient with the production configuration (serialization, auth, retries, validation)
 * on top of a MockEngine, so API tests exercise the real client setup. Retries are off so 5xx
 * tests return immediately; pass maxRetries to test the retry policy itself.
 */
/* sdk:internal */ public fun mockHttpClient(
    tokenProvider: TokenProvider = NoTokenProvider(),
    maxRetries: Int = 0,
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
): HttpClient = HttpClientFactory.create(
    engine = MockEngine(handler),
    config = AppConfig(baseUrl = TEST_BASE_URL, isDebug = false),
    tokenProvider = tokenProvider,
    maxRetries = maxRetries,
)

/* sdk:internal */ public fun MockRequestHandleScope.respondJson(
    json: String,
    status: HttpStatusCode = HttpStatusCode.OK,
): HttpResponseData = respond(
    content = json,
    status = status,
    headers = headersOf(HttpHeaders.ContentType, "application/json"),
)
