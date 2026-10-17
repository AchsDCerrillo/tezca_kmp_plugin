package __PKG__

import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
import __PKG__.core.ui.theme.AppTheme
import __PKG__.navigation.AppNavHost

/** Root composable for both platforms. Koin is started by the platform entry point before this runs. */
@Composable
fun App() {
    AppTheme {
        val navController = rememberNavController()
        AppNavHost(navController = navController)
    }
}
