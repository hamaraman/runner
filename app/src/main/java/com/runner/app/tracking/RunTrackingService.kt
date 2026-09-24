package com.runner.app.tracking

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.runner.app.R
import com.runner.app.RunnerApp
import com.runner.app.ui.MainActivity
import com.runner.core.Geo
import com.runner.core.Pace
import com.runner.core.RunRecord
import com.runner.core.TrackPoint
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 화면이 꺼져도 GPS 기록을 계속하는 포그라운드 서비스.
 * UI는 [TrackingState]를 구독하고, 이 서비스에 ACTION_* 인텐트로 명령한다.
 */
class RunTrackingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var locationClient: FusedLocationProviderClient
    private var timerJob: Job? = null

    /** 일시정지 전까지 누적된 움직인 시간 */
    private var accumulatedMs = 0L
    private var segmentStartedAt = 0L
    private var segment = 0
    private var lastAccepted: TrackPoint? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach(::onLocation)
        }
    }

    override fun onCreate() {
        super.onCreate()
        locationClient = LocationServices.getFusedLocationProviderClient(this)
        createChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start()
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_STOP -> finish(save = true)
            ACTION_DISCARD -> finish(save = false)
        }
        return START_NOT_STICKY
    }

    private fun start() {
        // startForegroundService 후 5초 안에 반드시 호출해야 한다
        goForeground()
        if (TrackingState.state.value.status != TrackingStatus.IDLE) return
        accumulatedMs = 0L
        segment = 0
        lastAccepted = null
        segmentStartedAt = SystemClock.elapsedRealtime()
        TrackingState.mutable.value = TrackingSnapshot(
            status = TrackingStatus.RUNNING,
            startedAtMs = System.currentTimeMillis(),
            waitingForGps = true,
        )
        requestUpdates()
        startTimer()
    }

    private fun pause() {
        if (TrackingState.state.value.status != TrackingStatus.RUNNING) return
        accumulatedMs += SystemClock.elapsedRealtime() - segmentStartedAt
        locationClient.removeLocationUpdates(callback)
        timerJob?.cancel()
        TrackingState.mutable.update { it.copy(status = TrackingStatus.PAUSED, elapsedSec = accumulatedMs / 1000) }
        updateNotification()
    }

    private fun resume() {
        if (TrackingState.state.value.status != TrackingStatus.PAUSED) return
        segment++
        lastAccepted = null
        segmentStartedAt = SystemClock.elapsedRealtime()
        TrackingState.mutable.update { it.copy(status = TrackingStatus.RUNNING) }
        requestUpdates()
        startTimer()
    }

    private fun finish(save: Boolean) {
        val snap = TrackingState.state.value
        if (snap.status == TrackingStatus.RUNNING) {
            accumulatedMs += SystemClock.elapsedRealtime() - segmentStartedAt
        }
        locationClient.removeLocationUpdates(callback)
        timerJob?.cancel()

        val durationSec = accumulatedMs / 1000
        // 너무 짧은 기록(1분 미만·50m 미만)은 저장하지 않음
        if (save && snap.status != TrackingStatus.IDLE && (durationSec >= 60 || snap.distanceM >= 50)) {
            (application as RunnerApp).container.saveRun(
                RunRecord(
                    id = UUID.randomUUID().toString(),
                    startedAtMs = snap.startedAtMs,
                    durationSec = durationSec,
                    distanceM = snap.distanceM,
                    points = snap.points,
                ),
            )
        }
        TrackingState.mutable.value = TrackingSnapshot()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    @SuppressLint("MissingPermission") // 권한은 UI에서 시작 전에 확인한다
    private fun requestUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000L)
            .setMinUpdateIntervalMillis(1_000L)
            .build()
        try {
            locationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission missing", e)
            finish(save = false)
        }
    }

    private fun onLocation(location: Location) {
        if (TrackingState.state.value.status != TrackingStatus.RUNNING) return
        val point = TrackPoint(
            lat = location.latitude,
            lng = location.longitude,
            timeMs = location.time,
            accuracyM = location.accuracy,
            segment = segment,
        )
        if (!Geo.isPlausible(lastAccepted, point)) return
        val added = lastAccepted?.let { Geo.distanceM(it, point) } ?: 0.0
        lastAccepted = point
        TrackingState.mutable.update {
            it.copy(points = it.points + point, distanceM = it.distanceM + added, waitingForGps = false)
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            var tick = 0
            while (isActive) {
                val elapsed = accumulatedMs + SystemClock.elapsedRealtime() - segmentStartedAt
                TrackingState.mutable.update { it.copy(elapsedSec = elapsed / 1000) }
                if (tick++ % 5 == 0) updateNotification()
                delay(1_000L)
            }
        }
    }

    private fun goForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification() = run {
        val s = TrackingState.state.value
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = if (s.status == TrackingStatus.PAUSED) "일시정지됨" else "러닝 중"
        val text = "%.2f km · %s · %s/km".format(
            s.distanceM / 1000, Pace.formatDuration(s.elapsedSec), Pace.format(Pace.secPerKm(s.distanceM, s.elapsedSec)),
        )
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_run)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "러닝 기록", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        locationClient.removeLocationUpdates(callback)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "RunTrackingService"
        private const val CHANNEL_ID = "run_tracking"
        private const val NOTIFICATION_ID = 1

        const val ACTION_START = "com.runner.app.START"
        const val ACTION_PAUSE = "com.runner.app.PAUSE"
        const val ACTION_RESUME = "com.runner.app.RESUME"
        const val ACTION_STOP = "com.runner.app.STOP"
        const val ACTION_DISCARD = "com.runner.app.DISCARD"

        fun send(context: Context, action: String) {
            val intent = Intent(context, RunTrackingService::class.java).setAction(action)
            if (action == ACTION_START) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
