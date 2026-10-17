package __PKG__.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Placeholder start destination until the first feature sets its own (new-feature replaces it). */
@Serializable
data object WelcomeDestination

/**
 * The only NavHost. Each feature contributes `NavGraphBuilder.<feature>Graph(...)` with callback
 * lambdas; cross-feature navigation is decided here so features never depend on each other.
 */
@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = WelcomeDestination, // tezca-kmp:start-destination
    ) {
        composable<WelcomeDestination> {
            Box(Modifier.fillMaxSize().safeContentPadding(), contentAlignment = Alignment.Center) {
                Text("__APP_NAME__", style = MaterialTheme.typography.headlineMedium)
            }
        }
        // tezca-kmp:feature-graphs (new-feature adds <feature>Graph(navController) calls below this line)
    }
}
