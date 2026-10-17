package __PKG__.core.ui

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Text produced in a ViewModel without a Composable context; resolved in the UI. */
sealed interface UiText {
    data class Dynamic(val value: String) : UiText
    class Resource(val resource: StringResource, vararg val args: Any) : UiText

    @Composable
    fun asString(): String = when (this) {
        is Dynamic -> value
        is Resource -> stringResource(resource, *args)
    }

    suspend fun asStringAsync(): String = when (this) {
        is Dynamic -> value
        is Resource -> getString(resource, *args)
    }
}
