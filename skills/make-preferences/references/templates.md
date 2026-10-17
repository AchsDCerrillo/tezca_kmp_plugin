# make-preferences templates

The examples use the package `com.acme.gym.core.session` (app mode: module `:core:session`). A feature's own preferences look the same in `feature/<name>/…/data/local/`.

## Contents
1. Feature setting (flag)
2. Session model + data source
3. SessionTokenProvider (no-refresh and refresh variants)
4. Logout
5. Start destination from the session
6. Koin

## 1. Feature setting
```kotlin
internal interface OnboardingPreferencesDataSource {
    val hasCompletedOnboarding: Flow<Boolean>
    suspend fun setCompletedOnboarding(completed: Boolean)
}

internal class OnboardingPreferencesDataSourceImpl(
    private val dataStore: DataStore<Preferences>,
) : OnboardingPreferencesDataSource {
    private val completedKey = booleanPreferencesKey("onboarding.completed")

    override val hasCompletedOnboarding: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[completedKey] ?: false }
        .distinctUntilChanged()

    override suspend fun setCompletedOnboarding(completed: Boolean) {
        dataStore.edit { it[completedKey] = completed }
    }
}
```

## 2. Session model + data source
`Session.kt`
```kotlin
package com.acme.gym.core.session

/** Who is signed in. Public in :core:session because several features read it. */
data class Session(
    val accessToken: String,
    val refreshToken: String?,
    val userId: Long,
    val displayName: String,
)
```
`SessionPreferencesDataSource.kt`
```kotlin
package com.acme.gym.core.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.io.IOException

interface SessionPreferencesDataSource {
    val session: Flow<Session?>
    suspend fun currentSession(): Session?
    suspend fun save(session: Session)
    suspend fun clear()
}

internal class SessionPreferencesDataSourceImpl(
    private val dataStore: DataStore<Preferences>,
) : SessionPreferencesDataSource {

    private object Keys {
        val ACCESS_TOKEN = stringPreferencesKey("session.access_token")
        val REFRESH_TOKEN = stringPreferencesKey("session.refresh_token")
        val USER_ID = longPreferencesKey("session.user_id")
        val DISPLAY_NAME = stringPreferencesKey("session.display_name")
    }

    override val session: Flow<Session?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            val access = prefs[Keys.ACCESS_TOKEN] ?: return@map null
            val userId = prefs[Keys.USER_ID] ?: return@map null
            Session(access, prefs[Keys.REFRESH_TOKEN], userId, prefs[Keys.DISPLAY_NAME].orEmpty())
        }
        .distinctUntilChanged()

    override suspend fun currentSession(): Session? = session.first()

    override suspend fun save(session: Session) {
        dataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = session.accessToken
            session.refreshToken?.let { prefs[Keys.REFRESH_TOKEN] = it } ?: prefs.remove(Keys.REFRESH_TOKEN)
            prefs[Keys.USER_ID] = session.userId
            prefs[Keys.DISPLAY_NAME] = session.displayName
        }
    }

    override suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.ACCESS_TOKEN)
            prefs.remove(Keys.REFRESH_TOKEN)
            prefs.remove(Keys.USER_ID)
            prefs.remove(Keys.DISPLAY_NAME)
        }
    }
}
```
Expose `ObserveSessionUseCase` and `LogoutUseCase` from the same module so features never touch the data source directly.

## 3. SessionTokenProvider
**No refresh endpoint** (the default):
```kotlin
internal class SessionTokenProvider(
    private val sessions: SessionPreferencesDataSource,
) : TokenProvider {
    override suspend fun currentTokens(): AuthTokens? =
        sessions.currentSession()?.let { AuthTokens(it.accessToken, it.refreshToken) }

    /** No refresh endpoint: a 401 ends the session; observers of `session` send the user to login. */
    override suspend fun refreshTokens(): AuthTokens? {
        sessions.clear()
        return null
    }
}
```
**Refresh endpoint.** `AuthRemoteDataSource.refresh(refreshToken): DataResult<Session>` is built with make-api on the **public** client, so a 401 from refresh doesn't recurse:
```kotlin
internal class RefreshingSessionTokenProvider(
    private val sessions: SessionPreferencesDataSource,
    private val authRemote: AuthRemoteDataSource,
) : TokenProvider {
    override suspend fun currentTokens(): AuthTokens? =
        sessions.currentSession()?.let { AuthTokens(it.accessToken, it.refreshToken) }

    override suspend fun refreshTokens(): AuthTokens? {
        val refreshToken = sessions.currentSession()?.refreshToken ?: return null
        return when (val result = authRemote.refresh(refreshToken)) {
            is DataResult.Success -> result.data.also { sessions.save(it) }.let { AuthTokens(it.accessToken, it.refreshToken) }
            is DataResult.Failure -> { sessions.clear(); null }
        }
    }
}
```

## 4. Logout
```kotlin
class LogoutUseCase internal constructor(
    private val sessions: SessionPreferencesDataSource,
    private val httpClient: HttpClient,
) {
    suspend operator fun invoke() {
        sessions.clear()
        httpClient.clearAuthTokens()   // core.network: drop Ktor's cached bearer tokens
    }
}
```
Also clear per-user caches (feature databases, `SyncMetadataStore.clear…`) if another user can sign in on the same device.

## 5. Start destination from the session (app mode, `:shared`)
```kotlin
// shared/.../navigation/AppStartViewModel.kt
internal class AppStartViewModel(observeSession: ObserveSessionUseCase) : ViewModel() {
    /** null while the session is being read (keep the splash / an empty surface on screen). */
    val signedIn: StateFlow<Boolean?> = observeSession()
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

// AppNavHost.kt
@Composable
fun AppNavHost(navController: NavHostController, startViewModel: AppStartViewModel = koinViewModel()) {
    val signedIn by startViewModel.signedIn.collectAsStateWithLifecycle()
    val start: Any = when (signedIn) {
        null -> return            // still loading: render nothing (or a splash) for a frame
        true -> HomeDestination.Home
        false -> OnboardingDestination.Login
    }
    NavHost(navController = navController, startDestination = start) { … }
}
```
Register `viewModelOf(::AppStartViewModel)` in a small `appModule` inside `:shared/di/KoinInit.kt` (added to `appModules`).

To react to logout or an expired session while the app is running, collect `signedIn` in a `LaunchedEffect` and `navigate(OnboardingDestination.Login) { popUpTo(0) }` when it turns `false`.

## 6. Koin (`:core:session` module)
```kotlin
val sessionModule = module {
    singleOf(::SessionPreferencesDataSourceImpl) { bind<SessionPreferencesDataSource>() }
    single<TokenProvider> { SessionTokenProvider(get()) }      // overrides core.network's NoTokenProvider
    factoryOf(::ObserveSessionUseCase)
    factoryOf(::LogoutUseCase)
}
```
Add `sessionModule` to `appModules()` **after** `networkModule`, because the later definition wins the `TokenProvider` override. Also add `implementation(projects.core.session)` to `:shared` and to every feature that reads the session.
