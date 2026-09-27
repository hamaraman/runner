package com.runner.app.tracking

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * 한국어 TTS 음성 안내. 말하는 동안만 음악 볼륨을 줄였다가(덕킹) 끝나면 돌려놓는다.
 * 기기에 한국어 음성이 없으면 조용히 아무것도 하지 않는다.
 */
class VoiceCoach(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()

    private val pending = AtomicInteger(0)
    @Volatile private var ready = false
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status -> onInit(status) }

    private fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.w(TAG, "TTS init failed: $status")
            return
        }
        val result = tts.setLanguage(Locale.KOREAN)
        ready = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        if (!ready) Log.w(TAG, "Korean TTS voice not available")
        tts.setAudioAttributes(attributes)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = finishOne()

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finishOne()
        })
    }

    fun speak(text: String) {
        if (!ready) return
        if (pending.getAndIncrement() == 0) audioManager.requestAudioFocus(focusRequest)
        val result = tts.speak(text, TextToSpeech.QUEUE_ADD, null, "coach-${System.nanoTime()}")
        if (result != TextToSpeech.SUCCESS) finishOne()
    }

    private fun finishOne() {
        // 대기 중인 안내가 모두 끝나면 음악 볼륨 복구
        if (pending.decrementAndGet() <= 0) {
            pending.set(0)
            audioManager.abandonAudioFocusRequest(focusRequest)
        }
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    private companion object {
        const val TAG = "VoiceCoach"
    }
}
