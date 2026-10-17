# make-viewmodel templates

The package for all of these is `com.acme.gym.feature.profile.presentation.<screen>`.

## Contents
1. List screen (observe + refresh)
2. Form screen (validate + submit)
3. Detail screen (route argument)
4. Error mapping helper
5. Koin

## 1. List screen: observe + refresh
`UserListUiState.kt`
```kotlin
import androidx.compose.runtime.Immutable
import com.acme.gym.core.ui.UiText
import com.acme.gym.feature.profile.domain.model.User

@Immutable
internal data class UserListUiState(
    val users: List<User> = emptyList(),
    val isLoading: Boolean = true,          // first load, nothing cached yet
    val isRefreshing: Boolean = false,      // pull-to-refresh indicator
    val errorMessage: UiText? = null,       // shown only when there is nothing cached to display
) {
    val isEmpty: Boolean get() = !isLoading && users.isEmpty() && errorMessage == null
}
```
`UserListAction.kt` and `UserListEvent.kt`
```kotlin
internal sealed interface UserListAction {
    data object Refresh : UserListAction
    data class UserClicked(val id: Long) : UserListAction
}

internal sealed interface UserListEvent {
    data class OpenUser(val id: Long) : UserListEvent
    data class ShowMessage(val message: UiText) : UserListEvent
}
```
`UserListViewModel.kt`
```kotlin
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.acme.gym.core.common.result.DataResult
import com.acme.gym.core.ui.toUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class UserListViewModel(
    observeUsers: ObserveUsersUseCase,
    private val refreshUsers: RefreshUsersUseCase,
) : ViewModel() {

    private val refresh = MutableStateFlow(RefreshState())

    private val _events = Channel<UserListEvent>(Channel.BUFFERED)
    val events: Flow<UserListEvent> = _events.receiveAsFlow()

    val state: StateFlow<UserListUiState> = combine(observeUsers(), refresh) { users, refreshState ->
        UserListUiState(
            users = users,
            isLoading = users.isEmpty() && refreshState.inProgress && !refreshState.userInitiated,
            isRefreshing = refreshState.inProgress && refreshState.userInitiated,
            errorMessage = refreshState.error.takeIf { users.isEmpty() },
        )
    }
        .onStart { load(force = false, userInitiated = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserListUiState())

    fun onAction(action: UserListAction) {
        when (action) {
            UserListAction.Refresh -> load(force = true, userInitiated = true)
            is UserListAction.UserClicked -> viewModelScope.launch { _events.send(UserListEvent.OpenUser(action.id)) }
        }
    }

    private fun load(force: Boolean, userInitiated: Boolean) {
        if (refresh.value.inProgress) return
        viewModelScope.launch {
            refresh.value = RefreshState(inProgress = true, userInitiated = userInitiated)
            val result = refreshUsers(force)
            val error = (result as? DataResult.Failure)?.error?.toUiText()
            refresh.value = RefreshState(error = error)
            // Cached data stays on screen; tell the user the refresh failed only when they asked for it.
            if (error != null && userInitiated) _events.send(UserListEvent.ShowMessage(error))
        }
    }

    private data class RefreshState(
        val inProgress: Boolean = false,
        val userInitiated: Boolean = false,
        val error: UiText? = null,
    )
}
```

## 2. Form screen: validate + submit
```kotlin
@Immutable
internal data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: UiText? = null,
    val isSubmitting: Boolean = false,
) {
    val canSubmit: Boolean get() = email.isNotBlank() && password.isNotBlank() && !isSubmitting
}

internal sealed interface LoginAction {
    data class EmailChanged(val value: String) : LoginAction
    data class PasswordChanged(val value: String) : LoginAction
    data object Submit : LoginAction
}

internal sealed interface LoginEvent {
    data object LoggedIn : LoginEvent
    data class ShowMessage(val message: UiText) : LoginEvent
}

internal class LoginViewModel(
    private val login: LoginUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    val events: Flow<LoginEvent> = _events.receiveAsFlow()

    fun onAction(action: LoginAction) {
        when (action) {
            is LoginAction.EmailChanged -> _state.update { it.copy(email = action.value, emailError = null) }
            is LoginAction.PasswordChanged -> _state.update { it.copy(password = action.value) }
            LoginAction.Submit -> submit()
        }
    }

    private fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true) }
            when (val result = login(current.email, current.password)) {
                is DataResult.Success -> _events.send(LoginEvent.LoggedIn)
                is DataResult.Failure -> when (val error = result.error) {
                    is AppError.Domain if error.code == "invalid_email" ->
                        _state.update { it.copy(emailError = UiText.Resource(Res.string.login_error_invalid_email)) }
                    else -> _events.send(LoginEvent.ShowMessage(error.toUiText()))
                }
            }
            _state.update { it.copy(isSubmitting = false) }
        }
    }
}
```
`is X if cond ->` guard conditions need Kotlin 2.2 or later. On older Kotlin, use `is AppError.Domain -> if (error.code == …) … else …`. `Res` is the feature module's own `…feature.profile.generated.resources.Res`, and the key goes into its `strings.xml`.

## 3. Detail screen: route argument via Koin parameters
```kotlin
internal class UserDetailViewModel(
    private val userId: Long,
    observeUser: ObserveUserUseCase,
    private val refreshUser: RefreshUserUseCase,
    private val updateUserName: UpdateUserNameUseCase,
) : ViewModel() {
    private val _events = Channel<UserDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val state: StateFlow<UserDetailUiState> = observeUser(userId)
        .map { user -> UserDetailUiState(user = user, isLoading = user == null) }
        .onStart { refreshUser(userId).onFailure { _events.send(UserDetailEvent.ShowMessage(it.toUiText())) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserDetailUiState())
    ...
}
// Koin: viewModel { params -> UserDetailViewModel(userId = params.get(), observeUser = get(), refreshUser = get(), updateUserName = get()) }
// Route: koinViewModel { parametersOf(userId) }
```
`onStart { }` runs suspending code before the first value, and the `onFailure` lambda is inline, so it can call `send`.

## 4. Error mapping helper (per feature)
```kotlin
internal fun AppError.toProfileUiText(): UiText = when (this) {
    is AppError.Domain -> when (code) {
        "invalid_name" -> UiText.Resource(Res.string.profile_error_invalid_name)
        else -> toUiText()
    }
    else -> toUiText()
}
```

## 5. Koin
```kotlin
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
// tezca-kmp:presentation
viewModelOf(::UserListViewModel)
viewModelOf(::LoginViewModel)
viewModel { params -> UserDetailViewModel(userId = params.get(), observeUser = get(), refreshUser = get(), updateUserName = get()) }
```
