package com.runner.app.tracking

import com.runner.core.IntRange2
import com.runner.core.TrackPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TrackingStatus { IDLE, RUNNING, PAUSED }

data class TrackingSnapshot(
    val status: TrackingStatus = TrackingStatus.IDLE,
    val startedAtMs: Long = 0L,
    val elapsedSec: Long = 0L,
    val distanceM: Double = 0.0,
    val points: List<TrackPoint> = emptyList(),
    /** 아직 첫 GPS 신호를 못 받았으면 true */
    val waitingForGps: Boolean = false,
    /** 멈춰 있어서 자동으로 기록을 멈춘 상태 */
    val autoPaused: Boolean = false,
    /** 최근 1분 기준 현재 페이스(초/km) */
    val currentPaceSec: Double? = null,
    /** 이번 러닝의 목표 페이스 범위(초/km) */
    val targetPace: IntRange2? = null,
)

/** 서비스와 UI가 공유하는 현재 러닝 상태. */
object TrackingState {
    internal val mutable = MutableStateFlow(TrackingSnapshot())
    val state: StateFlow<TrackingSnapshot> = mutable.asStateFlow()
}
