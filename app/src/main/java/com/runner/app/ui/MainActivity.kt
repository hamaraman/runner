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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
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
                if (adsReady && current != "routes") NativeAdBar()
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
private fun NativeAdBar() {
    val ctx = LocalContext.current
    val unitId = stringResource(R.string.admob_native_id)
    var ad by remember { mutableStateOf<NativeAd?>(null) }
    DisposableEffect(unitId) {
        var disposed = false
        AdLoader.Builder(ctx, unitId)
            .forNativeAd { if (disposed) it.destroy() else { ad?.destroy(); ad = it } }
            .withNativeAdOptions(NativeAdOptions.Builder().setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT).build())
            .build()
            .loadAd(AdRequest.Builder().build())
        onDispose { disposed = true; ad?.destroy(); ad = null }
    }
    val loaded = ad ?: return // 로드 전·실패 시 자리 차지 안 함
    val colors = MaterialTheme.colorScheme
    val fg = colors.onSurface.toArgb()
    val accent = colors.primary.toArgb()
    val onAccent = colors.onPrimary.toArgb()
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { c -> nativeAdView(c, fg, accent, onAccent) },
        update = { it.bind(loaded) },
    )
}

// 작은 템플릿: 아이콘 · [광고] 제목 / 본문 · 버튼
private fun nativeAdView(c: Context, fg: Int, accent: Int, onAccent: Int): NativeAdView {
    val dp = c.resources.displayMetrics.density
    fun px(v: Int) = (v * dp).toInt()
    val icon = ImageView(c).apply { layoutParams = LinearLayout.LayoutParams(px(40), px(40)) }
    val badge = TextView(c).apply {
        text = "광고"; textSize = 10f; setTextColor(onAccent); setBackgroundColor(accent)
        setPadding(px(4), 0, px(4), 0)
    }
    val headline = TextView(c).apply {
        textSize = 14f; setTextColor(fg); setTypeface(typeface, Typeface.BOLD)
        maxLines = 1; ellipsize = TextUtils.TruncateAt.END; setPadding(px(6), 0, 0, 0)
    }
    val body = TextView(c).apply {
        textSize = 12f; setTextColor(fg); alpha = 0.7f
        maxLines = 1; ellipsize = TextUtils.TruncateAt.END
    }
    val cta = Button(c).apply {
        textSize = 12f; setTextColor(onAccent); setBackgroundColor(accent); isAllCaps = false
        minHeight = 0; minimumHeight = 0; setPadding(px(12), px(6), px(12), px(6))
    }
    val titleRow = LinearLayout(c).apply { gravity = Gravity.CENTER_VERTICAL; addView(badge); addView(headline) }
    val texts = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = px(10); marginEnd = px(10) }
        addView(titleRow); addView(body)
    }
    val row = LinearLayout(c).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(px(12), px(8), px(12), px(8))
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        addView(icon); addView(texts); addView(cta)
    }
    return NativeAdView(c).apply {
        addView(row)
        iconView = icon; headlineView = headline; bodyView = body; callToActionView = cta
    }
}

private fun NativeAdView.bind(ad: NativeAd) {
    (headlineView as TextView).text = ad.headline
    (bodyView as TextView).apply { text = ad.body; visibility = if (ad.body == null) android.view.View.GONE else android.view.View.VISIBLE }
    (callToActionView as Button).apply { text = ad.callToAction; visibility = if (ad.callToAction == null) android.view.View.GONE else android.view.View.VISIBLE }
    (iconView as ImageView).apply { setImageDrawable(ad.icon?.drawable); visibility = if (ad.icon == null) android.view.View.GONE else android.view.View.VISIBLE }
    setNativeAd(ad)
}
