package dev.linakbed.app.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.linakbed.app.R
import dev.linakbed.app.feature.about.AboutScreen
import dev.linakbed.app.feature.diagnostics.DiagnosticsScreen
import dev.linakbed.app.feature.home.HomeScreen
import dev.linakbed.app.feature.log.ExecutionLogScreen
import dev.linakbed.app.feature.routines.RoutinesScreen
import dev.linakbed.app.feature.settings.SettingsScreen
import dev.linakbed.app.ui.LanguageToggle

/** 침대가 연결된 뒤의 화면들: 홈 → 설정(하위 화면) / 정보. */
@Composable
fun LinakNavHost(navController: NavHostController = rememberNavController()) {
    val openPairing = {
        navController.navigate(Route.SETTINGS) { launchSingleTop = true }
    }
    NavHost(navController, startDestination = Route.HOME) {
        composable(Route.HOME) {
            HomeScreen(onOpenSettings = { navController.navigate(Route.SETTINGS) }, onOpenAbout = { navController.navigate(Route.ABOUT) })
        }
        page(Route.SETTINGS, R.string.nav_settings, navController) { SettingsScreen(onOpen = { navController.navigate(it.route) }) }
        page(Route.ABOUT, R.string.about_title, navController) { AboutScreen() }
        page(SettingsRoute.ROUTINES.route, SettingsRoute.ROUTINES.title, navController) { RoutinesScreen(onOpenPairing = openPairing) }
        page(SettingsRoute.LOG.route, SettingsRoute.LOG.title, navController) { ExecutionLogScreen() }
        page(
            SettingsRoute.DIAGNOSTICS.route,
            SettingsRoute.DIAGNOSTICS.title,
            navController,
        ) { DiagnosticsScreen(onOpenPairing = openPairing) }
    }
}

/** 제목과 뒤로 가기가 있는 하위 화면. */
@OptIn(ExperimentalMaterial3Api::class)
private fun NavGraphBuilder.page(
    route: String,
    @StringRes title: Int,
    navController: NavHostController,
    content: @Composable () -> Unit,
) {
    composable(route) {
        Column {
            TopAppBar(
                title = { Text(stringResource(title)) },
                actions = { LanguageToggle() },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
            content()
        }
    }
}
