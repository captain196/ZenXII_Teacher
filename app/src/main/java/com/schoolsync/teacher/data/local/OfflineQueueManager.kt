package com.schoolsync.teacher.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.schoolsync.teacher.data.firebase.FirebaseService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class PendingWrite(
    val id: String = java.util.UUID.randomUUID().toString(),
    val path: String,
    val value: Map<String, Any?>,
    val type: String, // "set", "update"
    val timestamp: Long = System.currentTimeMillis()
)

@Singleton
class OfflineQueueManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firebaseService: FirebaseService,
    private val gson: Gson
) {
    private val prefs = context.getSharedPreferences("offline_queue", Context.MODE_PRIVATE)
    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: Flow<Int> = _pendingCount

    init { _pendingCount.value = getQueue().size }

    fun enqueue(write: PendingWrite) {
        val queue = getQueue().toMutableList()
        queue.add(write)
        saveQueue(queue)
        _pendingCount.value = queue.size
    }

    suspend fun processQueue(): Int {
        val queue = getQueue()
        if (queue.isEmpty()) return 0
        var processed = 0
        val remaining = mutableListOf<PendingWrite>()
        for (write in queue) {
            try {
                when (write.type) {
                    "set" -> firebaseService.setValue(write.path, write.value)
                    "update" -> firebaseService.updateChildren(write.path, write.value)
                }
                processed++
            } catch (_: Exception) {
                remaining.add(write)
            }
        }
        saveQueue(remaining)
        _pendingCount.value = remaining.size
        return processed
    }

    private fun getQueue(): List<PendingWrite> {
        val json = prefs.getString("queue", "[]") ?: "[]"
        return try {
            gson.fromJson(json, object : TypeToken<List<PendingWrite>>() {}.type)
        } catch (_: Exception) { emptyList() }
    }

    private fun saveQueue(queue: List<PendingWrite>) {
        prefs.edit().putString("queue", gson.toJson(queue)).apply()
    }
}
