---
name: make-repository
description: Create or update a repository in a Kotlin Multiplatform project that combines the remote data source (Ktor) and the local data source (Room / DataStore). By default it's offline-first: Room is the single source of truth, refresh() syncs from the server with a freshness TTL, and writes go to the server and then the cache. It can also be remote-only ("don't do this offline", "no offline support", "online only") or local-only. Generates the domain interface, the internal Impl, and the Koin binding. Use it whenever the user wants a repository, wants to sync or cache server data, make a feature work offline, combine API and database, or "connect the data layer", even if they don't say "repository".
---

# make-repository: offline-first repository

The repository is the data layer's public face. Use cases depend on its **interface**, which lives in `domain/repository`. The **implementation** in `data/repository` decides where data comes from.

## Before you start
1. Read `../kmp-architecture/SKILL.md` (section 8 is the offline-first contract) and `.tezca-kmp.json`, then locate the feature root.
2. Pick the **strategy** from what the user said:

   | User says | Strategy | Template |
   |---|---|---|
   | nothing about it / "offline" / "cache" | **offline-first** (default) | A |
   | "don't do this offline", "no offline support", "online only", "always fresh", "real-time" | **remote-only** | B |
   | "local only", "no API", "just on the device" | **local-only** | C |

   Strategy can be per operation. Payment confirmation, for example, is always remote, while the payment history list is offline-first. State the choice in one line, so the user can correct it.
3. Check which data sources exist (`data/remote/*RemoteDataSource`, `data/local/*LocalDataSource`, `*PreferencesDataSource`):
   - If one is missing, run `make-api` or `make-database` first, or ask.
   - If the repository already exists, add operations to it instead of creating another.

## Steps
1. **Interface** goes in `domain/repository/<X>Repository.kt` (`internal` in both modes; it's only used inside the feature and by the use cases):
   - Reads that the UI watches are `fun observe…(): Flow<…>`.
   - Sync is `suspend fun refresh…(force: Boolean = false): DataResult<Unit>`.
   - One-shot reads and commands are `suspend fun …(): DataResult<…>`.
   - Only domain models appear in signatures.
2. **Implementation** goes in `data/repository/<X>RepositoryImpl.kt` as `internal class`, from the template in `references/templates.md`. Rules for offline-first:
   - `observe…` returns the local data source's `Flow`, never the network.
   - `refresh…` asks `SyncMetadataStore.isStale(key, ttl)`, fetches remotely and saves through the local data source in one transactional call (`replaceAll`). It calls `markSynced(key)` only on success.
     - Keys are stable strings per resource: `"profile.users"`, `"profile.user:$id"`.
     - TTL defaults to 15 minutes for lists. Ask if the data is very dynamic.
   - Commands (`update…`, `delete…`) call the remote first and mirror a success locally, so the observed flow updates. Optimistic or queued offline writes are opt-in: only add them if the user asks for writes while offline (see template D).
   - Every function runs its I/O on `dispatchers.io` (`withContext`). Room is already off the main thread, but the mapping and network glue shouldn't be on it either.
   - Errors return as `DataResult.Failure`; they're never thrown. The flow from `observe…` never emits errors.
3. **Koin**, under `// tezca-kmp:data`: `singleOf(::<X>RepositoryImpl) { bind<<X>Repository>() }`. `SyncMetadataStore` and `DispatcherProvider` already come from core.
4. **Verify** with `./gradlew :<module>:compileAndroidMain :<module>:compileKotlinIosSimulatorArm64`. Then offer `add-tests` for the repository: fakes for both data sources, plus a cache-hit/cache-miss test with a fake clock in `SyncMetadataStore`.

## Output to the user
List the repository functions with their strategy, and say which use cases would naturally sit on top. `make-usecase` creates them.
