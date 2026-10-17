---
name: kmp-architecture
description: Architecture conventions for Kotlin Multiplatform (KMP) and Compose Multiplatform projects built with the tezca-kmp-development skills. Covers module and package layout, layer rules (Api / Dao → DataSource → Repository → UseCase → ViewModel → Screen), naming, DataResult and AppError, Koin wiring, dispatchers, offline-first sync, SDK vs app mode, and Swift-friendly API rules (SKIE). Use it whenever you write or review code in a KMP project that has a .tezca-kmp.json file. Also use it when the user asks where something belongs, how layers talk to each other, how to name a class, how errors flow to the UI, or how something should look from Swift, even if they don't say "architecture". Every make-* skill, new-feature and add-tests read it first.
---

# KMP architecture conventions

This skill is the single source of truth that keeps the other skills consistent. They are `start-kmp`, `new-feature`, `make-api`, `make-database`, `make-preferences`, `make-repository`, `make-usecase`, `make-viewmodel`, `make-screen` and `add-tests`. When code you write disagrees with this file, change the code, not the convention, unless the user explicitly asks for something else.

The architecture follows Android's [guide to app architecture](https://developer.android.com/topic/architecture):
- unidirectional data flow;
- a data layer that is the single source of truth;
- an optional domain layer of single-purpose use cases;
- ViewModels that expose immutable UI state as an observable `StateFlow`.

## 1. Find out what kind of project this is

Read `.tezca-kmp.json` at the project root, which `start-kmp` writes:

```json
{ "mode": "app", "basePackage": "com.acme.gym", "rootModule": ":shared", "sdkName": null,
  "interop": "skie", "features": ["onboarding", "payments"], "pluginVersion": "0.1.0" }
```

If the file is missing, infer the same facts:
- Read `kmp.basePackage` from `gradle.properties`.
- Read the modules from `settings.gradle.kts`.
- A `:feature:*` include means app mode; a module applying `kmp.sdk` means SDK mode.

Tell the user the project hasn't been set up with `start-kmp` and offer to run it.

| | **App mode** (Compose Multiplatform app) | **SDK mode** (library for Android + iOS consumers) |
|---|---|---|
| Unit of a feature | Gradle module `:feature:<name>` | Package `<base>.<feature>` inside the SDK module |
| Shared infrastructure | `:core:common`, `:core:network`, `:core:database`, `:core:datastore`, `:core:ui`, `:core:testing` | Same code, as packages `<base>.core.*` in the one SDK module |
| Entry point | `:shared` (`App()`, root `NavHost`, `initKoin`, iOS framework `Shared`) | `object <Facade>` (e.g. `FitCore`) + `initialize(...)`; the framework/Swift module has a different name (`FitCoreSdk`), because SKIE renames a type that has its module's name |
| Koin | global `startKoin` in `initKoin` | isolated `koinApplication` in `SdkKoin` (never global) |
| Visibility | normal Kotlin (public across modules) | `explicitApi()`; only the facade, feature accessors, use cases, domain models, `DataResult`/`AppError` and `AppConfig` are `public` |
| UI layer | ViewModel + Screen per feature | none; the consumer app builds its own UI |

## 2. Layers and dependency rule

```
UI (Route/Screen) ──▶ ViewModel ──▶ UseCase ──▶ Repository (interface, domain)
                                                     ▲
                                   RepositoryImpl (data) ──▶ RemoteDataSource ──▶ Api (Ktor)
                                                         └─▶ LocalDataSource  ──▶ Dao (Room) / DataStore
```

- **Dependencies point inward.** Domain (`model`, `repository` interfaces, `usecase`) knows nothing about Ktor, Room, DataStore or Compose. Data implements the domain interfaces. Presentation only talks to use cases.
- **A ViewModel never touches a repository or data source directly.** Even trivial reads go through a use case, so business rules have one home and ViewModels stay easy to fake.
- **DTOs (`@Serializable`) and entities (`@Entity`) never leave the data layer.** Mappers turn them into domain models at the data source / repository boundary.
- **Features never depend on other features.** Something two features need moves to a `:core:*` module, or the features talk through navigation callbacks wired in `:shared`. The usual case is the signed-in session: login (onboarding) writes it, and home, profile and payments read it. Put the session model, its preferences data source, the `TokenProvider` override and an `ObserveSessionUseCase` in a `:core:session` module (`kmp.library`, depends on `:core:datastore` and `:core:network`). Features then pass only ids through navigation.

## 3. Package layout of a feature

```
feature/<name>/src/commonMain/kotlin/<base>/feature/<pkg>/      (SDK mode: <sdkModule>/src/commonMain/kotlin/<base>/<pkg>/)
├── data/
│   ├── remote/   <X>Dto.kt, <X>Resources.kt, <X>Api.kt, <X>RemoteDataSource.kt (+ Impl)
│   ├── local/    <X>Entity.kt, <X>Dao.kt, <Feature>Database.kt, <X>LocalDataSource.kt (+ Impl), <X>PreferencesDataSource.kt (+ Impl)
│   ├── mapper/   <X>Mappers.kt   (Dto→Domain, Entity→Domain, Domain→Entity)
│   └── repository/ <X>RepositoryImpl.kt
├── domain/
│   ├── model/    <X>.kt          (plain immutable data classes / sealed types)
│   ├── repository/ <X>Repository.kt
│   └── usecase/  <Verb><X>UseCase.kt
├── presentation/<screen>/        (app mode only)
│   ├── <Screen>UiState.kt, <Screen>Action.kt, <Screen>Event.kt
│   ├── <Screen>ViewModel.kt
│   └── <Screen>Screen.kt         (<Screen>Route + <Screen>Screen + previews)
├── navigation/<Feature>Navigation.kt   (app mode: @Serializable destinations + NavGraphBuilder.<feature>Graph)
└── di/<Feature>Module.kt               (one Koin module for the feature)
```

`<pkg>` is the feature name lowercased without dashes (`user-profile` → `userprofile`). Class prefixes are PascalCase (`UserProfile`).

## 4. Naming

| Thing | Name | Notes |
|---|---|---|
| Network model | `UserDto` | `@Serializable`, `internal`, mirrors JSON exactly |
| Type-safe route | `UsersResource`, `UsersResource.Id` | Ktor `@Resource`, relative path (no leading `/`) |
| API | `internal class UserApi(client: HttpClient)` | Ktor calls only, returns `DataResult<Dto>`, one function per endpoint; tested with MockEngine |
| Remote data source | `UserRemoteDataSource` + `UserRemoteDataSourceImpl` | interface the repository depends on; maps DTO → domain, still `DataResult` |
| Entity / DAO | `UserEntity`, `UserDao` | `suspend` writes, `Flow` reads |
| Database | `<Feature>Database` + `<Feature>DatabaseConstructor` | one per feature that persists data |
| Local data source | `UserLocalDataSource` + `UserLocalDataSourceImpl` | maps Entity ↔ domain, exposes `Flow<Domain>` |
| Preferences | `<X>PreferencesDataSource` + `…Impl` | wraps the shared `DataStore<Preferences>`, keys prefixed with the feature name |
| Repository | `UserRepository` (domain) + `internal class UserRepositoryImpl` (data) | |
| Use case | `GetUserUseCase`, `ObserveUsersUseCase`, `UpdateUserNameUseCase`, `LoginUseCase` | verb first, one public `operator fun invoke` |
| ViewModel | `ProfileViewModel` | named after the screen, not the entity |
| UI state / actions / events | `ProfileUiState`, `ProfileAction`, `ProfileEvent` | |
| Composables | `ProfileRoute` (stateful), `ProfileScreen` (stateless) | |
| Destinations | `sealed interface ProfileDestination { @Serializable data object Profile }` | |
| Koin module | `val profileModule = module { … }` | |

Use cases are `Get…` (one-shot `suspend`), `Observe…` (returns `Flow`), or an imperative verb (`Login`, `SyncPayments`, `DeleteAccount`).

## 5. Results and errors

All fallible data operations return `DataResult<T>` (in `core/common/result`). They never throw.

```kotlin
sealed class DataResult<out T> {          // a class, not an interface: Swift keeps the generic type
    data class Success<out T>(val data: T) : DataResult<T>()
    data class Failure(val error: AppError) : DataResult<Nothing>()
}
// helpers: map, flatMap, onSuccess, onFailure, getOrNull, errorOrNull, asEmpty
```

`AppError` is a sealed hierarchy: `Network.{NoInternet, Timeout, Unauthorized, Forbidden, NotFound, Serialization, Client(code, body), Server(code, body)}`, `Local.{NotFound, Database(message)}`, `Domain(code, message)` and `Unknown(message)`.
- **Mapping in.** `safeCall { }` in `core/network` maps Ktor exceptions and HTTP status codes in one place. Local data sources catch SQLite exceptions and turn them into `AppError.Local.Database`.
- **Domain-specific errors.** Return `AppError.Domain(code = "coupon_expired")` with a stable code; the feature maps its codes to strings in the ViewModel and falls back to `toUiText()`. Don't throw.
- **UI.** `AppError.toUiText()` in `:core:ui` gives a default localized message. ViewModels put `UiText` into state or events; Composables resolve it with `asString()`.
- **Cancellation.** Never catch `CancellationException`. Rethrow it if you catch `Exception` broadly.

## 6. Visibility

Inside a feature, everything is `internal` except what other modules must reach:
- **App mode:** the Koin module `val <feature>Module` and the `NavGraphBuilder.<feature>Graph` function are the only public declarations.
- **SDK mode:** public declarations are domain models, use cases (`public class … internal constructor`) and the `<Feature>Feature` accessor. Repositories, data sources, DTOs, entities and DAOs stay `internal`.

## 7. Koin

- **One module per feature:** `di/<Feature>Module.kt`, registered once (app mode: `featureModules` in `:shared/.../di/KoinInit.kt`; SDK mode: `featureModules` in `di/SdkKoin.kt`). The skills insert lines at the `// tezca-kmp:<anchor>` marker comments. Keep those comments in place.
- **Scopes:**
  - `single` for infrastructure and anything holding state or connections: `HttpClient`, databases, DAOs, DataStore, APIs, data sources, repositories.
  - `factory` for use cases. They're stateless and cheap.
  - `viewModelOf(::XViewModel)` for ViewModels. Koin supplies `SavedStateHandle` automatically.
- **Prefer constructor DSL:** `singleOf(::UserApiImpl) { bind<UserApi>() }`, `factoryOf(::GetUserUseCase)`. Use the lambda form when a constructor needs a qualifier or a non-injected argument.
- **Dispatchers:** inject `DispatcherProvider` (`main`, `io`, `default`), never `Dispatchers.*`. Tests swap in `TestDispatcherProvider`.
- **Overrides:** a feature can override a core default, such as replacing `NoTokenProvider` with a DataStore-backed `TokenProvider`. Declare the override in the feature module; feature modules load after core.
- **Verification:** `KoinGraphTest` (androidHostTest in `:shared` or the SDK module) runs `verify()` over all modules. Keep it passing. Add new platform-only types to its `extraTypes`.

## 8. Offline-first data (default)

The repository makes the local database the single source of truth.

1. **Reads** return `Flow<…>` straight from Room through the local data source, so the UI updates whenever the data changes.
2. **`refresh(force)`** fetches from the network and upserts into Room inside a transaction. Room then re-emits to every observer. Use `SyncMetadataStore.isStale(key, ttl)` / `markSynced(key)` from `:core:database` to skip refreshes while the cache is fresh.
3. **Writes** go to the server first. On success the server's version is written locally. Queue or optimistically apply writes only when the user asks for offline writes.
4. **Errors from refresh** are returned as `DataResult`, never pushed into the data stream. The UI keeps showing cached data and surfaces the error separately.

When the user says "no offline", "don't add offline support", "online only" or similar, the repository talks only to the remote data source. When they say "local only" or "no API", it talks only to the local data source. `make-repository` has the templates.

## 9. Presentation (app mode)

- **`XUiState`:** an immutable `data class` (or a sealed interface when screens are mutually exclusive), with defaults so `XUiState()` is a valid initial state. Use `kotlinx.collections.immutable` only if the project already uses it; otherwise `List` is fine.
- **State exposure:** the ViewModel exposes `val state: StateFlow<XUiState>` built with `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), XUiState())`. Data is loaded in `onStart {}` or through `combine`, not in `init`, so nothing runs before the UI subscribes and tests control timing.
- **Input:** user intent arrives through a single `fun onAction(action: XAction)`, where `XAction` is a sealed interface.
- **One-off effects:** navigation, snackbars and similar go through `Channel<XEvent>(Channel.BUFFERED)` exposed as `events: Flow<XEvent> = channel.receiveAsFlow()`. The UI collects them with `ObserveAsEvents` from `:core:ui`. Don't model one-off effects as boolean flags in state.
- **Route / Screen split:**
  - `XRoute` gets the ViewModel (`koinViewModel()`), collects state with `collectAsStateWithLifecycle()`, observes events and receives navigation callbacks.
  - `XScreen(state, onAction, modifier)` is stateless and previewable (`@Preview` with `AppTheme`).
- **Strings:** all user-facing text lives in the module's `composeResources/values/strings.xml` and is read with `stringResource(Res.string.x)`. Each module has its own `Res` (`<namespace>.generated.resources.Res`). Never hardcode user-facing text.
- **Navigation:** type-safe `@Serializable` destinations per feature. `NavGraphBuilder.<feature>Graph(navController, onX: () -> Unit, …)` lives in the feature, and `:shared`'s `AppNavHost` decides where the callbacks lead. Route arguments are read in the graph (`entry.toRoute<XDestination.Detail>()`) and handed to the ViewModel as constructor parameters through Koin (`koinViewModel { parametersOf(route.id) }` + `viewModel { params -> DetailViewModel(userId = params.get(), …) }`). That keeps ViewModels free of `SavedStateHandle` and trivial to construct in tests.

## 10. SDK mode: Swift-friendly public API

SKIE (`co.touchlab.skie`) is applied by `kmp.sdk`:
- `suspend` functions become Swift `async throws`.
- `Flow<T>` becomes an `AsyncSequence`.
- Sealed classes and interfaces can be switched exhaustively with `onEnum(of:)`.
- Kotlin enums become real Swift enums.

Design the API so Swift code stays clean.

- **Public surface:** expose use cases through feature accessors (`FitCore.profile.getProfile`, Swift: `import FitCoreSdk` then `FitCore.shared.profile.getProfile`) whose constructor is `internal`. Return domain models and `DataResult`, never DTOs or entities.
- **Parameters:**
  - No default arguments on public SDK functions (Swift doesn't see them, so callers must pass everything anyway). Make the parameter explicit.
  - Prefer non-null primitives. `Int?` becomes `KotlinInt?` in Swift.
  - Take `NSData`-friendly inputs on iOS through an `iosMain` overload when you need bytes.
- **Dates:** return `kotlin.time.Instant` for timestamps and `kotlinx.datetime.LocalDate` for calendar dates. For Swift convenience, add an `iosMain` extension such as `public val Workout.performedAtDate: NSDate get() = performedAt.toNSDate()` (`kotlinx.datetime.toNSDate`).
- **Name clashes with Foundation or Swift** (`SortOrder`, `Data`, `Result`, `Error`): rename the type, or annotate it with `@ObjCName(swiftName = "FitSortOrder")`.
- **Never expose `Throwable`.** Errors travel as `AppError` inside `DataResult.Failure`.
- **Visibility under `explicitApi()`:** declarations need explicit visibility, so infrastructure stays `internal`. Use `internal` constructors for classes Swift should never build.

## 11. Testing conventions

These are summarized here; details are in `add-tests`.
- Tests live in `commonTest` so they run on the JVM (`testAndroidHostTest`) and the iOS simulator (`iosSimulatorArm64Test`).
- Test names follow `methodOrBehavior_condition_expectedResult`.
- Use fakes for repositories and data sources (`FakeUserRepository`), not mocking frameworks. Use `MockEngine` (`mockHttpClient` from `:core:testing`) for APIs and in-memory Room for DAOs.
- Use Turbine for flows, and `runTest` with `TestDispatcherProvider` plus `setMainDispatcher` for ViewModels.

## 12. Before you finish any change

- The module compiles for Android and iOS. Run `./gradlew :<module>:compileAndroidMain :<module>:compileKotlinIosSimulatorArm64`, or the module's tests.
- New Koin definitions are registered, and `KoinGraphTest` still passes.
- No DTO or entity leaks outside `data/`, and no ViewModel injects a repository.
- User-facing strings are in `composeResources`.
- In SDK mode, nothing became `public` by accident. Check the explicit-API compiler warnings and errors.
