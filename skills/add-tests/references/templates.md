# add-tests templates

The examples use the `profile` feature from the make-* templates. Imports are shown once per section.

## Contents
1. Api (MockEngine)
2. DAO / local data source (in-memory Room)
3. Preferences (DataStore on a temp file)
4. Fakes
5. Repository
6. Use case
7. ViewModel
8. Koin graph

## 1. Api: `data/remote/UserApiTest.kt`
```kotlin
import com.acme.gym.core.common.result.AppError
import com.acme.gym.core.common.result.DataResult
import com.acme.gym.core.testing.mockHttpClient
import com.acme.gym.core.testing.respondJson
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UserApiTest {

    @Test
    fun getUser_success_parsesDtoAndCallsRightPath() = runTest {
        var method: HttpMethod? = null
        var path: String? = null
        val api = UserApi(mockHttpClient { request ->
            method = request.method
            path = request.url.encodedPath
            respondJson(USER_JSON)
        })

        val result = api.getUser(42)

        assertEquals(HttpMethod.Get, method)
        assertEquals("/users/42", path)
        val dto = assertIs<DataResult.Success<UserDto>>(result).data
        assertEquals("Ana López", dto.fullName)
        assertEquals(PlanDto.PREMIUM, dto.plan)
    }

    @Test
    fun getUsers_queryParams_areSent() = runTest {
        var query: String? = null
        val api = UserApi(mockHttpClient { request ->
            query = request.url.encodedQuery
            respondJson("""{"items":[],"page":2,"total_pages":2}""")
        })
        api.getUsers(page = 2, query = "ana")
        assertEquals("page=2&query=ana", query)
    }

    @Test
    fun updateUser_sendsJsonBody() = runTest {
        var body: String? = null
        val api = UserApi(mockHttpClient { request ->
            body = (request.body as TextContent).text
            respondJson(USER_JSON)
        })
        api.updateUser(42, UpdateUserRequestDto(fullName = "Ana"))
        assertEquals("""{"full_name":"Ana"}""", body)
    }

    @Test
    fun getUser_404_mapsToNotFound() = runTest {
        val api = UserApi(mockHttpClient { respondError(HttpStatusCode.NotFound) })
        assertEquals(AppError.Network.NotFound, assertIs<DataResult.Failure>(api.getUser(1)).error)
    }

    @Test
    fun getUser_malformedJson_mapsToSerialization() = runTest {
        val api = UserApi(mockHttpClient { respondJson("""{"id":"not-a-number"}""") })
        assertEquals(AppError.Network.Serialization, assertIs<DataResult.Failure>(api.getUser(1)).error)
    }

    private companion object {
        // Paste a real response from the backend here; it's what catches @SerialName mistakes.
        const val USER_JSON = """{"id":42,"full_name":"Ana López","email":"ana@acme.com","avatar_url":null,"plan":"premium","created_at":"2026-01-10T12:00:00Z"}"""
    }
}
```
`encodedPath` includes the leading `/` from the base URL `https://api.test/`.

## 2. DAO / local data source (in-memory Room)
```kotlin
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

class UserLocalDataSourceTest {
    private lateinit var database: ProfileDatabase
    private lateinit var dataSource: UserLocalDataSourceImpl

    @BeforeTest
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder<ProfileDatabase> { ProfileDatabaseConstructor.initialize() }
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        dataSource = UserLocalDataSourceImpl(database.userDao())
    }

    @AfterTest
    fun tearDown() = database.close()

    @Test
    fun replaceAll_emitsNewListSortedByName() = runTest {
        dataSource.observeUsers().test {
            assertEquals(emptyList(), awaitItem())
            dataSource.replaceAll(listOf(user(2, "Zoe"), user(1, "Ana")))
            assertEquals(listOf("Ana", "Zoe"), awaitItem().map { it.fullName })
            cancelAndIgnoreRemainingEvents()
        }
    }
}

internal fun user(id: Long, name: String = "User $id", plan: Plan = Plan.FREE) =
    User(id, name, "u$id@acme.com", null, plan, Instant.fromEpochMilliseconds(1_000))
```
Room's in-memory builder is reified. If `Room.inMemoryDatabaseBuilder<T>(factory)` doesn't resolve on one platform, put the builder in a small `expect fun inMemoryProfileDatabase(): ProfileDatabase` with actuals in `androidHostTest` and `iosTest`. For JVM host tests, add `sqlite-bundled-jvm` as described in SKILL.md.

## 3. Preferences (DataStore on a temp file)
```kotlin
import com.acme.gym.core.datastore.createDataStore
import kotlin.random.Random

class SessionPreferencesDataSourceTest {
    // A unique file per test. SystemTemporaryDirectory works on JVM and iOS.
    private val path = kotlinx.io.files.SystemTemporaryDirectory.toString() + "/test-${Random.nextLong()}.preferences_pb"
    private val dataSource = SessionPreferencesDataSourceImpl(createDataStore { path })

    @Test
    fun session_roundTrip_andClear() = runTest {
        assertNull(dataSource.currentSession())
        dataSource.saveSession(Session(accessToken = "access", refreshToken = "refresh", userId = 42, displayName = "Ana"))
        assertEquals("access", dataSource.currentSession()?.accessToken)
        dataSource.clearSession()
        assertNull(dataSource.currentSession())
    }
}
```
In SDK mode `createDataStore` is internal, which is fine because tests live in the same module. In app mode it's public in `:core:datastore`; add `implementation(projects.core.datastore)` to the module's `commonTest` dependencies if it's missing.

## 4. Fakes: `commonTest/.../fake/`
```kotlin
internal class FakeUserRemoteDataSource : UserRemoteDataSource {
    var users: DataResult<List<User>> = DataResult.Success(emptyList())
    var getUsersCalls = 0
    override suspend fun getUsers(page: Int, query: String?): DataResult<Page<User>> {
        getUsersCalls++
        return users.map { Page(items = it, page = page, hasMore = false) }
    }
    override suspend fun getMe(): DataResult<User> = TODO("not needed")
    override suspend fun getUser(id: Long) = (users as? DataResult.Success)?.data?.firstOrNull { it.id == id }
        ?.let { DataResult.Success(it) } ?: DataResult.Failure(AppError.Network.NotFound)
    override suspend fun updateName(id: Long, fullName: String) = DataResult.Success(user(id, fullName))
    override suspend fun delete(id: Long) = DataResult.Success(Unit)
}

internal class FakeUserLocalDataSource : UserLocalDataSource {
    val stored = MutableStateFlow<List<User>>(emptyList())
    override fun observeUsers() = stored
    override fun observeUser(id: Long) = stored.map { list -> list.firstOrNull { it.id == id } }
    override suspend fun getUser(id: Long) = stored.value.firstOrNull { it.id == id }
    override suspend fun upsert(user: User) { stored.value = stored.value.filterNot { it.id == user.id } + user }
    override suspend fun replaceAll(users: List<User>) { stored.value = users }
    override suspend fun delete(id: Long) { stored.value = stored.value.filterNot { it.id == id } }
}

/** SyncMetadataStore needs a DAO; this one is in memory. */
internal class FakeSyncMetadataDao : SyncMetadataDao {
    private val rows = mutableMapOf<String, SyncMetadataEntity>()
    override suspend fun get(key: String) = rows[key]
    override suspend fun upsert(entity: SyncMetadataEntity) { rows[entity.key] = entity }
    override suspend fun delete(key: String) { rows.remove(key) }
    override suspend fun clear() = rows.clear()
}

internal class FakeClock(var now: Instant = Instant.fromEpochMilliseconds(0)) : Clock {
    override fun now(): Instant = now
}

/** Fake of the domain interface, for use-case and ViewModel tests. */
internal class FakeUserRepository : UserRepository {
    val users = MutableStateFlow<List<User>>(emptyList())
    var refreshResult: DataResult<Unit> = DataResult.Success(Unit)
    var refreshCalls = 0
    var updateNameCalls = 0

    override fun observeUsers(): Flow<List<User>> = users
    override fun observeUser(id: Long): Flow<User?> = users.map { list -> list.firstOrNull { it.id == id } }
    override suspend fun refreshUsers(force: Boolean): DataResult<Unit> = refreshResult.also { refreshCalls++ }
    override suspend fun refreshUser(id: Long, force: Boolean): DataResult<Unit> = refreshResult
    override suspend fun updateName(id: Long, fullName: String): DataResult<User> {
        updateNameCalls++
        val updated = user(id, fullName)
        users.value = users.value.filterNot { it.id == id } + updated
        return DataResult.Success(updated)
    }
    override suspend fun delete(id: Long): DataResult<Unit> = DataResult.Success(Unit).also { users.value = users.value.filterNot { it.id == id } }
}
```

## 5. Repository
```kotlin
class UserRepositoryImplTest {
    private val remote = FakeUserRemoteDataSource()
    private val local = FakeUserLocalDataSource()
    private val clock = FakeClock()
    private val dispatchers = TestDispatcherProvider()
    private val repository = UserRepositoryImpl(remote, local, SyncMetadataStore(FakeSyncMetadataDao(), clock), dispatchers)

    @Test
    fun refreshUsers_firstTime_fetchesAndStores() = runTest(dispatchers.testDispatcher) {
        remote.users = DataResult.Success(listOf(user(1)))
        assertIs<DataResult.Success<Unit>>(repository.refreshUsers())
        assertEquals(listOf(user(1)), local.stored.value)
    }

    @Test
    fun refreshUsers_cacheFresh_skipsNetwork() = runTest(dispatchers.testDispatcher) {
        repository.refreshUsers()
        clock.now += 1.minutes
        repository.refreshUsers()
        assertEquals(1, remote.getUsersCalls)
    }

    @Test
    fun refreshUsers_forced_fetchesEvenIfFresh() = runTest(dispatchers.testDispatcher) {
        repository.refreshUsers()
        repository.refreshUsers(force = true)
        assertEquals(2, remote.getUsersCalls)
    }

    @Test
    fun refreshUsers_failure_keepsCacheAndReturnsError() = runTest(dispatchers.testDispatcher) {
        local.stored.value = listOf(user(1))
        remote.users = DataResult.Failure(AppError.Network.NoInternet)
        assertEquals(AppError.Network.NoInternet, assertIs<DataResult.Failure>(repository.refreshUsers()).error)
        assertEquals(listOf(user(1)), local.stored.value)
    }
}
```

## 6. Use case
```kotlin
class UpdateUserNameUseCaseTest {
    @Test
    fun invoke_tooShortName_returnsDomainErrorWithoutCallingRepository() = runTest {
        val repository = FakeUserRepository()
        val result = UpdateUserNameUseCase(repository)(id = 1, fullName = " a ")
        assertEquals(AppError.Domain("invalid_name"), assertIs<DataResult.Failure>(result).error)
        assertEquals(0, repository.updateNameCalls)
    }
}
```
`FakeUserRepository` implements the domain interface with the same knobs as the data-source fakes.

## 7. ViewModel
```kotlin
class UserListViewModelTest {
    private val dispatchers = TestDispatcherProvider()
    private val repository = FakeUserRepository()

    @BeforeTest fun setUp() = setMainDispatcher(dispatchers.testDispatcher)
    @AfterTest fun tearDown() = resetMainDispatcher()

    private fun viewModel() = UserListViewModel(ObserveUsersUseCase(repository), RefreshUsersUseCase(repository))

    @Test
    fun state_afterRefresh_showsUsers() = runTest(dispatchers.testDispatcher) {
        repository.users.value = listOf(user(1, "Ana"))
        viewModel().state.test {
            val loaded = awaitUntil { !it.isLoading }
            assertEquals(listOf("Ana"), loaded.users.map { it.fullName })
            assertEquals(1, repository.refreshCalls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onAction_userClicked_emitsOpenUserOnce() = runTest(dispatchers.testDispatcher) {
        val vm = viewModel()
        vm.events.test {
            vm.onAction(UserListAction.UserClicked(7))
            assertEquals(UserListEvent.OpenUser(7), awaitItem())
            expectNoEvents()
        }
    }
}
```
State tests shouldn't depend on how many intermediate states the ViewModel emits, so wait for the state you care about instead of hardcoding `skipItems` counts that break on harmless refactors. Put this helper in `:core:testing` (or the module's `commonTest`):
```kotlin
suspend fun <T> ReceiveTurbine<T>.awaitUntil(predicate: (T) -> Boolean): T {
    while (true) { val item = awaitItem(); if (predicate(item)) return item }
}
```

## 8. Koin graph
Generated by start-kmp: `shared/src/androidHostTest/.../di/KoinGraphTest.kt` (app) and `SdkKoinGraphTest` (SDK). When a definition takes a new platform type or primitive that Koin can't see, add it to `extraTypes`, e.g. `extraTypes = listOf(Context::class, HttpClientEngine::class, String::class, Boolean::class)`. When a ViewModel takes route params through `parametersOf`, add the param type too (`Long::class`).
