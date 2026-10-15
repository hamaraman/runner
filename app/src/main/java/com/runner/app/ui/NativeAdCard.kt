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
import com.google.android.gms.ads.nativead.MediaView
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

// 아이콘 · [광고] 제목 / 광고주 → 미디어 → 본문 → 버튼. 카드 모양은 앱의 다른 카드(16dp, surfaceContainerLow)와 맞춘다.
private fun nativeAdCardView(c: Context, k: AdColors): NativeAdView {
    val dp = c.resources.displayMetrics.density
    fun px(v: Int) = (v * dp).toInt()
    fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = px(radius).toFloat() }

    val icon = ImageView(c).apply { layoutParams = LinearLayout.LayoutParams(px(36), px(36)) }
    val badge = TextView(c).apply {
        text = "광고"; textSize = 10f; setTextColor(k.onAccent); background = rounded(k.accent, 4)
        setPadding(px(5), 0, px(5), 0)
    }
    val headline = TextView(c).apply {
        textSize = 15f; setTextColor(k.fg); setTypeface(typeface, Typeface.BOLD)
        maxLines = 1; ellipsize = TextUtils.TruncateAt.END; setPadding(px(6), 0, 0, 0)
    }
    val advertiser = TextView(c).apply { textSize = 12f; setTextColor(k.muted); maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    val titleRow = LinearLayout(c).apply { gravity = Gravity.CENTER_VERTICAL; addView(badge); addView(headline) }
    val titles = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = px(10) }
        addView(titleRow); addView(advertiser)
    }
    val header = LinearLayout(c).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 0, px(24), 0) // AdChoices 아이콘 자리
        addView(icon); addView(titles)
    }
    val media = MediaView(c).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, px(180)).apply { topMargin = px(12) }
        setImageScaleType(ImageView.ScaleType.CENTER_CROP)
        background = rounded(k.surface, 8); clipToOutline = true
    }
    val body = TextView(c).apply {
        textSize = 13f; setTextColor(k.muted); maxLines = 2; ellipsize = TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = px(10) }
    }
    val cta = Button(c).apply {
        textSize = 14f; setTextColor(k.onAccent); background = rounded(k.accent, 24); isAllCaps = false
        stateListAnimator = null; minHeight = px(44); minimumHeight = px(44)
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = px(12) }
    }
    val column = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(px(16), px(16), px(16), px(16))
        addView(header); addView(media); addView(body); addView(cta)
    }
    return NativeAdView(c).apply {
        background = rounded(k.surface, 16); clipToOutline = true
        addView(column)
        iconView = icon; headlineView = headline; advertiserView = advertiser
        mediaView = media; bodyView = body; callToActionView = cta
    }
}

private fun NativeAdView.bind(ad: NativeAd) {
    fun View.shownIf(present: Boolean) { visibility = if (present) View.VISIBLE else View.GONE }
    (headlineView as TextView).text = ad.headline
    (advertiserView as TextView).apply { text = ad.advertiser; shownIf(ad.advertiser != null) }
    (bodyView as TextView).apply { text = ad.body; shownIf(ad.body != null) }
    (callToActionView as Button).apply { text = ad.callToAction; shownIf(ad.callToAction != null) }
    (iconView as ImageView).apply { setImageDrawable(ad.icon?.drawable); shownIf(ad.icon != null) }
    mediaView?.shownIf(ad.mediaContent != null)
    setNativeAd(ad) // 미디어는 setNativeAd가 mediaView에 채운다
}
