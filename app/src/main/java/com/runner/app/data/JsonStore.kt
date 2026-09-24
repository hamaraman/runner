package com.runner.app.data

import android.util.Log
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * 값 하나를 JSON 파일로 보관하는 초간단 저장소.
 * 메모리의 StateFlow가 진실의 원천이고, 변경될 때마다 백그라운드에서 파일에 기록한다.
 * (MVP용. 데이터가 커지면 Room 등으로 교체)
 */
class JsonStore<T>(
    private val file: File,
    private val serializer: KSerializer<T>,
    default: T,
    private val scope: CoroutineScope,
) {
    private val writeLock = Mutex()
    private val _state = MutableStateFlow(load() ?: default)
    val state: StateFlow<T> = _state.asStateFlow()

    val value: T get() = _state.value

    fun update(transform: (T) -> T) {
        _state.update(transform)
        scope.launch(Dispatchers.IO) {
            writeLock.withLock { write(_state.value) }
        }
    }

    private fun load(): T? = try {
        if (file.exists()) json.decodeFromString(serializer, file.readText()) else null
    } catch (e: Exception) {
        // 파일이 깨졌으면 백업해두고 기본값으로 시작
        Log.w(TAG, "Failed to read ${file.name}, starting fresh", e)
        file.copyTo(File(file.path + ".broken"), overwrite = true)
        null
    }

    private fun write(value: T) {
        try {
            val tmp = File(file.path + ".tmp")
            tmp.writeText(json.encodeToString(serializer, value))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write ${file.name}", e)
        }
    }

    private companion object {
        const val TAG = "JsonStore"
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
