package com.runner.core

/** 인터벌 한 구간: 달리기 또는 걷기를 sec초 동안. */
data class IntervalStep(val run: Boolean, val sec: Int) {
    val label get() = if (run) "달리기" else "걷기"
}

data class IntervalSession(val name: String, val steps: List<IntervalStep>)

/** 지금 몇 번째 구간인지와 그 구간의 남은 시간. */
data class IntervalPosition(val index: Int, val remainingSec: Int)

object IntervalPrograms {
    private const val WARMUP_SEC = 300

    private fun session(week: Int, runSec: Int, walkSec: Int, reps: Int) = IntervalSession(
        "${week}주차 · ${Pace.formatDuration(runSec.toLong())} 달리기 × $reps",
        buildList {
            add(IntervalStep(false, WARMUP_SEC))
            repeat(reps) { i ->
                add(IntervalStep(true, runSec))
                if (i < reps - 1) add(IntervalStep(false, walkSec))
            }
            add(IntervalStep(false, WARMUP_SEC))
        },
    )

    /** 8주 초보 프로그램: 걷기와 달리기를 번갈아 30분 연속 달리기까지. 앞뒤 5분은 걷기. */
    val beginner = listOf(
        session(1, 60, 90, 8),
        session(2, 90, 90, 7),
        session(3, 120, 90, 6),
        session(4, 180, 90, 5),
        session(5, 300, 120, 3),
        session(6, 480, 120, 2),
        session(7, 600, 90, 2),
        session(8, 1800, 0, 1),
    )

    /** elapsedSec 시점의 구간. 프로그램이 끝났으면 null. */
    fun at(steps: List<IntervalStep>, elapsedSec: Long): IntervalPosition? {
        var end = 0L
        steps.forEachIndexed { i, step ->
            end += step.sec
            if (elapsedSec < end) return IntervalPosition(i, (end - elapsedSec).toInt())
        }
        return null
    }
}
