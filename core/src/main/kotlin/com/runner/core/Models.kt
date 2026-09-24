package com.runner.core

import kotlinx.serialization.Serializable

/** GPS로 받은 한 지점. */
@Serializable
data class TrackPoint(
    val lat: Double,
    val lng: Double,
    val timeMs: Long,
    val accuracyM: Float = 0f,
    /** 일시정지 후 재개하면 증가. 다른 구간끼리는 거리를 잇지 않는다. */
    val segment: Int = 0,
)

/** 완료된 러닝 1회 기록. */
@Serializable
data class RunRecord(
    val id: String,
    val startedAtMs: Long,
    val durationSec: Long,
    val distanceM: Double,
    val points: List<TrackPoint> = emptyList(),
    val memo: String = "",
) {
    /** km당 초. 거리가 너무 짧으면 null. */
    val paceSecPerKm: Double? get() = Pace.secPerKm(distanceM, durationSec)
}

enum class RaceDistance(val label: String, val km: Double) {
    FIVE_K("5K", 5.0),
    TEN_K("10K", 10.0),
    HALF("하프", 21.0975),
    FULL("풀", 42.195),
}

/** 사용자가 등록한 대회. 날짜는 epochDay(LocalDate.toEpochDay) */
@Serializable
data class Race(
    val id: String,
    val name: String,
    val dateEpochDay: Long,
    val distance: RaceDistance,
    val location: String = "",
    val url: String = "",
    val registered: Boolean = false,
    val goalTimeSec: Long? = null,
)

@Serializable
data class Crew(
    val id: String,
    val name: String,
    val area: String,
    val description: String = "",
    val joined: Boolean = true,
)

/** 크루 번개/정기런 모임. */
@Serializable
data class CrewEvent(
    val id: String,
    val crewId: String,
    val title: String,
    val dateTimeMs: Long,
    val place: String,
    val distanceKm: Double,
    val paceSecPerKm: Int? = null,
    val attending: Boolean = false,
)

enum class WorkoutType(val label: String) {
    REST("휴식"),
    EASY("이지런"),
    LONG("장거리"),
    TEMPO("템포런"),
    INTERVAL("인터벌"),
    RACE("대회"),
}

@Serializable
data class Workout(
    val dateEpochDay: Long,
    val type: WorkoutType,
    val distanceKm: Double,
    val description: String,
    val done: Boolean = false,
)

@Serializable
data class TrainingWeek(
    val index: Int,
    val phase: String,
    val totalKm: Double,
    val workouts: List<Workout>,
)

@Serializable
data class TrainingPlan(
    val goal: RaceDistance,
    val raceDateEpochDay: Long,
    val raceName: String,
    val zones: PaceZones?,
    val weeks: List<TrainingWeek>,
)

/** 훈련 강도별 목표 페이스(초/km). */
@Serializable
data class PaceZones(
    val easy: IntRange2,
    val tempo: IntRange2,
    val interval: IntRange2,
    val race: Int,
)

/** 직렬화 가능한 정수 범위(느린 쪽이 max). */
@Serializable
data class IntRange2(val min: Int, val max: Int)
