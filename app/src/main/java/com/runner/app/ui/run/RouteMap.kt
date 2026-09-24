package com.runner.app.ui.run

import android.annotation.SuppressLint
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.runner.core.TrackPoint
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

private val SEOUL = GeoPoint(37.5665, 126.9780)

/**
 * OpenStreetMap 지도 위에 러닝 경로를 그린다(API 키 불필요).
 * @param follow true면 마지막 위치를 따라가고(러닝 중), false면 전체 경로가 보이게 맞춘다(기록 상세).
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun RouteMap(points: List<TrackPoint>, modifier: Modifier = Modifier, follow: Boolean = false) {
    val context = LocalContext.current
    val lineColor = MaterialTheme.colorScheme.primary.toArgb()
    val fitted = remember { booleanArrayOf(false) }
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(16.0)
            controller.setCenter(SEOUL)
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
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier, update = { map ->
        map.overlays.clear()
        val geo = points.map { GeoPoint(it.lat, it.lng) }
        // 일시정지 구간끼리는 선을 잇지 않도록 segment별로 따로 그린다
        points.indices.groupBy { points[it].segment }.values.forEach { idx ->
            map.overlays.add(Polyline(map).apply {
                setPoints(idx.map { geo[it] })
                outlinePaint.color = lineColor
                outlinePaint.strokeWidth = 12f
            })
        }
        if (geo.isNotEmpty()) {
            map.overlays.add(marker(map, geo.first(), "출발"))
            if (!follow && geo.size > 1) map.overlays.add(marker(map, geo.last(), "도착"))
        }
        map.overlays.add(CopyrightOverlay(map.context))

        when {
            follow && geo.isNotEmpty() -> map.controller.animateTo(geo.last())
            !follow && !fitted[0] && geo.size > 1 -> {
                fitted[0] = true
                // 레이아웃이 끝난 뒤에야 크기를 알 수 있다
                map.post { map.zoomToBoundingBox(BoundingBox.fromGeoPoints(geo), false, 80) }
            }
            !follow && !fitted[0] && geo.size == 1 -> map.controller.setCenter(geo.first())
        }
        map.invalidate()
    })
}

private fun marker(map: MapView, point: GeoPoint, title: String) = Marker(map).apply {
    position = point
    this.title = title
    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
}
