---
name: make-preferences
description: Create or update DataStore (Preferences) storage in a Kotlin Multiplatform project. Generates typed keys, a PreferencesDataSource interface + Impl exposing Flow values and suspend setters on the shared DataStore<Preferences> from :core:datastore, and the Koin binding. It covers the signed-in session too: a DataStore-backed TokenProvider so Ktor sends (and optionally refreshes) the token, logout that clears Ktor's token cache, and choosing the start screen from the stored session. Use it whenever the user wants to save settings, flags, onboarding-completed state, the selected theme or language, a session or auth token, "keep the user logged in", the last sync time, or any small key-value data on the device in a KMP/Compose Multiplatform app or SDK, even if they say "SharedPreferences", "UserDefaults" or "settings".
---

# make-preferences: typed DataStore preferences

`:core:datastore` (or the SDK's `core.datastore` package) owns the **single** `DataStore<Preferences>` instance. DataStore must never be created twice for the same file. Features add typed data sources on top of it.

Use DataStore for small key-value state. Use Room (`make-database`) for anything you query, list or relate. Tokens are stored in plain DataStore by default; offer platform secure storage (Android Keystore-encrypted values, iOS Keychain through an `expect` wrapper) when the user cares about it.

## Steps
1. Read `../kmp-architecture/SKILL.md` and `.tezca-kmp.json`, then locate the owner:
   - A feature's own settings live in that feature (`data/local/`).
   - **The session** (tokens plus who is signed in) is read by many features. In app mode, put it in `:core:session`; create it with the `kmp.library` convention, depending on `:core:datastore` and `:core:network`, if it doesn't exist (kmp-architecture section 2). Use the feature that does the login only when no other feature will read the session. In SDK mode, use a `core.session` package.
2. List the values: name, type (`Boolean`, `Int`, `Long`, `Float`, `Double`, `String`, `Set<String>`) and default.
   - Group values that change together into one small domain model.
   - Enums are stored as `String`.
   - Structured values go in a JSON `String` through `kotlinx.serialization`, only when they are small.
3. Write `<X>PreferencesDataSource.kt` (an interface plus `internal …Impl`) from `references/templates.md`:
   - **Keys** go in a private object, **prefixed with the owner's name** (`"session.access_token"`, `"onboarding.completed"`). Every module shares one file, so prefixes prevent collisions.
   - **Reads** are `Flow`s from `dataStore.data`, with `.catch { if (it is IOException) emit(emptyPreferences()) else throw it }`, then `.map { … }.distinctUntilChanged()`.
   - **Writes** are `suspend` and use `dataStore.edit { }`. Clear values with `remove(key)`.
   - If the data source already exists, add keys in place. Never rename an existing key string: that silently drops users' stored values. If a rename is required, migrate by reading the old key, writing the new one and removing the old one.
4. Koin: `singleOf(::<X>PreferencesDataSourceImpl) { bind<<X>PreferencesDataSource>() }`. `DataStore<Preferences>` already comes from `dataStoreModule`.
5. **Session extras**, all in the templates:
   - **`SessionTokenProvider`** overrides core's `NoTokenProvider`: `single<TokenProvider> { SessionTokenProvider(get()) }`. Pick the variant:
     - **No refresh endpoint** (the default when the backend docs don't mention one). On 401 the session is cleared and the user signs in again.
     - **Refresh endpoint.** It calls `AuthRemoteDataSource.refresh(refreshToken)` on the *public* client from make-api; on failure it clears the session.
   - **Logout:** clear the session, **and** call `httpClient.clearAuthTokens()` (core.network). Ktor's bearer provider caches tokens, so without that the next request still sends the old one.
   - **Start destination:** if the app should open on home when a session exists, add the small `AppStartViewModel` in `:shared` from the template. `AppNavHost` waits for it before choosing `startDestination`.
6. Verify with `./gradlew :<module>:compileAndroidMain :<module>:compileKotlinIosSimulatorArm64`, plus `:shared:testAndroidHostTest` when Koin bindings changed. Offer tests through `add-tests`: a real DataStore on a temp file.
