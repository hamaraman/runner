package com.runner.app.data

import android.content.Context
import com.runner.core.CoachSettings
import com.runner.core.Crew
import com.runner.core.CrewEvent
import com.runner.core.Race
import com.runner.core.RunRecord
import com.runner.core.TrainingPlan
import com.runner.core.Workout
import com.runner.core.WorkoutType
import com.runner.core.markRun
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
    val coach = JsonStore(File(dir, "coach.json"), CoachSettings.serializer(), CoachSettings(), scope)

    /** 러닝을 저장하고 훈련표에 반영한다(같은 날 없으면 같은 주 미완료 운동). */
    fun saveRun(run: RunRecord) {
        runs.update { listOf(run) + it }
        val day = Instant.ofEpochMilli(run.startedAtMs).atZone(ZoneId.systemDefault()).toLocalDate()
        plan.update { it?.markRun(day.toEpochDay()) }
    }

    /** 새 훈련 계획을 저장하면서, 지금까지의 러닝 기록을 반영한다. */
    fun setPlan(p: TrainingPlan) {
        val zone = ZoneId.systemDefault()
        val runDays = runs.value.map { Instant.ofEpochMilli(it.startedAtMs).atZone(zone).toLocalDate().toEpochDay() }
        plan.update { runDays.sorted().fold(p) { acc, day -> acc.markRun(day) } }
    }

    fun deleteRun(id: String) = runs.update { list -> list.filterNot { it.id == id } }

    fun setWorkoutDone(epochDay: Long, done: Boolean) {
        plan.update { p ->
            p?.copy(weeks = p.weeks.map { w ->
                w.copy(workouts = w.workouts.map { wo ->
                    if (wo.dateEpochDay == epochDay && wo.type != WorkoutType.REST) {
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
