# make-usecase templates

## Observe (Flow) — a thin pass-through is fine: it keeps ViewModels off repositories
```kotlin
package com.acme.gym.feature.profile.domain.usecase

import com.acme.gym.feature.profile.domain.model.User
import com.acme.gym.feature.profile.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Users sorted for display; premium members first. */
internal class ObserveUsersUseCase(private val repository: UserRepository) {
    operator fun invoke(): Flow<List<User>> =
        repository.observeUsers().map { users -> users.sortedWith(compareByDescending<User> { it.plan == Plan.PREMIUM }.thenBy { it.fullName }) }
}
```

## Command / one-shot (suspend + DataResult) with a business rule
```kotlin
internal class UpdateUserNameUseCase(private val repository: UserRepository) {
    suspend operator fun invoke(id: Long, fullName: String): DataResult<User> {
        val name = fullName.trim()
        if (name.length !in 2..60) return DataResult.Failure(AppError.Domain(code = "invalid_name"))
        return repository.updateName(id, name)
    }
}
```

## Refresh (sync trigger used by pull-to-refresh / screen start)
```kotlin
internal class RefreshUsersUseCase(private val repository: UserRepository) {
    suspend operator fun invoke(force: Boolean = false): DataResult<Unit> = repository.refreshUsers(force)
}
```

## Several repositories, one answer
```kotlin
internal class ObserveDashboardUseCase(
    private val workouts: WorkoutRepository,
    private val payments: PaymentRepository,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
) {
    operator fun invoke(): Flow<Dashboard> = combine(
        workouts.observeThisWeek(),
        payments.observeActiveSubscription(),
    ) { week, subscription ->
        Dashboard(
            workoutsThisWeek = week.size,
            minutesThisWeek = week.sumOf { it.duration.inWholeMinutes },
            subscriptionExpiresInDays = subscription?.let { (it.expiresAt - clock.now()).inWholeDays },
        )
    }.flowOn(dispatchers.default)   // only because the combining work is non-trivial
}
```

## Composition (a use case using another use case)
```kotlin
internal class LoginUseCase(
    private val validateEmail: ValidateEmailUseCase,
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(email: String, password: String): DataResult<Session> {
        if (!validateEmail(email)) return DataResult.Failure(AppError.Domain("invalid_email"))
        if (password.isEmpty()) return DataResult.Failure(AppError.Domain("empty_password"))
        // No strength rules here: they belong to sign-up (RegisterUseCase). Applying them at login
        // would lock out existing users whose passwords predate the rule.
        return authRepository.login(email.trim().lowercase(), password)
    }
}

/** Pure rule, reused by login, registration and profile edit. */
internal class ValidateEmailUseCase {
    private val pattern = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    operator fun invoke(email: String): Boolean = pattern.matches(email.trim())
}
```

## SDK mode
```kotlin
public class GetProfileUseCase internal constructor(private val repository: UserRepository) {
    public suspend operator fun invoke(id: Long): DataResult<User> {
        repository.refreshUser(id)                         // best effort; cached value still returned
        return repository.observeUser(id).first()?.let { DataResult.Success(it) }
            ?: DataResult.Failure(AppError.Local.NotFound)
    }
}

// <Feature>Feature.kt
public class ProfileFeature internal constructor(private val koin: Koin) {
    // tezca-kmp:sdk-usecases
    public val getProfile: GetProfileUseCase get() = koin.get()
}
```
Swift (SKIE):
```swift
import FitCoreSdk

let result = try await FitCore.shared.profile.getProfile.invoke(id: 42)
switch onEnum(of: result) {
case .success(let success): show(success.data)      // typed: DataResult is a generic class
case .failure(let failure): handle(failure.error)
}
```

## Koin
```kotlin
import org.koin.core.module.dsl.factoryOf
// tezca-kmp:domain
factoryOf(::ObserveUsersUseCase)
factoryOf(::UpdateUserNameUseCase)
factory { ObserveDashboardUseCase(get(), get(), Clock.System, get()) }   // non-injected Clock
```
