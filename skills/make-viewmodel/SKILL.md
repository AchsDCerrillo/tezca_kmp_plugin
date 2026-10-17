---
name: make-viewmodel
description: Create or update a multiplatform ViewModel (org.jetbrains.androidx.lifecycle) for a Compose Multiplatform screen, following Android's architecture guidance. It gets all its data from use cases, holds the screen's presentation and business logic, exposes an immutable UiState as a StateFlow (stateIn WhileSubscribed), receives user intent through onAction(Action), and emits one-off Events through a Channel. Registered with Koin viewModelOf; route arguments come in as constructor params. Use it whenever the user wants a ViewModel, screen state, UI logic, form validation, loading/error/empty states, pull-to-refresh, or "the logic behind the screen" in a KMP/Compose Multiplatform app, even if they don't say "ViewModel".
---

# make-viewmodel: state holder for one screen

One ViewModel per screen, named after the screen (`ProfileViewModel`, not `UserViewModel`). It follows [Android's UI layer guidance](https://developer.android.com/topic/architecture/ui-layer):
- **State flows down** as a single immutable `UiState`.
- **Events flow up** through `onAction`.
- **One-off effects** (navigate, show a snackbar) go through an event channel.

## Before you start
1. Read `../kmp-architecture/SKILL.md` (section 9) and `.tezca-kmp.json`. **App mode only.**
   - In SDK mode, explain that the SDK exposes use cases and the host apps own their ViewModels.
   - If the user really wants a shared presentation layer in the SDK, put a plain class in `presentation/` that exposes `StateFlow` (SKIE turns it into an `AsyncSequence`), without `androidx.lifecycle.ViewModel`.
2. Work out:
   - what the screen shows (fields, lists, loading, empty, error)
   - what the user can do (actions)
   - what happens once (navigation, messages)
   - which use cases provide the data, and the route arguments
3. Make sure the use cases exist (`domain/usecase/`). If any are missing, run `make-usecase` first. **Never inject a repository or data source into a ViewModel.**
4. If the ViewModel exists, update it in place: add state fields, actions and events. Keep the public `state` / `events` / `onAction` shape.

## Steps
Create `presentation/<screen>/` with three files. `references/templates.md` has a list screen (observe + refresh), a form (validate + submit) and a detail screen (route argument).

1. **`<Screen>UiState.kt`**: an `@Immutable data class` with defaults, so `UiState()` is the initial state.
   - Hold only what the UI renders: formatted where needed, `UiText` for messages, and flags like `isLoading` / `isRefreshing`.
   - Derived values are computed `val` properties (`canSubmit`), not stored fields that can drift.
2. **`<Screen>Action.kt`**: a `sealed interface` with one entry per user intent (`EmailChanged(value)`, `Submit`, `Refresh`, `UserClicked(id)`).
   **`<Screen>Event.kt`**: a `sealed interface` for one-off effects (`NavigateHome`, `ShowMessage(UiText)`).
   Omit `Action` or `Event` when the screen has none (a read-only greeting doesn't need empty sealed types). Add them when the first interaction or effect appears.
3. **`<Screen>ViewModel.kt`**: `internal class <Screen>ViewModel(<use cases>, <route params>) : ViewModel()`.
   - **Observed data:** build `state` declaratively from use case flows with `combine` / `map`, and finish with `.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), <Screen>UiState())`. Trigger the initial refresh in `.onStart { }` rather than `init`, so nothing runs before the UI subscribes.
   - **Local input state** (form fields): use a private `MutableStateFlow`. Expose it with `asStateFlow()`, or `combine` it with the observed data.
   - **Events:** `private val _events = Channel<<Screen>Event>(Channel.BUFFERED)` and `val events = _events.receiveAsFlow()`.
   - `fun onAction(action: <Screen>Action)` uses `when` over every action. Launch work in `viewModelScope`.
   - **Errors:** map `AppError` with a feature-specific `when` for `AppError.Domain` codes, falling back to `toUiText()`. Put it in state for persistent errors (an empty-state message) or in an event for transient ones (a snackbar).
   - Don't touch `Dispatchers` directly. Use cases and repositories already move work off the main thread. The ViewModel only orchestrates.
   - Don't use `GlobalScope`, `runBlocking` or blocking calls.
4. **Koin**, under `// tezca-kmp:presentation` in `di/<Feature>Module.kt`:
   - No route params: `viewModelOf(::<Screen>ViewModel)` (`org.koin.core.module.dsl.viewModelOf`).
   - Route params: `viewModel { params -> <Screen>ViewModel(userId = params.get(), getUser = get()) }` (`org.koin.core.module.dsl.viewModel`). The Route passes `parametersOf(id)`.
5. **Verify** with `./gradlew :feature:<name>:compileAndroidMain :feature:<name>:compileKotlinIosSimulatorArm64` and `:shared:testAndroidHostTest` (Koin graph). Offer a ViewModel test through `add-tests`: fake use cases or repositories, Turbine on `state` and `events`.

## Next
`make-screen` builds the Route and Screen composables on top of this ViewModel. If the user asked for the whole screen, continue there.
