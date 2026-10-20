package com.runner.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.runner.app.R
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.runner.app.CrashReport
import com.runner.app.ui.plan.PlanScreen
import com.runner.app.ui.race.RaceScreen
import com.runner.app.ui.run.AllRoutesScreen
import com.runner.app.ui.run.RunDetailScreen
import com.runner.app.ui.run.RunScreen
import com.runner.app.ui.run.ShoesScreen
import com.runner.app.ui.theme.RunnerTheme

class MainActivity : ComponentActivity() {
    private val adsReady = mutableStateOf(false)
    private val privacyOptionsRequired = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RunnerTheme {
                CrashReport()
                UpdateCheck()
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

private data class Tab(val route: String, val label: String, val icon: @Composable () -> ImageVector)

private val tabs = listOf(
    Tab("run", "러닝") { Icons.AutoMirrored.Filled.DirectionsRun },
    Tab("plan", "훈련") { Icons.Filled.CalendarMonth },
    Tab("shoes", "러닝화") { ImageVector.vectorResource(R.drawable.ic_shoe) },
    Tab("race", "대회") { Icons.Filled.EmojiEvents },
)

@Composable
private fun RunnerNav(adsReady: Boolean, onPrivacyOptions: (() -> Unit)?) {
    val nav = rememberNavController()
    val ad = if (adsReady) rememberNativeAd() else null
    // 탭 화면은 Pager에 계속 살려둔다. 탭마다 화면을 새로 만들면 첫 프레임이 길어 전환이 끊긴다.
    val pager = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()
    var planRequest by remember { mutableStateOf<Pair<Int, String>?>(null) }
    fun goTab(page: Int) {
        nav.popBackStack("main", inclusive = false)
        scope.launch { pager.scrollToPage(page) }
    }
    BackHandler(pager.currentPage != 0) { goTab(0) }

    val context = LocalContext.current
    // 탭을 처음 열 때마다 그 탭의 코치마크를 한 번씩 보여준다
    var coachMarkPage by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(pager.currentPage) {
        coachMarkPage = pager.currentPage.takeIf { !context.coachMarkDone(it) }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                NavigationBar(Modifier.coachMarkTarget("coach_tabs")) {
                    tabs.forEachIndexed { i, tab ->
                        NavigationBarItem(
                            selected = pager.currentPage == i,
                            onClick = { goTab(i) },
                            icon = { Icon(tab.icon(), contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            },
        ) { padding ->
            NavHost(
                nav,
                startDestination = "main",
                modifier = Modifier.padding(padding),
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
            ) {
                composable("main") {
                    HorizontalPager(pager, userScrollEnabled = false, beyondViewportPageCount = tabs.size - 1, key = { it }) { page ->
                        when (page) {
                            0 -> RunScreen(onOpenRun = { id -> nav.navigate("run/$id") }, onOpenAllRoutes = { nav.navigate("routes") }, onOpenShoes = { goTab(2) }, onPrivacyOptions = onPrivacyOptions, ad = ad)
                            // 대회에서 '훈련표 만들기'를 누를 때마다 새 폼으로 열리게 요청 번호로 키를 준다
                            1 -> key(planRequest) { PlanScreen(raceId = planRequest?.second) }
                            2 -> ShoesScreen()
                            else -> RaceScreen(onMakePlan = { raceId ->
                                planRequest = (planRequest?.first ?: 0) + 1 to raceId
                                goTab(1)
                            })
                        }
                    }
                }
                composable("routes") { AllRoutesScreen(onBack = { nav.popBackStack() }) }
                composable("run/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                    RunDetailScreen(id = it.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
                }
            }
        }
        coachMarkPage?.let { page ->
            CoachMarkOverlay(page = page, onFinished = { coachMarkPage = null })
        }
    }
}
