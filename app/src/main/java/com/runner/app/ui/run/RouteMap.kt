package com.runner.app.ui.run

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.CameraPosition
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.NaverMapOptions
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.Overlay
import com.naver.maps.map.overlay.OverlayImage
import com.naver.maps.map.overlay.PathOverlay
import com.naver.maps.map.util.MarkerIcons
import com.runner.core.TrackPoint

private val SEOUL = LatLng(37.5665, 126.9780)
private const val ROUTE_BLUE = 0xFF1E6BFF.toInt()
private const val ROUTE_OUTLINE = 0xFF0B3FB0.toInt()
private fun dp(v: Int) = (v * android.content.res.Resources.getSystem().displayMetrics.density).toInt()

/**
 * 네이버 지도 위에 러닝 경로를 그린다.
 * @param follow true면 마지막 위치를 따라가고(러닝 중), false면 전체 경로가 보이게 맞춘다(기록 상세).
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun RouteMap(points: List<TrackPoint>, modifier: Modifier = Modifier, follow: Boolean = false, markers: Boolean = true) {
    val context = LocalContext.current
    val lineColor = ROUTE_BLUE
    // 지도는 비동기로 준비된다. 준비 전 들어온 points는 준비되는 순간 다시 그린다.
    val state = remember { MapState() }
    val mapView = remember {
        MapView(context, NaverMapOptions().camera(CameraPosition(SEOUL, 16.0)).zoomControlEnabled(false)).apply {
            onCreate(null)
            getMapAsync { map ->
                state.map = map
                state.draw(state.lastPoints, lineColor, follow, markers)
            }
            // 스크롤 목록 안에서도 지도 드래그가 먹히도록
            setOnTouchListener { v, _ ->
                v.parent?.requestDisallowInterceptTouchEvent(true)
                false
            }
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier, update = {
        state.lastPoints = points
        state.draw(points, lineColor, follow, markers)
    })
}

private class MapState {
    var map: NaverMap? = null
    var lastPoints: List<TrackPoint> = emptyList()
    private var fitted = false
    private val overlays = mutableListOf<Overlay>()

    fun draw(points: List<TrackPoint>, lineColor: Int, follow: Boolean, markers: Boolean) {
        val map = map ?: return
        overlays.forEach { it.map = null }
        overlays.clear()
        val geo = points.map { LatLng(it.lat, it.lng) }
        // 일시정지 구간끼리는 선을 잇지 않도록 segment별로 따로 그린다 (PathOverlay는 2점 이상 필요)
        points.indices.groupBy { points[it].segment }.values.filter { it.size >= 2 }.forEach { idx ->
            overlays += PathOverlay(idx.map { geo[it] }).apply {
                // 네비게이션처럼 굵은 파란 선 + 진한 테두리 + 진행 방향 화살표 (px 단위라 dp로 환산)
                color = lineColor
                width = dp(10)
                outlineColor = ROUTE_OUTLINE
                outlineWidth = dp(1)
                patternImage = ARROW
                patternInterval = dp(28)
            }
        }
        if (markers && geo.isNotEmpty()) {
            overlays += Marker(geo.first()).apply { icon = MarkerIcons.GREEN; captionText = "출발" }
            if (!follow && geo.size > 1) overlays += Marker(geo.last()).apply { icon = MarkerIcons.RED; captionText = "도착" }
        }
        overlays.forEach { it.map = map }

        when {
            follow && geo.isNotEmpty() -> map.moveCamera(CameraUpdate.scrollTo(geo.last()))
            !follow && !fitted && geo.size > 1 -> {
                fitted = true
                map.moveCamera(CameraUpdate.fitBounds(LatLngBounds.from(geo), 80))
            }
            !follow && !fitted && geo.size == 1 -> map.moveCamera(CameraUpdate.scrollTo(geo.first()))
        }
    }
}

/** 흰 꺾쇠(^) 화살표. 패턴 이미지는 위쪽이 경로 진행 방향이다. */
private val ARROW: OverlayImage by lazy {
    val w = dp(8)
    val h = dp(6)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val pad = paint.strokeWidth
    Canvas(bmp).drawPath(Path().apply {
        moveTo(pad, h - pad)
        lineTo(w / 2f, pad)
        lineTo(w - pad, h - pad)
    }, paint)
    OverlayImage.fromBitmap(bmp)
}

private fun dp(v: Float) = v * android.content.res.Resources.getSystem().displayMetrics.density
