package __PKG__.core.network

/* sdk:internal */ public data class AuthTokens(
    val accessToken: String,
    val refreshToken: String?,
)

/**
 * Supplies tokens to the Ktor Auth plugin. The default binding returns no tokens; a login feature
 * overrides it in Koin with an implementation backed by DataStore (see make-preferences).
 */
/* sdk:internal */ public interface TokenProvider {
    public suspend fun currentTokens(): AuthTokens?

    /** Called after a 401. Return fresh tokens, or null to give up (the 401 reaches the caller). */
    public suspend fun refreshTokens(): AuthTokens?
}

/* sdk:internal */ public class NoTokenProvider : TokenProvider {
    override suspend fun currentTokens(): AuthTokens? = null
    override suspend fun refreshTokens(): AuthTokens? = null
}
