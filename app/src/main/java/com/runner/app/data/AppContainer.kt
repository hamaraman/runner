package com.runner.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.runner.core.CoachSettings
import com.runner.core.Crew
import com.runner.core.CrewEvent
import com.runner.core.Race
import com.runner.core.RunRecord
import com.runner.core.Shoe
import com.runner.core.TrainingPlan
import com.runner.core.Workout
import com.runner.core.WorkoutType
import com.runner.core.markRun
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable

/** 앱 전역 의존성 모음(수동 DI). */
class AppContainer(context: Context) {
    private val scope = CoroutineScope(SupervisorJob())
    private val dir = File(context.filesDir, "data").apply { mkdirs() }
    private val photoDir = File(context.filesDir, "shoe_photos").apply { mkdirs() }
    private val resolver = context.contentResolver

    val runs = JsonStore(File(dir, "runs.json"), ListSerializer(RunRecord.serializer()), emptyList(), scope)
    val plan = JsonStore(File(dir, "plan.json"), TrainingPlan.serializer().nullable, null, scope)
    val races = JsonStore(File(dir, "races.json"), ListSerializer(Race.serializer()), emptyList(), scope)
    val crews = JsonStore(File(dir, "crews.json"), ListSerializer(Crew.serializer()), emptyList(), scope)
    val events = JsonStore(File(dir, "events.json"), ListSerializer(CrewEvent.serializer()), emptyList(), scope)
    val coach = JsonStore(File(dir, "coach.json"), CoachSettings.serializer(), CoachSettings(), scope)
    val shoes = JsonStore(File(dir, "shoes.json"), ListSerializer(Shoe.serializer()), emptyList(), scope)
    /** 진행 중인 러닝의 임시 저장본. 앱이 강제 종료돼도 다음 실행 때 살릴 수 있게 한다. */
    val draft = JsonStore(File(dir, "draft.json"), RunRecord.serializer().nullable, null, scope)

    /** 러닝을 저장하고 훈련표에 반영한다(같은 날 없으면 같은 주 미완료 운동). */
    fun saveRun(run: RunRecord) {
        val shoeId = run.shoeId ?: shoes.value.firstOrNull { !it.retired }?.id
        runs.update { listOf(run.copy(shoeId = shoeId)) + it }
        val day = Instant.ofEpochMilli(run.startedAtMs).atZone(ZoneId.systemDefault()).toLocalDate()
        plan.update { it?.markRun(day.toEpochDay()) }
    }

    /** 새 훈련 계획을 저장하면서, 지금까지의 러닝 기록을 반영한다. */
    fun setPlan(p: TrainingPlan) {
        val zone = ZoneId.systemDefault()
        val runDays = runs.value.map { Instant.ofEpochMilli(it.startedAtMs).atZone(zone).toLocalDate().toEpochDay() }
        plan.update { runDays.sorted().fold(p) { acc, day -> acc.markRun(day) } }
    }

    fun setRunShoe(runId: String, shoeId: String?) =
        runs.update { list -> list.map { if (it.id == runId) it.copy(shoeId = shoeId) else it } }

    /** 이 신발을 맨 앞(=기본 신발)으로 옮긴다. */
    fun selectShoe(id: String) = shoes.update { list -> list.sortedBy { it.id != id } }

    fun photoFile(name: String) = File(photoDir, name)
    fun shoePhoto(shoe: Shoe): File? = shoe.photo?.let(::photoFile)?.takeIf { it.exists() }

    /** 고른 사진을 긴 변 1024px JPEG로 줄여 앱 안에 복사한다(사진 선택기 URI는 나중에 못 읽는다). 실패하면 null. IO 스레드에서 부를 것. */
    fun importShoePhoto(uri: Uri): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1024) sample *= 2
        var bmp = resolver.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }!!
        val degrees = when (resolver.openInputStream(uri)!!.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 0) }) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
        if (degrees != 0) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
        val name = "${UUID.randomUUID()}.jpg"
        File(photoDir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        name
    }.getOrNull()

    fun setShoePhoto(id: String, photo: String) {
        shoes.value.firstOrNull { it.id == id }?.let { shoePhoto(it)?.delete() }
        shoes.update { list -> list.map { if (it.id == id) it.copy(photo = photo) else it } }
    }

    /** 저장되지 않은 사진 파일(새 신발 등록을 취소한 경우 등)을 지운다. */
    fun discardShoePhoto(photo: String) = photoFile(photo).delete()

    fun deleteShoe(id: String) {
        shoes.value.firstOrNull { it.id == id }?.let { shoePhoto(it)?.delete() }
        shoes.update { list -> list.filterNot { it.id == id } }
        runs.update { list -> list.map { if (it.shoeId == id) it.copy(shoeId = null) else it } }
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
