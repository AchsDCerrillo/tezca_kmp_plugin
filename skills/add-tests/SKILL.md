---
name: add-tests
description: Add or update tests for any layer of a Kotlin Multiplatform project. Ktor APIs use MockEngine through the real client config, Room DAOs use an in-memory database, DataStore preferences use a temp file, repositories use fake data sources plus the freshness TTL, use cases use fake repositories, ViewModels use Turbine on state and events with a test Main dispatcher, and the Koin graph is verified. Tests live in commonTest and run on the JVM and the iOS simulator. Use it whenever the user asks for tests, coverage, a test for a class or feature, TDD, "make sure this works", or after the make-* skills generate code they want covered, even if they only say "test it".
---

# add-tests: tests for every layer

Tests go in `commonTest` of the module under test, mirroring the main package. They run with:
- `./gradlew :<module>:testAndroidHostTest`: JVM, fast, use it while iterating.
- `./gradlew :<module>:iosSimulatorArm64Test`: Kotlin/Native, which catches platform differences.

`:core:testing` (app mode) or the SDK's `commonTest/…/core/testing` provides:
- `mockHttpClient { request -> respondJson(...) }`: the production `HttpClientFactory` on a `MockEngine`.
- `TestDispatcherProvider`.
- `setMainDispatcher` / `resetMainDispatcher`.
- `respondJson`.

Feature modules get `:core:testing`, kotlin-test, coroutines-test, Turbine, koin-test and ktor-client-mock through the `kmp.feature` convention.

## Steps
1. Read `../kmp-architecture/SKILL.md` (section 11) and `.tezca-kmp.json`. Find the classes to test, either the ones the user named or the ones just generated.
2. For each class, pick the matching recipe in `references/templates.md`:

   | Layer | Test with | Assert |
   |---|---|---|
   | `XApi` | `mockHttpClient` | method, path, query, body JSON sent; DTO parsed from a **real** sample; 401/404/500/timeouts → right `AppError` |
   | DAO / LocalDataSource | in-memory Room (`Room.inMemoryDatabaseBuilder` + `BundledSQLiteDriver`) | queries, ordering, upsert/replace, Flow re-emits after writes |
   | PreferencesDataSource | real DataStore on a temp file | defaults, round trip, clear |
   | Repository | `Fake…RemoteDataSource` + `Fake…LocalDataSource` + `SyncMetadataStore` on a fake DAO and fake clock | cache hit skips network, stale or forced refresh fetches and saves, failure keeps cache and returns error, remote-only paths |
   | UseCase | fake repositories | business rules, combinations, `AppError.Domain` codes |
   | ViewModel | fake repositories wired into real use cases (or fake use cases), `setMainDispatcher`, Turbine | initial state, state after each action, events emitted once |
   | Koin | `verify()` in androidHostTest | all definitions resolvable (already generated: `KoinGraphTest`) |

3. **Fakes** go in the module's `commonTest/.../fake/`. They implement the interface with in-memory state (`MutableStateFlow` for observed data) and expose knobs (`nextResult`, call counters).
   - Reuse a fake that already exists instead of writing a second one.
   - Move fakes that several modules need into `:core:testing`.
4. **Naming:** `UserRepositoryImplTest`, with functions named `refreshUsers_cacheFresh_skipsNetwork()`. Use `methodOrBehavior_condition_expectedResult`. Backtick names aren't supported on Kotlin/Native for all characters, so use underscores.
5. **Rules:**
   - Use `runTest` for every coroutine test.
   - Use `StandardTestDispatcher` via `TestDispatcherProvider` and the same dispatcher for Main. Call `advanceUntilIdle()` when you need queued work finished; it needs `@OptIn(ExperimentalCoroutinesApi::class)` on the test class.
   - Collect flows with Turbine (`flow.test { awaitItem() … cancelAndIgnoreRemainingEvents() }`). For `stateIn(WhileSubscribed)` state, the Turbine collector is the subscriber.
   - No real network, no sleeps, no shared mutable state between tests. Create objects in each test or in `@BeforeTest`.
   - API JSON samples go in raw string constants in the test, or in `commonTest/resources` read through a small expect/actual helper if they're large.
6. Run the tests. Fix failing production code only when the test shows a real bug; tell the user what the bug was. Report the count of tests and what they cover.

## Room on the JVM host test
`BundledSQLiteDriver` on Android ships natives for Android ABIs only. The `kmp.room` convention plugin therefore adds `androidx.sqlite:sqlite-bundled-jvm` to `androidHostTest`, so in-memory DAO tests in `commonTest` run on the JVM as well as on the iOS simulator. If a project was set up before that, add the dependency (catalog key `androidx-sqlite-bundled-jvm`).
