# make-repository templates

The examples use the feature `profile` and the domain model `User`, with the data sources from make-api and make-database.

## Interface: `domain/repository/UserRepository.kt`
```kotlin
package com.acme.gym.feature.profile.domain.repository

import com.acme.gym.core.common.result.DataResult
import com.acme.gym.feature.profile.domain.model.User
import kotlinx.coroutines.flow.Flow

internal interface UserRepository {
    fun observeUsers(): Flow<List<User>>
    fun observeUser(id: Long): Flow<User?>
    suspend fun refreshUsers(force: Boolean = false): DataResult<Unit>
    suspend fun refreshUser(id: Long, force: Boolean = false): DataResult<Unit>
    suspend fun updateName(id: Long, fullName: String): DataResult<User>
    suspend fun delete(id: Long): DataResult<Unit>
}
```

## A. Offline-first (default): `data/repository/UserRepositoryImpl.kt`
```kotlin
package com.acme.gym.feature.profile.data.repository

import com.acme.gym.core.common.dispatchers.DispatcherProvider
import com.acme.gym.core.common.result.DataResult
import com.acme.gym.core.common.result.asEmpty
import com.acme.gym.core.common.result.onSuccess
import com.acme.gym.core.database.sync.SyncMetadataStore
import com.acme.gym.feature.profile.data.local.UserLocalDataSource
import com.acme.gym.feature.profile.data.remote.UserRemoteDataSource
import com.acme.gym.feature.profile.domain.model.User
import com.acme.gym.feature.profile.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.minutes

/**
 * Room is the single source of truth: the UI observes the database, and refresh pulls from the
 * server into it. Fresh-enough data (TTL) is not fetched again unless forced (pull-to-refresh).
 */
internal class UserRepositoryImpl(
    private val remote: UserRemoteDataSource,
    private val local: UserLocalDataSource,
    private val syncMetadata: SyncMetadataStore,
    private val dispatchers: DispatcherProvider,
) : UserRepository {

    override fun observeUsers(): Flow<List<User>> = local.observeUsers()

    override fun observeUser(id: Long): Flow<User?> = local.observeUser(id)

    override suspend fun refreshUsers(force: Boolean): DataResult<Unit> = withContext(dispatchers.io) {
        if (!force && !syncMetadata.isStale(USERS_KEY, LIST_TTL)) return@withContext DataResult.Success(Unit)
        remote.getUsers(page = 1)
            .onSuccess { firstPage ->
                local.replaceAll(firstPage.items)
                syncMetadata.markSynced(USERS_KEY)
            }
            .asEmpty()
    }

    override suspend fun refreshUser(id: Long, force: Boolean): DataResult<Unit> = withContext(dispatchers.io) {
        val key = userKey(id)
        if (!force && !syncMetadata.isStale(key, ITEM_TTL)) return@withContext DataResult.Success(Unit)
        remote.getUser(id)
            .onSuccess { user ->
                local.upsert(user)
                syncMetadata.markSynced(key)
            }
            .asEmpty()
    }

    override suspend fun updateName(id: Long, fullName: String): DataResult<User> = withContext(dispatchers.io) {
        remote.updateName(id, fullName).onSuccess { local.upsert(it) }
    }

    override suspend fun delete(id: Long): DataResult<Unit> = withContext(dispatchers.io) {
        remote.delete(id).onSuccess {
            local.delete(id)
            syncMetadata.invalidate(userKey(id))
        }
    }

    private companion object {
        const val USERS_KEY = "profile.users"
        fun userKey(id: Long) = "profile.user:$id"
        val LIST_TTL = 15.minutes
        val ITEM_TTL = 5.minutes
    }
}
```
`onSuccess` is `inline`, so the lambda can call `suspend` functions.

## B. Remote-only
```kotlin
internal class PaymentRepositoryImpl(
    private val remote: PaymentRemoteDataSource,
    private val dispatchers: DispatcherProvider,
) : PaymentRepository {
    // No cache on purpose: the user asked for always-fresh data.
    override suspend fun getPlans(): DataResult<List<Plan>> = withContext(dispatchers.io) { remote.getPlans() }

    override suspend fun confirmPayment(intentId: String): DataResult<PaymentReceipt> =
        withContext(dispatchers.io) { remote.confirm(intentId) }

    /** Poll-based "live" value when the UI needs a stream without a socket. */
    override fun observePaymentStatus(intentId: String): Flow<DataResult<PaymentStatus>> = flow {
        while (currentCoroutineContext().isActive) {
            val result = remote.status(intentId)
            emit(result)
            if ((result as? DataResult.Success)?.data?.isFinal == true) break
            delay(3.seconds)
        }
    }.flowOn(dispatchers.io)
}
```
The interface for remote-only exposes `suspend fun get…(): DataResult<T>` instead of `observe…` + `refresh…`.

## C. Local-only
```kotlin
internal class WorkoutDraftRepositoryImpl(
    private val local: WorkoutDraftLocalDataSource,
    private val dispatchers: DispatcherProvider,
) : WorkoutDraftRepository {
    override fun observeDrafts(): Flow<List<WorkoutDraft>> = local.observeDrafts()
    override suspend fun save(draft: WorkoutDraft): DataResult<Unit> = withContext(dispatchers.io) {
        runCatching { local.upsert(draft) }.fold(
            onSuccess = { DataResult.Success(Unit) },
            onFailure = { DataResult.Failure(AppError.Local.Database(it.message)) },
        )
    }
}
```
`runCatching` also catches `CancellationException`. That's acceptable here because the block isn't suspending across cancellation points that matter. For longer work, catch specific exceptions instead.

## D. Offline writes (only when the user asks for them)
Write locally first with a `pending_sync` flag, then try the server. Leave unsent rows for the next refresh.
```kotlin
override suspend fun updateName(id: Long, fullName: String): DataResult<Unit> = withContext(dispatchers.io) {
    local.updateName(id, fullName, pendingSync = true)          // UI updates immediately
    remote.updateName(id, fullName)
        .onSuccess { serverUser -> local.upsert(serverUser); local.markSynced(id) }
        .asEmpty()
        .let { result -> if (result is DataResult.Failure && result.error is AppError.Network.NoInternet) DataResult.Success(Unit) else result }
}

/** Called from refreshUsers() before fetching, or by a periodic sync. */
suspend fun pushPendingChanges() {
    local.getPending().forEach { pending -> remote.updateName(pending.id, pending.fullName).onSuccess { local.markSynced(pending.id) } }
}
```
This needs an extra `pending_sync` column (make-database), and a conflict policy agreed with the user (last write wins by default).

## E. Paginated lists (offline-first)
Page 1 replaces the cache on refresh; further pages append. The repository remembers whether more pages exist.
```kotlin
internal interface WorkoutRepository {
    fun observeWorkouts(): Flow<List<Workout>>
    suspend fun refreshWorkouts(force: Boolean = false): DataResult<Unit>      // page 1, replaces cache
    suspend fun loadMoreWorkouts(): DataResult<Boolean>                         // next page; true = more remain
}

internal class WorkoutRepositoryImpl(…) : WorkoutRepository {
    private val paging = Mutex()
    private var nextPage = 2
    private var hasMore = true

    override suspend fun refreshWorkouts(force: Boolean): DataResult<Unit> = withContext(dispatchers.io) {
        if (!force && !syncMetadata.isStale(KEY, TTL)) return@withContext DataResult.Success(Unit)
        paging.withLock {
            remote.getWorkouts(page = 1).onSuccess { page ->
                local.replaceAll(page.items)
                nextPage = 2
                hasMore = page.hasMore
                syncMetadata.markSynced(KEY)
            }.asEmpty()
        }
    }

    override suspend fun loadMoreWorkouts(): DataResult<Boolean> = withContext(dispatchers.io) {
        paging.withLock {
            if (!hasMore) return@withLock DataResult.Success(false)
            remote.getWorkouts(page = nextPage).onSuccess { page ->
                local.upsertAll(page.items)
                nextPage++
                hasMore = page.hasMore
            }.map { it.hasMore }
        }
    }
}
```
The ViewModel keeps `isLoadingMore` / `canLoadMore` / `loadMoreError` in its state, triggers `LoadMore` when the last visible item is reached (a footer item that calls `onAction(LoadMore)` from a `LaunchedEffect`), and shows a retry button in the footer on failure. For very large or infinite lists, consider AndroidX Paging 3 multiplatform (`androidx.paging:paging-common` + `paging-compose`) with a `RemoteMediator`. Use it only when the user wants it, because it's a heavier dependency.

## Koin
```kotlin
// tezca-kmp:data
singleOf(::UserRepositoryImpl) { bind<UserRepository>() }
```
