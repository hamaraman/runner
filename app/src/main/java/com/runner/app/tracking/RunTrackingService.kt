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
import com.runner.core.AutoPauseDetector
import com.runner.core.CoachSettings
import com.runner.core.Geo
import com.runner.core.IntRange2
import com.runner.core.KmSplitTracker
import com.runner.core.PaceAlertPolicy
import com.runner.core.TargetPace
import com.runner.core.VoiceText
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
    private lateinit var voice: VoiceCoach
    private var timerJob: Job? = null

    /** 일시정지 전까지 누적된 움직인 시간 */
    private var accumulatedMs = 0L
    private var segmentStartedAt = 0L
    private var segment = 0
    private var lastAccepted: TrackPoint? = null
    private var runId = ""

    // 코칭
    private var settings = CoachSettings()
    private var splitTracker = KmSplitTracker()
    private var paceAlert: PaceAlertPolicy? = null
    private val autoPause = AutoPauseDetector()

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach(::onLocation)
        }
    }

    private val container get() = (application as RunnerApp).container

    override fun onCreate() {
        super.onCreate()
        locationClient = LocationServices.getFusedLocationProviderClient(this)
        voice = VoiceCoach(this)
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
        runId = UUID.randomUUID().toString()
        segment = 0
        lastAccepted = null
        segmentStartedAt = SystemClock.elapsedRealtime()

        settings = container.coach.value
        splitTracker = KmSplitTracker()
        autoPause.reset()
        val target = chooseTarget()
        paceAlert = target?.takeIf { settings.paceAlert }?.let { PaceAlertPolicy(it) }

        TrackingState.mutable.value = TrackingSnapshot(
            status = TrackingStatus.RUNNING,
            startedAtMs = System.currentTimeMillis(),
            waitingForGps = true,
            targetPace = target,
        )
        say(VoiceText.start(target))
        requestUpdates()
        startTimer()
    }

    /** 오늘 훈련표의 운동에 맞는 목표 페이스, 없으면 수동 설정값. */
    private fun chooseTarget(): IntRange2? {
        val plan = container.plan.value
        val today = container.todayWorkout(plan)
        return TargetPace.forWorkout(today?.type, plan?.zones) ?: TargetPace.manual(settings.manualTargetPaceSec)
    }

    private fun pause() {
        val snap = TrackingState.state.value
        if (snap.status != TrackingStatus.RUNNING) return
        if (!snap.autoPaused) accumulatedMs += SystemClock.elapsedRealtime() - segmentStartedAt
        autoPause.reset()
        locationClient.removeLocationUpdates(callback)
        timerJob?.cancel()
        TrackingState.mutable.update {
            it.copy(status = TrackingStatus.PAUSED, autoPaused = false, elapsedSec = accumulatedMs / 1000)
        }
        say("일시정지.")
        updateNotification()
        saveDraft()
    }

    private fun resume() {
        if (TrackingState.state.value.status != TrackingStatus.PAUSED) return
        startNewSegment()
        TrackingState.mutable.update { it.copy(status = TrackingStatus.RUNNING) }
        say("다시 시작합니다.")
        requestUpdates()
        startTimer()
    }

    private fun startNewSegment() {
        segment++
        lastAccepted = null
        segmentStartedAt = SystemClock.elapsedRealtime()
    }

    private fun finish(save: Boolean) {
        val snap = TrackingState.state.value
        if (snap.status == TrackingStatus.RUNNING && !snap.autoPaused) {
            accumulatedMs += SystemClock.elapsedRealtime() - segmentStartedAt
        }
        locationClient.removeLocationUpdates(callback)
        timerJob?.cancel()
        container.draft.update { null }

        val durationSec = accumulatedMs / 1000
        // 너무 짧은 기록(1분 미만·50m 미만)은 저장하지 않음
        if (save && snap.status != TrackingStatus.IDLE && (durationSec >= 60 || snap.distanceM >= 50)) {
            container.saveRun(
                RunRecord(
                    id = runId,
                    startedAtMs = snap.startedAtMs,
                    durationSec = durationSec,
                    distanceM = snap.distanceM,
                    points = snap.points,
                ),
            )
            say(VoiceText.finish(snap.distanceM, durationSec, Pace.secPerKm(snap.distanceM, durationSec)))
        }
        TrackingState.mutable.value = TrackingSnapshot()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        // 종료 요약 음성이 끊기지 않도록 잠시 뒤에 서비스를 끝낸다(그 사이 새 러닝을 시작했으면 유지)
        scope.launch {
            delay(if (settings.voiceEnabled && save) 12_000L else 0L)
            if (TrackingState.state.value.status == TrackingStatus.IDLE) stopSelf()
        }
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
        if (settings.autoPause && location.accuracy <= 25f && handleAutoPause(location)) return

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
        announceSplits()
    }

    /** 자동 일시정지 처리. 멈춤 상태라 이 위치를 기록하지 말아야 하면 true. */
    private fun handleAutoPause(location: Location): Boolean {
        val nowElapsed = location.elapsedRealtimeNanos / 1_000_000
        val change = autoPause.feed(location.latitude, location.longitude, nowElapsed)
        when (change?.event) {
            AutoPauseDetector.Event.PAUSED -> {
                // 멈춘 것으로 판단되는 시점까지만 운동 시간으로 친다
                accumulatedMs += (change.atMs - segmentStartedAt).coerceAtLeast(0)
                TrackingState.mutable.update { it.copy(autoPaused = true, elapsedSec = accumulatedMs / 1000) }
                say("자동 일시정지.")
                updateNotification()
            }
            AutoPauseDetector.Event.RESUMED -> {
                startNewSegment()
                TrackingState.mutable.update { it.copy(autoPaused = false) }
                say("다시 출발합니다.")
                updateNotification()
            }
            null -> Unit
        }
        return autoPause.paused
    }

    private fun announceSplits() {
        if (!settings.kmAnnounce) return
        val s = TrackingState.state.value
        val elapsed = currentElapsedMs() / 1000
        splitTracker.update(s.distanceM, elapsed).forEach { split ->
            say(VoiceText.km(split, Pace.secPerKm(s.distanceM, elapsed)))
        }
    }

    private fun currentElapsedMs(): Long =
        if (TrackingState.state.value.autoPaused) accumulatedMs
        else accumulatedMs + SystemClock.elapsedRealtime() - segmentStartedAt

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            var tick = 0
            while (isActive) {
                val elapsedMs = currentElapsedMs()
                val snap = TrackingState.state.value
                val current = if (snap.autoPaused) null else Geo.recentPaceSec(snap.points)
                TrackingState.mutable.update { it.copy(elapsedSec = elapsedMs / 1000, currentPaceSec = current) }
                if (tick % 10 == 0 && !snap.autoPaused) checkPace(current, elapsedMs)
                if (tick % 5 == 0) updateNotification()
                if (tick % 10 == 9) saveDraft()
                tick++
                delay(1_000L)
            }
        }
    }

    /** 지금까지의 기록을 임시 저장한다. 강제 종료 시 최대 10초 분량만 잃는다. */
    private fun saveDraft() {
        val s = TrackingState.state.value
        val ms = if (s.status == TrackingStatus.RUNNING) currentElapsedMs() else accumulatedMs
        container.draft.update { RunRecord(runId, s.startedAtMs, ms / 1000, s.distanceM, s.points) }
    }

    private fun checkPace(current: Double?, elapsedMs: Long) {
        val policy = paceAlert ?: return
        val target = TrackingState.state.value.targetPace ?: return
        policy.check(current, elapsedMs)?.let { say(VoiceText.paceAlert(it, target)) }
    }

    private fun say(text: String) {
        if (settings.voiceEnabled) voice.speak(text)
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
        val title = when {
            s.status == TrackingStatus.PAUSED -> "일시정지됨"
            s.autoPaused -> "자동 일시정지"
            else -> "러닝 중"
        }
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
        voice.shutdown()
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
