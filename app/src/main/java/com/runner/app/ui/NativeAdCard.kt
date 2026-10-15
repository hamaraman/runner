package com.runner.app.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.runner.app.R

private class AdColors(val surface: Int, val fg: Int, val muted: Int, val accent: Int, val onAccent: Int)

/** 목록 사이에 다른 카드처럼 섞이는 네이티브 광고. 로드 전·실패 시 아무것도 그리지 않는다. */
// ponytail: LazyColumn에서 화면 밖으로 나가면 다시 로드됨, 광고를 여러 곳에 넣게 되면 화면 단위로 끌어올릴 것
@Composable
fun NativeAdCard(modifier: Modifier = Modifier) {
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
    val loaded = ad ?: return
    val c = MaterialTheme.colorScheme
    val colors = AdColors(
        c.surfaceContainerLow.toArgb(), c.onSurface.toArgb(), c.onSurfaceVariant.toArgb(), c.primary.toArgb(), c.onPrimary.toArgb(),
    )
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { nativeAdCardView(it, colors) },
        update = { it.bind(loaded) },
    )
}

// 한 줄 카드: 아이콘 · [광고] 제목 / 본문 · 버튼. 카드 모양은 앱의 다른 카드(16dp, surfaceContainerLow)와 맞춘다.
// 미디어(큰 이미지·영상)는 화면을 너무 차지해서 뺐다.
private fun nativeAdCardView(c: Context, k: AdColors): NativeAdView {
    val dp = c.resources.displayMetrics.density
    fun px(v: Int) = (v * dp).toInt()
    fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = px(radius).toFloat() }

    val icon = ImageView(c).apply {
        layoutParams = LinearLayout.LayoutParams(px(40), px(40))
        background = rounded(k.surface, 8); clipToOutline = true
    }
    val badge = TextView(c).apply {
        text = "광고"; textSize = 10f; setTextColor(k.onAccent); background = rounded(k.accent, 4)
        setPadding(px(5), 0, px(5), 0)
    }
    val headline = TextView(c).apply {
        textSize = 14f; setTextColor(k.fg); setTypeface(typeface, Typeface.BOLD)
        maxLines = 1; ellipsize = TextUtils.TruncateAt.END; setPadding(px(6), 0, 0, 0)
    }
    val body = TextView(c).apply { textSize = 12f; setTextColor(k.muted); maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    val titleRow = LinearLayout(c).apply { gravity = Gravity.CENTER_VERTICAL; addView(badge); addView(headline) }
    val texts = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = px(12); marginEnd = px(8) }
        addView(titleRow); addView(body)
    }
    val cta = Button(c).apply {
        textSize = 12f; setTextColor(k.onAccent); background = rounded(k.accent, 16); isAllCaps = false
        stateListAnimator = null; minWidth = 0; minimumWidth = 0; minHeight = px(32); minimumHeight = px(32)
        setPadding(px(14), 0, px(14), 0)
    }
    val row = LinearLayout(c).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(px(14), px(12), px(14), px(12))
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        addView(icon); addView(texts); addView(cta)
    }
    return NativeAdView(c).apply {
        background = rounded(k.surface, 16); clipToOutline = true
        addView(row)
        iconView = icon; headlineView = headline; bodyView = body; callToActionView = cta
    }
}

private fun NativeAdView.bind(ad: NativeAd) {
    fun View.shownIf(present: Boolean) { visibility = if (present) View.VISIBLE else View.GONE }
    (headlineView as TextView).text = ad.headline
    (bodyView as TextView).apply { text = ad.body ?: ad.advertiser; shownIf(text.isNotEmpty()) }
    (callToActionView as Button).apply { text = ad.callToAction; shownIf(ad.callToAction != null) }
    (iconView as ImageView).apply { setImageDrawable(ad.icon?.drawable); shownIf(ad.icon != null) }
    setNativeAd(ad)
}
