# make-api templates

The examples use the feature `profile`, the aggregate `User`, and the base package `com.acme.gym`.
- App mode package: `com.acme.gym.feature.profile`. Everything is `internal`, domain models included (kmp-architecture section 6).
- SDK mode package: `com.acme.gym.profile`. Domain models are `public` (consumers see them); everything else stays `internal`.

## Contents
1. DTOs
2. Resources (routes)
3. Api
4. Mappers + domain model
5. Remote data source
6. Koin
7. Special cases

## 1. DTOs: `data/remote/UserDto.kt`
```kotlin
package com.acme.gym.feature.profile.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class UserDto(
    @SerialName("id") val id: Long,
    @SerialName("full_name") val fullName: String,
    @SerialName("email") val email: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("plan") val plan: PlanDto = PlanDto.FREE,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
internal enum class PlanDto {
    @SerialName("free") FREE,
    @SerialName("premium") PREMIUM,
}

@Serializable
internal data class UpdateUserRequestDto(
    @SerialName("full_name") val fullName: String,
)

/** Envelope for paginated lists: { "items": [...], "page": 1, "total_pages": 4 } */
@Serializable
internal data class PageDto<T>(
    @SerialName("items") val items: List<T>,
    @SerialName("page") val page: Int,
    @SerialName("total_pages") val totalPages: Int,
)
```

## 2. Resources: `data/remote/UserResources.kt`
```kotlin
package com.acme.gym.feature.profile.data.remote

import io.ktor.resources.Resource
import kotlinx.serialization.Serializable

/** GET users?page=&query=  |  GET/PUT/DELETE users/{id}  |  POST users/{id}/avatar */
@Serializable
@Resource("users")
internal class UsersResource(val page: Int? = null, val query: String? = null) {

    @Serializable
    @Resource("{id}")
    internal class Id(val parent: UsersResource = UsersResource(), val id: Long) {

        @Serializable
        @Resource("avatar")
        internal class Avatar(val parent: Id)
    }

    @Serializable
    @Resource("me")
    internal class Me(val parent: UsersResource = UsersResource())
}
```
Null query params are omitted from the URL, so optional filters are just nullable constructor params.

## 3. Api: `data/remote/UserApi.kt`
```kotlin
package com.acme.gym.feature.profile.data.remote

import com.acme.gym.core.common.result.DataResult
import com.acme.gym.core.network.safeCall
import io.ktor.client.HttpClient
import io.ktor.client.plugins.resources.delete
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.put
import io.ktor.client.request.setBody

/** Raw HTTP calls of the profile feature. Only the remote data source uses this class. */
internal class UserApi(private val client: HttpClient) {

    suspend fun getMe(): DataResult<UserDto> =
        safeCall { client.get(UsersResource.Me()) }

    suspend fun getUsers(page: Int, query: String?): DataResult<PageDto<UserDto>> =
        safeCall { client.get(UsersResource(page = page, query = query)) }

    suspend fun getUser(id: Long): DataResult<UserDto> =
        safeCall { client.get(UsersResource.Id(id = id)) }

    suspend fun updateUser(id: Long, body: UpdateUserRequestDto): DataResult<UserDto> =
        safeCall { client.put(UsersResource.Id(id = id)) { setBody(body) } }

    suspend fun deleteUser(id: Long): DataResult<Unit> =
        safeCall { client.delete(UsersResource.Id(id = id)) }
}
```

## 4. Mappers and domain model
`domain/model/User.kt`:
```kotlin
package com.acme.gym.feature.profile.domain.model

import kotlin.time.Instant

internal data class User(          // SDK mode: public data class
    val id: Long,
    val fullName: String,
    val email: String,
    val avatarUrl: String?,
    val plan: Plan,
    val createdAt: Instant,
)

internal enum class Plan { FREE, PREMIUM }   // SDK mode: public
```
`data/mapper/UserMappers.kt`:
```kotlin
package com.acme.gym.feature.profile.data.mapper

import com.acme.gym.feature.profile.data.remote.PlanDto
import com.acme.gym.feature.profile.data.remote.UserDto
import com.acme.gym.feature.profile.domain.model.Plan
import com.acme.gym.feature.profile.domain.model.User
import kotlin.time.Instant

internal fun UserDto.toDomain(): User = User(
    id = id,
    fullName = fullName,
    email = email,
    avatarUrl = avatarUrl,
    plan = when (plan) {
        PlanDto.FREE -> Plan.FREE
        PlanDto.PREMIUM -> Plan.PREMIUM
    },
    createdAt = Instant.parse(createdAt),
)
```
`Instant.parse` throws on malformed input. The remote data source maps with `mapDto { }` from core.network, which turns a throwing mapper into `AppError.Network.Serialization`, so the "never throw" rule holds. If the server sends optional or sloppy dates, use `runCatching { Instant.parse(it) }.getOrNull()` and a nullable field instead.

## 5. Remote data source: `data/remote/UserRemoteDataSource.kt`
```kotlin
package com.acme.gym.feature.profile.data.remote

import com.acme.gym.core.common.result.DataResult
import com.acme.gym.core.network.mapDto
import com.acme.gym.feature.profile.data.mapper.toDomain
import com.acme.gym.feature.profile.domain.model.User

/** What the repository needs from the server, already in domain terms. */
internal interface UserRemoteDataSource {
    suspend fun getUsers(page: Int = 1, query: String? = null): DataResult<Page<User>>
    suspend fun getMe(): DataResult<User>
    suspend fun getUser(id: Long): DataResult<User>
    suspend fun updateName(id: Long, fullName: String): DataResult<User>
    suspend fun delete(id: Long): DataResult<Unit>
}

internal class UserRemoteDataSourceImpl(private val api: UserApi) : UserRemoteDataSource {
    override suspend fun getUsers(page: Int, query: String?): DataResult<List<User>> =
        api.getUsers(page, query).mapDto { dto -> Page(items = dto.items.map { it.toDomain() }, page = dto.page, hasMore = dto.page < dto.totalPages) }
    override suspend fun getMe(): DataResult<User> = api.getMe().mapDto { it.toDomain() }
    override suspend fun getUser(id: Long): DataResult<User> = api.getUser(id).mapDto { it.toDomain() }
    override suspend fun updateName(id: Long, fullName: String): DataResult<User> =
        api.updateUser(id, UpdateUserRequestDto(fullName = fullName)).mapDto { it.toDomain() }
    override suspend fun delete(id: Long): DataResult<Unit> = api.deleteUser(id)
}
```

## 6. Koin: `di/ProfileModule.kt`
```kotlin
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf

val profileModule: Module = module {
    // tezca-kmp:data (APIs, databases, DAOs, data sources, repositories)
    singleOf(::UserApi)
    singleOf(::UserRemoteDataSourceImpl) { bind<UserRemoteDataSource>() }
    ...
}
```

## 7. Special cases

**Authentication.** The shared client already has Ktor's `Auth { bearer }` wired to `TokenProvider`. Don't set `Authorization` by hand. A login feature provides a real `TokenProvider` with make-preferences, which stores the tokens, and overrides the default in its Koin module:
```kotlin
single<TokenProvider> { SessionTokenProvider(get()) }              // or SessionTokenProvider(get(), get()) with a refresh endpoint
```
Endpoints that must not carry the token (login, register, refresh) use a second, named client whose `TokenProvider` never returns tokens. Otherwise a 401 from login would make the Auth plugin call `refreshTokens()`, which clears the session or loops:
```kotlin
import org.koin.core.qualifier.named

internal val PublicHttpClient = named("publicHttpClient")

// tezca-kmp:data
single(PublicHttpClient) { HttpClientFactory.create(engine = get(), config = get(), tokenProvider = NoTokenProvider()) }
single { AuthApi(get(PublicHttpClient)) }      // singleOf can't pick a qualifier per parameter
```

**401 on login means wrong credentials, not an expired session.** `safeCall` maps 401 to `AppError.Network.Unauthorized`, whose default text is "session expired". Translate it in the auth Api:
```kotlin
internal object AuthErrorCodes { const val INVALID_CREDENTIALS = "invalid_credentials" }

suspend fun login(body: LoginRequestDto): DataResult<LoginResponseDto> =
    when (val result = safeCall<LoginResponseDto> { client.post(AuthResource.Login()) { setBody(body) } }) {
        is DataResult.Failure if result.error == AppError.Network.Unauthorized ->
            DataResult.Failure(AppError.Domain(AuthErrorCodes.INVALID_CREDENTIALS))
        else -> result
    }
```
The login ViewModel maps `invalid_credentials` to its own string (`login_error_invalid_credentials`).

**Login + refresh with tokens in the response.** Do the login in `AuthApi` (public client), save the session through `SessionPreferencesDataSource` (make-preferences), and, if the backend has a refresh endpoint, let `SessionTokenProvider.refreshTokens()` call it through `AuthRemoteDataSource.refresh(refreshToken): DataResult<Session>` on the public client. Without a refresh endpoint, use make-preferences' no-refresh variant.

**Different host.** Pass an absolute URL. `defaultRequest` only fills in what's missing:
```kotlin
safeCall<Unit> { client.put("https://uploads.acme.com/${path}") { setBody(bytes) } }
```

**Multipart upload.**
```kotlin
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders

suspend fun uploadAvatar(id: Long, bytes: ByteArray, mimeType: String): DataResult<UserDto> = safeCall {
    client.submitFormWithBinaryData(
        url = "users/$id/avatar",
        formData = formData {
            append("file", bytes, Headers.build {
                append(HttpHeaders.ContentType, mimeType)
                append(HttpHeaders.ContentDisposition, "filename=\"avatar\"")
            })
        },
    )
}
```

**Server error body → domain error.** If the backend returns `{ "code": "coupon_expired" }` with a 4xx status, decode it in the Api and map it to `AppError.Domain`:
```kotlin
@Serializable internal data class ErrorBodyDto(val code: String, val message: String? = null)

suspend fun redeem(code: String): DataResult<CouponDto> {
    val result = safeCall<CouponDto> { client.post(CouponsResource.Redeem()) { setBody(RedeemRequestDto(code)) } }
    val error = (result as? DataResult.Failure)?.error as? AppError.Network.Client ?: return result
    val body = error.body?.let { runCatching { NetworkJson.decodeFromString<ErrorBodyDto>(it) }.getOrNull() }
    return if (body != null) DataResult.Failure(AppError.Domain(body.code, body.message)) else result
}
```

**Pagination.** Return the page DTO from the Api. The remote data source maps it to a domain `Page<T>` (`internal data class Page<T>(val items: List<T>, val page: Int, val hasMore: Boolean)` in `domain/model`). The repository and ViewModel side (refresh = page 1 replaces the cache, load-more appends) is in make-repository's templates, section E.

**Headers per request.** Use `client.get(Resource()) { header("X-Client-Version", version) }`. For headers on every request, add them in `HttpClientFactory`'s `defaultRequest`, which is shared infrastructure, so tell the user.
