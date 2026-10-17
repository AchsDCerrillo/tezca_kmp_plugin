package __PKG__.core.common.result

/**
 * Result of any data operation (network, database, preferences). Repositories and use cases
 * return it instead of throwing, so callers on both platforms handle failures exhaustively
 * (`when` in Kotlin, `switch onEnum(of:)` in Swift through SKIE).
 *
 * A sealed *class* rather than an interface on purpose: Objective-C/Swift keep the generic type
 * of classes (`DataResultSuccess<Workout>`) but erase it for protocols.
 */
public sealed class DataResult<out T> {
    public data class Success<out T>(val data: T) : DataResult<T>()
    public data class Failure(val error: AppError) : DataResult<Nothing>()
}

public inline fun <T, R> DataResult<T>.map(transform: (T) -> R): DataResult<R> = when (this) {
    is DataResult.Success -> DataResult.Success(transform(data))
    is DataResult.Failure -> this
}

public inline fun <T, R> DataResult<T>.flatMap(transform: (T) -> DataResult<R>): DataResult<R> = when (this) {
    is DataResult.Success -> transform(data)
    is DataResult.Failure -> this
}

public inline fun <T> DataResult<T>.onSuccess(action: (T) -> Unit): DataResult<T> {
    if (this is DataResult.Success) action(data)
    return this
}

public inline fun <T> DataResult<T>.onFailure(action: (AppError) -> Unit): DataResult<T> {
    if (this is DataResult.Failure) action(error)
    return this
}

public fun <T> DataResult<T>.getOrNull(): T? = (this as? DataResult.Success)?.data

public fun <T> DataResult<T>.errorOrNull(): AppError? = (this as? DataResult.Failure)?.error

public fun DataResult<*>.asEmpty(): DataResult<Unit> = map { }
