---
name: make-api
description: Create or update Ktor API calls in a Kotlin Multiplatform project and expose them as the feature's remote data source. Covers @Serializable DTOs, type-safe @Resource routes, an Api class using the shared HttpClient and safeCall → DataResult, a RemoteDataSource interface + Impl mapping DTOs to domain models, and the Koin bindings. Use it whenever the user wants to call an endpoint, add or change a REST call, consume a backend/server API, map a JSON response, add query params, headers, upload or auth to a request, or "connect the feature to the server" in a KMP/Compose Multiplatform app or SDK, even if they never say "Ktor". Also use it when another skill (make-repository, make-screen) needs a remote data source that doesn't exist yet.
---

# make-api: Ktor remote data source

Turns an endpoint description (method, path, request and response JSON) into four pieces:
- DTOs
- routes
- an Api class
- a remote data source that the repository consumes

The data source returns domain models wrapped in `DataResult`, so nothing Ktor-specific leaves `data/remote`.

## Before you start
1. Read `../kmp-architecture/SKILL.md` (the conventions skill next to this one) if you haven't in this session.
2. Read `.tezca-kmp.json` to learn `mode`, `basePackage` and `rootModule`. Find the feature root:
   - **App mode:** `feature/<name>/src/commonMain/kotlin/<basePath>/feature/<pkg>/`
   - **SDK mode:** `<sdkModule>/src/commonMain/kotlin/<basePath>/<pkg>/`

   If the feature doesn't exist, run the `new-feature` skill first, or ask whether the call belongs to an existing feature.
3. Get from the user, or from a spec, curl command, Postman collection or OpenAPI file they point to:
   - method and relative path
   - path and query params
   - request body
   - response body, with one real example if possible
   - auth requirement and error responses

   Don't invent fields. If the JSON is unknown, ask, or generate a clearly marked minimal DTO.
4. Look in `data/remote/` for an existing `<X>Api`, `<X>Resources` and DTOs. **Update in place**: add the endpoint to the existing classes instead of creating parallel ones. Keep existing function signatures unless the user asked to change them, and update every call site if you do.

## Steps
Templates are in `references/templates.md`. Copy the shape, rename, and adapt the fields.

1. **DTOs** go in `data/remote/<X>Dto.kt` (one file per aggregate):
   - `@Serializable internal data class`, with `@SerialName` for every field whose JSON name isn't the Kotlin name.
   - Nullable plus a default for optional fields, so a missing key doesn't crash. Enums as `@Serializable enum class` with `@SerialName` per value.
   - Request bodies get their own `…RequestDto`. Don't reuse response DTOs for requests.
   - Dates arrive as `String`. Parse them in the mapper with `Instant.parse` / `LocalDate.parse`.
2. **Routes** go in `data/remote/<X>Resources.kt`, one `@Serializable @Resource("path")` class per path:
   - Use nested classes for sub-paths (`UsersResource.Id`) and constructor params for query params.
   - Paths are **relative** (no leading `/`) so they resolve under `AppConfig.baseUrl`.
3. **Api** goes in `data/remote/<X>Api.kt`: `internal class <X>Api(private val client: HttpClient)`.
   - Write one `suspend fun` per endpoint, each body being `safeCall<T> { client.get(Resource(...)) }`.
   - Use the resource-aware extensions from `io.ktor.client.plugins.resources` (`get`, `post`, `put`, `patch`, `delete`), not `io.ktor.client.request.get`. The wrong import compiles but sends the class name as a path.
   - Send bodies with `setBody(dto)`. JSON content type is already the default.
   - For endpoints returning no body, use `safeCall<Unit>`.
4. **Mappers** go in `data/mapper/<X>Mappers.kt`: `internal fun <X>Dto.toDomain(): <X>` (and `<X>.toRequestDto()` when needed). Create the domain model in `domain/model/<X>.kt` if it doesn't exist. It's a plain data class with domain types (`Instant`, enums, value classes), not raw strings.
5. **Remote data source** goes in `data/remote/<X>RemoteDataSource.kt`: an interface plus `internal class <X>RemoteDataSourceImpl(private val api: <X>Api)`.
   - Every function returns `DataResult<Domain>` via `api.call().mapDto { it.toDomain() }` (`mapDto` from core.network turns a throwing mapper into a Serialization error).
   - This interface is what repositories and fakes depend on.
6. **Koin**: in `di/<Feature>Module.kt`, under `// tezca-kmp:data`, add:
   ```kotlin
   singleOf(::<X>Api)
   singleOf(::<X>RemoteDataSourceImpl) { bind<<X>RemoteDataSource>() }
   ```
   Imports: `org.koin.core.module.dsl.singleOf`, `org.koin.core.module.dsl.bind`. The `HttpClient` comes from `networkModule`.
7. **Special cases**, covered in `references/templates.md`:
   - Auth headers come from `TokenProvider`; don't add them by hand.
   - A different base URL means using an absolute URL or a second, named client.
   - Multipart uploads.
   - Paginated responses.
   - Endpoints that don't need auth.
   - Mapping a server error body to `AppError.Domain`.

## Verify
- Run `./gradlew :<module>:compileAndroidMain :<module>:compileKotlinIosSimulatorArm64`. The module is `feature:<name>`, or the SDK module.
- Offer an Api test with `MockEngine` through the `add-tests` skill. It checks method, path, query and body, and parses a real response sample. It's the cheapest way to catch DTO and `@SerialName` mistakes.
- Tell the user which files changed and which endpoints are now available to `make-repository`.
