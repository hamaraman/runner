package com.runner.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** 개인 최고 기록. 시간 기록은 GPS 1km 구간 중 가장 빠른 연속 구간으로 잰다. */
data class PersonalBests(
    val longestM: Double?,
    val best1kSec: Long?,
    val best5kSec: Long?,
    val best10kSec: Long?,
)

object Records {
    /** 1km 구간 기록 중 연속 [km]개 합의 최솟값. 구간이 모자라면 null. */
    fun bestWindowSec(splits: List<Long>, km: Int): Long? =
        if (splits.size < km) null else splits.windowed(km).minOf { it.sum() }

    fun bests(runs: List<RunRecord>): PersonalBests {
        val splits = runs.map { Geo.splitsSec(it.points) }
        fun best(km: Int) = splits.mapNotNull { bestWindowSec(it, km) }.minOrNull()
        return PersonalBests(runs.maxOfOrNull { it.distanceM }, best(1), best(5), best(10))
    }

    /** [run]이 이전 기록들([before])을 깬 항목 이름. 첫 기록은 축하할 게 없어 빈 목록. */
    fun newBests(run: RunRecord, before: List<RunRecord>): List<String> {
        if (before.isEmpty()) return emptyList()
        val old = bests(before)
        val now = bests(listOf(run))
        fun beat(n: Long?, o: Long?) = n != null && (o == null || n < o)
        return buildList {
            if (run.distanceM > (old.longestM ?: 0.0)) add("최장 거리")
            if (beat(now.best1kSec, old.best1kSec)) add("1km")
            if (beat(now.best5kSec, old.best5kSec)) add("5km")
            if (beat(now.best10kSec, old.best10kSec)) add("10km")
        }
    }

    /** 이번 주(월요일 시작)까지 끊기지 않고 달린 주 수. 이번 주에 아직 안 뛰었으면 지난주부터 센다. */
    fun weekStreak(runDays: Collection<LocalDate>, today: LocalDate): Int {
        val weeks = runDays.map { it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }.toSet()
        var week = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        if (week !in weeks) week = week.minusWeeks(1)
        var n = 0
        while (week in weeks) { n++; week = week.minusWeeks(1) }
        return n
    }
}
