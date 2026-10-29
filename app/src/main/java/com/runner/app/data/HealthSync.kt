package com.runner.app.data

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Length
import com.runner.core.RunRecord
import java.time.Instant

/**
 * 러닝 기록을 Health Connect로 보낸다 → Google Fit 등이 읽어 간다 (삼성 헬스는 다른 앱 운동 기록을 안 가져감).
 * 쓰기만 한다. ponytail: 다른 앱 기록 가져오기는 경로마다 사용자 동의가 따로 필요해 뺐다.
 */
object HealthSync {
    val permissions = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(DistanceRecord::class),
        HealthPermission.PERMISSION_WRITE_EXERCISE_ROUTE,
    )

    /** 이 폰에서 Health Connect를 쓸 수 있나 (Android 9 미만이거나 앱이 없으면 false). */
    fun available(context: Context) =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun granted(context: Context): Boolean =
        available(context) && HealthConnectClient.getOrCreate(context).permissionController
            .getGrantedPermissions().containsAll(permissions)

    /** 권한이 있으면 보내고 실패한 건수를 돌려준다. 같은 run.id는 덮어써서 여러 번 보내도 중복되지 않는다. */
    suspend fun write(context: Context, runs: List<RunRecord>): Int {
        if (runs.isEmpty() || !granted(context)) return 0
        var failed = 0
        val client = HealthConnectClient.getOrCreate(context)
        // 한 건씩 보내서 이상한 기록 하나 때문에 전부 실패하지 않게
        for (run in runs) {
            val start = Instant.ofEpochMilli(run.startedAtMs)
            // 일시정지 시간까지 포함한 실제 끝 시각. 경로 점은 세션 안에 있어야 한다.
            val end = Instant.ofEpochMilli(maxOf(run.startedAtMs + run.durationSec * 1000, run.points.maxOfOrNull { it.timeMs } ?: 0) + 1)
            val route = run.points.filter { it.timeMs >= run.startedAtMs }.map {
                ExerciseRoute.Location(Instant.ofEpochMilli(it.timeMs), it.lat, it.lng, horizontalAccuracy = Length.meters(it.accuracyM.toDouble()), altitude = it.altM?.let(Length::meters))
            }
            val device = Device(type = Device.TYPE_PHONE)
            val records = listOf<Record>(
                ExerciseSessionRecord(
                    startTime = start, startZoneOffset = null, endTime = end, endZoneOffset = null,
                    metadata = Metadata.activelyRecorded(device, clientRecordId = "run-${run.id}", clientRecordVersion = 1),
                    exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
                    title = "러너",
                    notes = run.memo.ifBlank { null },
                    exerciseRoute = route.takeIf { it.isNotEmpty() }?.let(::ExerciseRoute),
                ),
                DistanceRecord(
                    startTime = start, startZoneOffset = null, endTime = end, endZoneOffset = null,
                    distance = Length.meters(run.distanceM),
                    metadata = Metadata.activelyRecorded(device, clientRecordId = "dist-${run.id}", clientRecordVersion = 1),
                ),
            )
            runCatching { client.insertRecords(records) }.onFailure {
                Log.w("HealthSync", "insert failed for run ${run.id}", it)
                failed++
            }
        }
        return failed
    }

    /** 앱에서 지운 기록은 Health Connect에서도 지운다. */
    suspend fun delete(context: Context, runId: String) {
        if (!granted(context)) return
        val client = HealthConnectClient.getOrCreate(context)
        runCatching {
            client.deleteRecords(ExerciseSessionRecord::class, emptyList(), listOf("run-$runId"))
            client.deleteRecords(DistanceRecord::class, emptyList(), listOf("dist-$runId"))
        }.onFailure { Log.w("HealthSync", "delete failed for run $runId", it) }
    }
}
