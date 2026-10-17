package __PKG__.feature.__FPKG__.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import kotlinx.serialization.Serializable

/** Type-safe destinations of the __FEATURE__ feature. make-screen adds one entry per screen. */
sealed interface __Feature__Destination {
    // tezca-kmp:destinations (e.g. @Serializable data object List : __Feature__Destination)
}

/**
 * Screens of the __FEATURE__ feature. Navigation that leaves the feature is exposed as callback
 * parameters (onFinished, onOpenPayments...) and wired in :shared's AppNavHost, so features never
 * depend on each other.
 */
fun NavGraphBuilder.__feature__Graph(
    navController: NavController,
    // tezca-kmp:graph-callbacks
) {
    // tezca-kmp:graph-composables (make-screen adds composable<__Feature__Destination.X> { XRoute(...) })
}
