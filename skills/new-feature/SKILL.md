---
name: new-feature
description: Create a new feature in a Kotlin Multiplatform project set up with start-kmp. App mode creates a Gradle module :feature:<name> (data/domain/presentation packages, Koin module, type-safe navigation graph, compose resources) and registers it in settings.gradle.kts, :shared, Koin and the root NavHost. SDK mode creates a feature package with its Koin module and a public accessor on the SDK facade. Use it whenever the user wants to add a feature, module or area of the app ("create the onboarding module", "add a payments feature", "new module for user profile", "start the login flow"), or when another skill needs a feature that doesn't exist yet, even if they don't say "module".
---

# new-feature: add a feature module or package

A feature is a vertical slice that owns one area of the product: onboarding (registration → login → first home), payments, profile, and so on. It contains its own data, domain and presentation code and depends only on `:core:*`. See `../kmp-architecture/SKILL.md` (sections 1–7) for the rules.

## Steps
1. **Read `.tezca-kmp.json`.** If it's missing, the project isn't set up yet: offer `start-kmp`.
2. **Agree on the name.** Use lowercase words separated by dashes (`onboarding`, `user-profile`, `payments`). It becomes:
   - module `:feature:user-profile`
   - package `…feature.userprofile` (SDK mode: `….userprofile`)
   - class prefix `UserProfile`

   Suggest splitting or merging when the boundary looks off. Login and registration usually belong together in `onboarding` or `auth`. Home is normally its own feature (`home`), reached through an `onFinished`-style callback. A tiny settings toggle usually belongs to an existing feature, not a new module. State that several features need (the signed-in session, the current user) doesn't belong to any one feature: put it in a small `:core:session` module (see kmp-architecture, section 2).
3. **Run the script** from the project root:
   ```bash
   python3 -I <this-skill>/scripts/new_feature.py --project . --name <name> [--dry-run]
   ```
   - **App mode:** it creates `feature/<name>/` (build file with `kmp.feature`, `di/<Feature>Module.kt`, `navigation/<Feature>Navigation.kt`, `composeResources/values/strings.xml`). It inserts registrations below the `// tezca-kmp:` markers in `settings.gradle.kts`, `shared/build.gradle.kts`, `shared/.../di/KoinInit.kt` and `shared/.../navigation/AppNavHost.kt`, and adds the feature to `.tezca-kmp.json`.
   - **SDK mode:** it creates `<sdkModule>/.../<pkg>/di/<Feature>Module.kt` and `<Feature>Feature.kt` (the public accessor class). It registers the module in `di/SdkKoin.kt` and adds `val <feature>` to the SDK facade object.

   The JSON report lists `created`, `edited` and `manual` items. Do every `manual` item by hand; usually a marker was deleted or a file was renamed.
4. **First screen as start destination (app mode).** If this is the first feature and the app still starts at `WelcomeDestination`, ask whether this feature should become the start destination. If nobody can answer, make the first real screen the start destination; it beats a placeholder. Change `startDestination` in `AppNavHost` once the feature has a destination (`make-screen` creates it), and remove the placeholder `WelcomeDestination` once it's unused. When the start depends on stored state (signed in → home, else → login), see make-preferences, "Start destination from the session".
5. **Cross-feature navigation.** When the feature must hand off to another one (onboarding → home), add a callback parameter to `<feature>Graph(...)`, e.g. `onFinished: () -> Unit`, below the `tezca-kmp:graph-callbacks` marker. Implement it in `AppNavHost`:
   ```kotlin
   onboardingGraph(navController = navController, onFinished = {
       navController.navigate(HomeDestination.Home) { popUpTo<OnboardingDestination.Welcome> { inclusive = true } }
   })
   ```
   Never make one feature module depend on another.
6. **Verify:**
   - App mode: `./gradlew :feature:<name>:compileAndroidMain :shared:testAndroidHostTest`. `KoinGraphTest` confirms the module is wired.
   - SDK mode: `./gradlew :<sdkModule>:testAndroidHostTest`.

## What to do next
Tell the user what was created, then suggest building the slice:
- `make-api` for the endpoints
- `make-database` and/or `make-preferences` for local data
- `make-repository`
- `make-usecase` for each business action
- `make-viewmodel` + `make-screen` per screen

`make-screen` can do the whole chain for one screen in a single pass.

## Manual fallback (no Python)
Reproduce the script's output by hand from `assets/templates/<mode>/`. Substitute the placeholders:
- `__FEATURE__` = `user-profile`
- `__FPKG__` = `userprofile`
- `__Feature__` = `UserProfile`
- `__feature__` = `userProfile`
- `__feature_snake__` = `user_profile`
- `__PKG__` = the base package

Add the registrations listed in step 3.
