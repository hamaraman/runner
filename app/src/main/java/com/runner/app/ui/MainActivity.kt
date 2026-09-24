package com.runner.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.runner.app.ui.crew.CrewScreen
import com.runner.app.ui.plan.PlanScreen
import com.runner.app.ui.race.RaceScreen
import com.runner.app.ui.run.RunDetailScreen
import com.runner.app.ui.run.RunScreen
import com.runner.app.ui.theme.RunnerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { RunnerTheme { RunnerNav() } }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("run", "러닝", Icons.AutoMirrored.Filled.DirectionsRun),
    Tab("plan", "훈련", Icons.Filled.CalendarMonth),
    Tab("crew", "크루", Icons.Filled.Groups),
    Tab("race", "대회", Icons.Filled.EmojiEvents),
)

@Composable
private fun RunnerNav() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route.orEmpty()

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = current.substringBefore("?").substringBefore("/") == tab.route,
                        onClick = { nav.goTab(tab.route) },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "run", modifier = Modifier.padding(padding)) {
            composable("run") {
                RunScreen(onOpenRun = { id -> nav.navigate("run/$id") })
            }
            composable("run/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                RunDetailScreen(id = it.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
            }
            composable(
                "plan?raceId={raceId}",
                arguments = listOf(navArgument("raceId") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) {
                PlanScreen(raceId = it.arguments?.getString("raceId"))
            }
            composable("crew") { CrewScreen() }
            composable("race") {
                RaceScreen(onMakePlan = { raceId -> nav.goTab("plan?raceId=$raceId") })
            }
        }
    }
}

private fun NavHostController.goTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = !route.contains("?")
}
