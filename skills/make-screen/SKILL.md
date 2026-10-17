---
name: make-screen
description: Build or update a complete Compose Multiplatform screen end to end in a KMP app. It inspects what already exists and creates whatever is missing across the whole stack, in order (Ktor API / remote data source, Room DAO / local data source, preferences, offline-first repository, use cases, ViewModel), then writes the stateful Route and stateless Screen composables connected to the ViewModel, string resources, previews, the navigation destination, and the Koin wiring. Use it whenever the user asks for a screen, page, view or flow ("make the login screen", "create the workout list with data from /workouts", "add a profile detail page", "build the payment summary UI"), or for a feature's UI plus its data, even if they only describe what the screen shows.
---

# make-screen: from endpoint to pixels

This skill orchestrates the layer skills and adds the UI on top. It works in **app mode** only. In SDK mode there's no UI: offer `make-usecase` for the operations instead.

## 1. Understand the screen
Read `../kmp-architecture/SKILL.md` and `.tezca-kmp.json`. Then pin down, asking only for what you can't infer:
- **Feature:** which `:feature:<name>` it belongs to. If it doesn't exist, run `new-feature` first.
- **Content:** fields, lists, images, empty/loading/error states. A design (Figma link, screenshot) is the best input; if one is given, follow it closely.
- **Actions:** buttons, inputs, pull-to-refresh, item clicks, and where each one leads. Navigation outside the feature becomes a graph callback.
- **Data:** endpoint(s) with request/response, whether it must work offline (default yes; "don't do this offline" → remote-only), and any local-only state.
- **Route arguments:** e.g. `userId`.

## 2. Inventory, then fill the gaps in order
List what exists in the feature (`data/remote`, `data/local`, `domain/repository`, `domain/usecase`, `presentation/`). For each missing piece, follow that skill's SKILL.md and templates, in this order, because each layer consumes the previous one:

| Missing | Follow | Notes |
|---|---|---|
| endpoint calls | `../make-api/SKILL.md` | DTOs, routes, `XApi`, `XRemoteDataSource` |
| cached tables | `../make-database/SKILL.md` | only for offline-first or local data |
| small settings / session | `../make-preferences/SKILL.md` | |
| repository | `../make-repository/SKILL.md` | pass the offline decision through |
| use cases | `../make-usecase/SKILL.md` | one per action / observation |
| ViewModel | `../make-viewmodel/SKILL.md` | UiState / Action / Event |

Reuse and extend what exists. If the repository already has `observeUsers`, the new screen doesn't need a new one.

## 3. Write the UI
Create `presentation/<screen>/<Screen>Screen.kt` from `references/templates.md`. It contains three parts.

1. **`<Screen>Route`**: the stateful entry, `internal`.
   - Gets the ViewModel with `koinViewModel()` (or `koinViewModel { parametersOf(arg) }`).
   - Collects `state` with `collectAsStateWithLifecycle()`.
   - Handles `events` with `ObserveAsEvents` (navigation callbacks, snackbars via a `SnackbarHostState`).
   - Calls `<Screen>Screen`.
2. **`<Screen>Screen(state, onAction, modifier, …)`**: stateless and `internal`.
   - Uses Material 3 components and `Dimens` / `AppTheme` from `:core:ui`, plus `LoadingContent` / `ErrorContent` where they fit. `:core:ui`'s `Res` is public, so shared strings (`action_retry`, error messages) are reused instead of duplicated.
  - Inside `PullToRefreshBox` every branch (empty, error, content) must be scrollable, or pull-to-refresh can't fire on an empty or offline list.
  - Paginated lists: see make-repository, section E, for load-more.
   - Handles insets with `Scaffold` (`contentWindowInsets`) or `safeContentPadding()`. The Android app is edge-to-edge and iOS has notches.
   - Sends every interaction as an `Action`. No ViewModel or Koin inside, so it previews and tests in isolation.
   - Lists use `LazyColumn` with stable `key`s.
3. **Previews**: `@Preview` private functions wrapping the screen in `AppTheme` with realistic sample states (content, empty, loading, error). Use `androidx.compose.ui.tooling.preview.Preview`.

Then:
- **Strings:** put every user-visible text in `feature/<name>/src/commonMain/composeResources/values/strings.xml`, with keys prefixed by the screen (`login_title`, `login_email_label`). Use `stringResource(Res.string.…)` with the feature's `Res` (`<base>.feature.<pkg>.generated.resources.Res`). Add translations under `values-<lang>/` if the project already has other locales.
- **Images/icons:** use `Res.drawable.*` for custom assets. For icons, prefer `material-icons-core` only if it's already in the catalog; otherwise use a vector XML drawable in `composeResources/drawable`.
- **Destination:** add it to `navigation/<Feature>Navigation.kt`:
  - `@Serializable data object Login : <Feature>Destination`, or `@Serializable data class UserDetail(val userId: Long) : <Feature>Destination`.
  - `composable<…> { entry -> … Route(…) }` under `tezca-kmp:graph-composables`. Read arguments with `entry.toRoute<…>()`.
  - Wire in-feature navigation with `navController.navigate(...)`.
  - Navigation that leaves the feature becomes a new callback parameter of `<feature>Graph(...)` (under `tezca-kmp:graph-callbacks`), implemented in `:shared`'s `AppNavHost`.
  - If this is the app's first real screen, ask whether it becomes `startDestination` in `AppNavHost`; with no answer, make it the start destination.
- **Koin:** confirm the ViewModel (and everything below it) is registered in `di/<Feature>Module.kt`.

## 4. Verify
The layer skills each list their own compile check; when they run as part of this skill, skip those and compile once here, after all layers exist.
```bash
./gradlew :feature:<name>:compileAndroidMain :feature:<name>:compileKotlinIosSimulatorArm64
./gradlew :shared:testAndroidHostTest          # Koin graph still resolves
./gradlew :androidApp:assembleDevDebug         # the screen is reachable from the app
```
Offer `add-tests` for the ViewModel (state and events) and the repository. Offer running the app (`./gradlew :androidApp:installDevDebug`, or Xcode for iOS) so the user can see the screen.

## 5. Report
Give the layers created or updated (file list grouped by layer), the navigation entry, and anything stubbed or assumed (e.g. a DTO shape inferred from a description). Keep it short.
