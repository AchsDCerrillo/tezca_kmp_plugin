---
name: make-usecase
description: Create or update a domain-layer use case in a Kotlin Multiplatform project. Each use case has exactly one responsibility and a single operator fun invoke. It combines one or more repositories, holds the business rules (validation, combining, ordering, permission checks), returns Flow or DataResult, and is registered in Koin as a factory. In SDK mode it's also exposed on the SDK's public feature accessor, with SKIE making it async/AsyncSequence in Swift. Use it whenever the user wants a use case or interactor, wants to put business logic in the domain layer, combine data from several repositories, or expose an operation to the ViewModel or to SDK consumers, even if they only say "add the logic for X".
---

# make-usecase: one business action

A use case represents **one** thing the product does: observe the user's workouts, log in, apply a coupon, sync payments. It sits between ViewModels (or SDK consumers) and repositories. It's where rules live that don't belong to one data source or one screen.

## Before you start
1. Read `../kmp-architecture/SKILL.md` and `.tezca-kmp.json`, then locate the feature root.
2. Name it **verb + subject**, matching the kmp-architecture naming table:
   - `Observe…UseCase` returns a `Flow`.
   - `Get…UseCase` is a one-shot `suspend`.
   - Commands use an imperative verb (`LoginUseCase`, `ApplyCouponUseCase`, `RefreshUsersUseCase`).
3. **One responsibility.** If the request describes two actions ("load the profile and update the avatar"), create two use cases and say why: each can be reused, tested and faked on its own. Combining several *repositories* to answer one question is fine (`ObserveDashboardUseCase` = workouts + payments).
4. Find the repositories it needs (`domain/repository`). If one is missing, run `make-repository` first.
5. Look in `domain/usecase/` for an existing use case with the same responsibility. Update it rather than creating a near-duplicate. If behavior must change for one caller only, add a parameter or a new use case. Never create a `…V2` copy.

## Steps
1. Create `domain/usecase/<Name>UseCase.kt` from `references/templates.md`:
   - **App mode:** `internal class`.
   - **SDK mode:** `public class <Name>UseCase internal constructor(…)`. Domain models in its signature must be `public`.
   - Constructor parameters are repositories, other use cases (to compose rules), `DispatcherProvider` (only if the use case does CPU-heavy work, applied with `flowOn(dispatchers.default)` / `withContext`) and `kotlin.time.Clock` when time matters (inject it for testability).
   - Exactly one public member: `operator fun invoke(...)`, or `suspend operator fun invoke(...)`.
   - Return types: `Flow<T>` for observations, `DataResult<T>` for one-shot operations and commands. Don't return `Flow<DataResult<T>>` unless every emission can fail independently (polling).
   - Business rules (validation, permission checks, choosing which repository to ask) live here and fail with `AppError.Domain("<stable_code>")`.
2. Register it in Koin under `// tezca-kmp:domain`: `factoryOf(::<Name>UseCase)`.
3. **SDK mode only.** Expose it in `<Feature>Feature.kt` below `// tezca-kmp:sdk-usecases`: `public val <name>: <Name>UseCase get() = koin.get()`. The property name drops the `UseCase` suffix and is lowerCamel (`getProfile`, `observeWorkouts`). Swift calls it as `try await FitCore.shared.profile.getProfile.invoke(id: 1)` or `for await workouts in FitCore.shared.workouts.observeWorkouts.invoke()` (`import FitCoreSdk`). In SDK mode give `invoke` no default arguments; Swift sees every parameter.
4. Verify with `./gradlew :<module>:compileAndroidMain :<module>:compileKotlinIosSimulatorArm64`. In SDK mode, also check that the explicit-API check doesn't complain. Offer a use-case test through `add-tests`, using fake repositories.

## Output
Say what the use case does in one sentence, what it depends on, and where it's registered. Then suggest `make-viewmodel` (app) or show the Kotlin and Swift call (SDK).
