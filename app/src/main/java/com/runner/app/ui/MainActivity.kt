package com.runner.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.runner.app.R
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
import com.runner.app.ui.run.AllRoutesScreen
import com.runner.app.ui.run.RunDetailScreen
import com.runner.app.ui.run.RunScreen
import com.runner.app.ui.theme.RunnerTheme

class MainActivity : ComponentActivity() {
    private val adsReady = mutableStateOf(false)
    private val privacyOptionsRequired = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RunnerTheme {
                RunnerNav(
                    adsReady = adsReady.value,
                    onPrivacyOptions = if (privacyOptionsRequired.value) {
                        { UserMessagingPlatform.showPrivacyOptionsForm(this) {} }
                    } else null,
                )
            }
        }

        // UMP: 필요한 지역(EEA·영국 등)에서만 동의 폼이 뜬다. 동의 결과와 상관없이 실패해도 앱은 그대로 동작.
        val consent = UserMessagingPlatform.getConsentInformation(this)
        consent.requestConsentInfoUpdate(
            this,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) {
                    privacyOptionsRequired.value = consent.privacyOptionsRequirementStatus ==
                        ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
                    startAds()
                }
            },
            { startAds() },
        )
        startAds() // 이전 세션에서 이미 동의했으면 바로 시작
    }

    private fun startAds() {
        if (adsReady.value || !UserMessagingPlatform.getConsentInformation(this).canRequestAds()) return
        MobileAds.initialize(this)
        adsReady.value = true
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
private fun RunnerNav(adsReady: Boolean, onPrivacyOptions: (() -> Unit)?) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route.orEmpty()

    Scaffold(
        bottomBar = {
            Column {
                if (adsReady) BannerAd()
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
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "run", modifier = Modifier.padding(padding)) {
            composable("run") {
                RunScreen(onOpenRun = { id -> nav.navigate("run/$id") }, onOpenAllRoutes = { nav.navigate("routes") }, onPrivacyOptions = onPrivacyOptions)
            }
            composable("routes") { AllRoutesScreen(onBack = { nav.popBackStack() }) }
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

@Composable
private fun BannerAd() {
    val unitId = stringResource(R.string.admob_banner_id)
    val widthDp = LocalConfiguration.current.screenWidthDp
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, widthDp))
                adUnitId = unitId
                loadAd(AdRequest.Builder().build())
            }
        },
        onRelease = { it.destroy() },
    )
}
