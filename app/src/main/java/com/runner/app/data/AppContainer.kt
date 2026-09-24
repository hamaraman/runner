package com.runner.app.data

import android.content.Context
import com.runner.core.Crew
import com.runner.core.CrewEvent
import com.runner.core.Race
import com.runner.core.RunRecord
import com.runner.core.TrainingPlan
import com.runner.core.Workout
import com.runner.core.WorkoutType
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable

/** 앱 전역 의존성 모음(수동 DI). */
class AppContainer(context: Context) {
    private val scope = CoroutineScope(SupervisorJob())
    private val dir = File(context.filesDir, "data").apply { mkdirs() }

    val runs = JsonStore(File(dir, "runs.json"), ListSerializer(RunRecord.serializer()), emptyList(), scope)
    val plan = JsonStore(File(dir, "plan.json"), TrainingPlan.serializer().nullable, null, scope)
    val races = JsonStore(File(dir, "races.json"), ListSerializer(Race.serializer()), emptyList(), scope)
    val crews = JsonStore(File(dir, "crews.json"), ListSerializer(Crew.serializer()), emptyList(), scope)
    val events = JsonStore(File(dir, "events.json"), ListSerializer(CrewEvent.serializer()), emptyList(), scope)

    /** 러닝을 저장하고, 오늘 훈련표에 있던 운동은 완료 처리한다. */
    fun saveRun(run: RunRecord) {
        runs.update { listOf(run) + it }
        val day = Instant.ofEpochMilli(run.startedAtMs).atZone(ZoneId.systemDefault()).toLocalDate()
        setWorkoutDone(day.toEpochDay(), true, onlyIfRun = true)
    }

    fun deleteRun(id: String) = runs.update { list -> list.filterNot { it.id == id } }

    fun setWorkoutDone(epochDay: Long, done: Boolean, onlyIfRun: Boolean = false) {
        plan.update { p ->
            p?.copy(weeks = p.weeks.map { w ->
                w.copy(workouts = w.workouts.map { wo ->
                    if (wo.dateEpochDay == epochDay && wo.type != WorkoutType.REST && (!onlyIfRun || !wo.done)) {
                        wo.copy(done = done)
                    } else {
                        wo
                    }
                })
            })
        }
    }

    fun todayWorkout(p: TrainingPlan?, today: LocalDate = LocalDate.now()): Workout? =
        p?.weeks?.asSequence()?.flatMap { it.workouts }?.firstOrNull { it.dateEpochDay == today.toEpochDay() }

    fun deleteCrew(id: String) {
        crews.update { list -> list.filterNot { it.id == id } }
        events.update { list -> list.filterNot { it.crewId == id } }
    }
}
