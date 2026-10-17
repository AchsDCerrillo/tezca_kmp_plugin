# make-screen templates

## List screen: `presentation/userlist/UserListScreen.kt`
```kotlin
package com.acme.gym.feature.profile.presentation.userlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.acme.gym.core.ui.ObserveAsEvents
import com.acme.gym.core.ui.components.ErrorContent
import com.acme.gym.core.ui.components.LoadingContent
import com.acme.gym.core.ui.theme.AppTheme
import com.acme.gym.core.ui.theme.Dimens
import com.acme.gym.feature.profile.domain.model.Plan
import com.acme.gym.feature.profile.domain.model.User
import com.acme.gym.feature.profile.generated.resources.Res
import com.acme.gym.feature.profile.generated.resources.user_list_empty
import com.acme.gym.feature.profile.generated.resources.user_list_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Instant

@Composable
internal fun UserListRoute(
    onOpenUser: (Long) -> Unit,
    viewModel: UserListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    ObserveAsEvents(viewModel.events, snackbarHostState) { event ->
        when (event) {
            is UserListEvent.OpenUser -> onOpenUser(event.id)
            is UserListEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message.asStringAsync())
        }
    }

    UserListScreen(state = state, onAction = viewModel::onAction, snackbarHostState = snackbarHostState)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UserListScreen(
    state: UserListUiState,
    onAction: (UserListAction) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(Res.string.user_list_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { onAction(UserListAction.Refresh) },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            // Every branch is scrollable (LazyColumn), otherwise pull-to-refresh can't be triggered on an
            // empty or offline list.
            when {
                state.isLoading -> LoadingContent()
                state.errorMessage != null -> LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        ErrorContent(
                            message = state.errorMessage.asString(),
                            onRetry = { onAction(UserListAction.Refresh) },
                            modifier = Modifier.fillParentMaxSize(),
                        )
                    }
                }
                state.isEmpty -> LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(Res.string.user_list_empty))
                        }
                    }
                }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = Dimens.spacingS)) {
                    items(state.users, key = { it.id }) { user ->
                        ListItem(
                            headlineContent = { Text(user.fullName) },
                            supportingContent = { Text(user.email) },
                            modifier = Modifier.clickable { onAction(UserListAction.UserClicked(user.id)) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

private val previewUsers = listOf(
    User(1, "Ana López", "ana@acme.com", null, Plan.PREMIUM, Instant.fromEpochMilliseconds(0)),
    User(2, "Luis Pérez", "luis@acme.com", null, Plan.FREE, Instant.fromEpochMilliseconds(0)),
)

@Preview
@Composable
private fun UserListScreenPreview() {
    AppTheme { UserListScreen(state = UserListUiState(users = previewUsers, isLoading = false), onAction = {}) }
}

@Preview
@Composable
private fun UserListScreenEmptyPreview() {
    AppTheme { UserListScreen(state = UserListUiState(isLoading = false), onAction = {}) }
}
```
`strings.xml`:
```xml
<resources>
    <string name="user_list_title">Members</string>
    <string name="user_list_empty">No members yet.</string>
</resources>
```

## Form screen (core of LoginScreen)
```kotlin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.acme.gym.core.ui.ObserveAsEvents
import com.acme.gym.core.ui.theme.Dimens
import com.acme.gym.feature.onboarding.generated.resources.*   // Res + the login_* keys
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun LoginRoute(onLoggedIn: () -> Unit, viewModel: LoginViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            LoginEvent.LoggedIn -> onLoggedIn()
            is LoginEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message.asStringAsync())
        }
    }
    LoginScreen(state, viewModel::onAction, snackbarHostState = snackbarHostState)
}

@Composable
internal fun LoginScreen(
    state: LoginUiState,
    onAction: (LoginAction) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(modifier = modifier, snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(Dimens.spacingL).imePadding(),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingM, Alignment.CenterVertically),
        ) {
            Text(stringResource(Res.string.login_title), style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(
                value = state.email,
                onValueChange = { onAction(LoginAction.EmailChanged(it)) },
                label = { Text(stringResource(Res.string.login_email_label)) },
                isError = state.emailError != null,
                supportingText = state.emailError?.let { error -> { Text(error.asString()) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = { onAction(LoginAction.PasswordChanged(it)) },
                label = { Text(stringResource(Res.string.login_password_label)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onAction(LoginAction.Submit) }),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { onAction(LoginAction.Submit) }, enabled = state.canSubmit, modifier = Modifier.fillMaxWidth()) {
                if (state.isSubmitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(stringResource(Res.string.login_submit))
            }
        }
    }
}
```

## Navigation: `navigation/ProfileNavigation.kt`
```kotlin
sealed interface ProfileDestination {
    // tezca-kmp:destinations
    @Serializable data object UserList : ProfileDestination
    @Serializable data class UserDetail(val userId: Long) : ProfileDestination
}

fun NavGraphBuilder.profileGraph(
    navController: NavController,
    // tezca-kmp:graph-callbacks
    onOpenPayments: () -> Unit,
) {
    // tezca-kmp:graph-composables
    composable<ProfileDestination.UserList> {
        UserListRoute(onOpenUser = { id -> navController.navigate(ProfileDestination.UserDetail(id)) })
    }
    composable<ProfileDestination.UserDetail> { entry ->
        val route = entry.toRoute<ProfileDestination.UserDetail>()
        UserDetailRoute(
            viewModel = koinViewModel { parametersOf(route.userId) },
            onBack = navController::navigateUp,
            onOpenPayments = onOpenPayments,
        )
    }
}
```
Imports: `androidx.navigation.compose.composable`, `androidx.navigation.toRoute`, `kotlinx.serialization.Serializable`, `org.koin.compose.viewmodel.koinViewModel`, `org.koin.core.parameter.parametersOf`.

In `:shared` `AppNavHost`, after adding a callback:
```kotlin
profileGraph(navController = navController, onOpenPayments = { navController.navigate(PaymentsDestination.Plans) })
```
Use `startDestination = ProfileDestination.UserList` if this screen opens the app.
