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
import com.runner.core.Records
import com.runner.core.RunRecord
import com.runner.core.Shoe
import com.runner.core.TrainingPlan
import com.runner.core.Workout
import com.runner.core.WorkoutType
import com.runner.core.markRun
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.builtins.nullable

/** 앱 전역 의존성 모음(수동 DI). */
class AppContainer(context: Context) {
    private val scope = CoroutineScope(SupervisorJob())
    private val dir = File(context.filesDir, "data").apply { mkdirs() }
    private val photoDir = File(context.filesDir, "shoe_photos").apply { mkdirs() }
    private val resolver = context.contentResolver
    private val appContext = context.applicationContext

    val runs = JsonStore(File(dir, "runs.json"), ListSerializer(RunRecord.serializer()), emptyList(), scope)
    val plan = JsonStore(File(dir, "plan.json"), TrainingPlan.serializer().nullable, null, scope)
    val races = JsonStore(File(dir, "races.json"), ListSerializer(Race.serializer()), emptyList(), scope)
    val crews = JsonStore(File(dir, "crews.json"), ListSerializer(Crew.serializer()), emptyList(), scope)
    val events = JsonStore(File(dir, "events.json"), ListSerializer(CrewEvent.serializer()), emptyList(), scope)
    val coach = JsonStore(File(dir, "coach.json"), CoachSettings.serializer(), CoachSettings(), scope)
    val shoes = JsonStore(File(dir, "shoes.json"), ListSerializer(Shoe.serializer()), emptyList(), scope)
    /** 주간 목표 거리(km). 0이면 목표 없음. */
    val weeklyGoalKm = JsonStore(File(dir, "goal.json"), Double.serializer(), 0.0, scope)
    /** 진행 중인 러닝의 임시 저장본. 앱이 강제 종료돼도 다음 실행 때 살릴 수 있게 한다. */
    val draft = JsonStore(File(dir, "draft.json"), RunRecord.serializer().nullable, null, scope)

    /** 백업에 넣는 저장소. 진행 중 러닝(draft)은 뺀다. */
    private val backupStores = mapOf(
        "runs.json" to runs, "plan.json" to plan, "races.json" to races, "crews.json" to crews,
        "events.json" to events, "coach.json" to coach, "shoes.json" to shoes, "goal.json" to weeklyGoalKm,
    )

    /** 모든 기록과 신발 사진을 zip 하나로 쓴다. IO 스레드에서 부를 것. */
    fun exportBackup(out: OutputStream) = ZipOutputStream(out).use { zip ->
        backupStores.forEach { (name, store) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(store.toJson().toByteArray())
            zip.closeEntry()
        }
        photoDir.listFiles()?.forEach { f ->
            zip.putNextEntry(ZipEntry("shoe_photos/${f.name}"))
            f.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }

    /** 백업 zip으로 지금 데이터를 통째로 바꾼다. 파일이 하나라도 깨졌으면 아무것도 안 바꾸고 예외. IO 스레드에서 부를 것. */
    fun importBackup(input: InputStream) {
        val texts = mutableMapOf<String, String>()
        val photos = mutableMapOf<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            generateSequence { zip.nextEntry }.forEach { e ->
                val photo = e.name.removePrefix("shoe_photos/")
                when {
                    e.name in backupStores -> texts[e.name] = zip.readBytes().decodeToString()
                    // 경로 조작(../) 막기: 사진은 우리가 만든 이름 형식만 받는다
                    e.name.startsWith("shoe_photos/") && PHOTO_NAME.matches(photo) -> photos[photo] = zip.readBytes()
                }
            }
        }
        require("runs.json" in texts) { "러너 백업 파일이 아니에요" }
        val apply = texts.map { (name, text) -> backupStores.getValue(name).prepare(text) }
        photoDir.listFiles()?.forEach { it.delete() }
        photos.forEach { (name, bytes) -> File(photoDir, name).writeBytes(bytes) }
        apply.forEach { it() }
    }

    /** 러닝을 저장하고 훈련표에 반영한다(같은 날 없으면 같은 주 미완료 운동). 새로 깬 개인 기록 이름을 돌려준다. */
    fun saveRun(run: RunRecord): List<String> {
        val newBests = Records.newBests(run, runs.value)
        val shoeId = run.shoeId ?: shoes.value.firstOrNull { !it.retired }?.id
        runs.update { listOf(run.copy(shoeId = shoeId)) + it }
        scope.launch { HealthSync.write(appContext, listOf(run)) }
        val day = Instant.ofEpochMilli(run.startedAtMs).atZone(ZoneId.systemDefault()).toLocalDate()
        plan.update { it?.markRun(day.toEpochDay()) }
        return newBests
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

    fun deleteRun(id: String) {
        runs.update { list -> list.filterNot { it.id == id } }
        scope.launch { HealthSync.delete(appContext, id) }
    }

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

    private companion object {
        val PHOTO_NAME = Regex("""[A-Za-z0-9-]+\.jpg""")
    }
}
