package com.schoolsync.teacher.data.repository

import android.util.Log
import com.schoolsync.teacher.data.firebase.FirebaseService
import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified Data Access Layer (DAL) for the Teacher App.
 *
 * Consistent read/write pattern across ALL modules:
 *   READ:  Firestore (primary) → RTDB (fallback) → null
 *   WRITE: Firestore (primary) → RTDB (mirror)
 */
@Singleton
class DataRepository @Inject constructor(
    @PublishedApi internal val firestoreService: FirestoreService,
    @PublishedApi internal val firebaseService: FirebaseService,
    private val tokenManager: TokenManager
) {
    @PublishedApi internal companion object {
        const val TAG = "DataRepository"
    }

    suspend inline fun <reified T> get(
        collection: String,
        docId: String,
        rtdbPath: String? = null
    ): Result<T> {
        try {
            val doc = firestoreService.getDocumentAs<T>(collection, docId)
            if (doc != null) {
                Log.d(TAG, "GET $collection/$docId → Firestore")
                return Result.success(doc)
            }
        } catch (e: Exception) {
            Log.w(TAG, "GET $collection/$docId → FS fail: ${e.message}")
        }

        if (rtdbPath != null) {
            try {
                val snapshot = firebaseService.readSnapshot(rtdbPath)
                if (snapshot.exists()) {
                    val data = snapshot.getValue(T::class.java)
                    if (data != null) {
                        Log.d(TAG, "GET $collection/$docId → RTDB fallback")
                        return Result.success(data)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "GET $collection/$docId → RTDB fail: ${e.message}")
            }
        }

        return Result.failure(Exception("$collection/$docId not found"))
    }

    suspend inline fun <reified T> query(
        collection: String,
        noinline queryBuilder: (com.google.firebase.firestore.CollectionReference) -> com.google.firebase.firestore.Query = { it },
        rtdbPath: String? = null
    ): Result<List<T>> {
        try {
            val docs = firestoreService.queryDocumentsAs<T>(collection, queryBuilder)
            if (docs.isNotEmpty()) {
                Log.d(TAG, "QUERY $collection → Firestore (${docs.size})")
                return Result.success(docs)
            }
        } catch (e: Exception) {
            Log.w(TAG, "QUERY $collection → FS fail: ${e.message}")
        }

        if (rtdbPath != null) {
            try {
                val snapshot = firebaseService.readSnapshot(rtdbPath)
                val list = mutableListOf<T>()
                for (child in snapshot.children) {
                    child.getValue(T::class.java)?.let { list.add(it) }
                }
                if (list.isNotEmpty()) {
                    Log.d(TAG, "QUERY $collection → RTDB fallback (${list.size})")
                    return Result.success(list)
                }
            } catch (e: Exception) {
                Log.w(TAG, "QUERY $collection → RTDB fail: ${e.message}")
            }
        }

        return Result.success(emptyList())
    }

    suspend fun set(
        collection: String,
        docId: String,
        data: Any,
        merge: Boolean = true,
        rtdbPath: String? = null
    ): Result<Unit> {
        var ok = false
        try {
            firestoreService.setDocument(collection, docId, data, merge)
            ok = true
        } catch (e: Exception) {
            Log.e(TAG, "SET $collection/$docId → FS fail: ${e.message}")
        }

        if (rtdbPath != null && data is Map<*, *>) {
            try {
                @Suppress("UNCHECKED_CAST")
                firebaseService.setValue(rtdbPath, data)
                ok = true
            } catch (e: Exception) {
                Log.w(TAG, "SET → RTDB mirror fail: ${e.message}")
            }
        }

        return if (ok) Result.success(Unit) else Result.failure(Exception("Write failed"))
    }

    suspend fun remove(collection: String, docId: String, rtdbPath: String? = null): Result<Unit> {
        try { firestoreService.deleteDocument(collection, docId) } catch (_: Exception) {}
        if (rtdbPath != null) {
            try { firebaseService.setValue(rtdbPath, null) } catch (_: Exception) {}
        }
        return Result.success(Unit)
    }

    suspend fun schoolCode(): String? =
        tokenManager.schoolCode.firstOrNull()?.takeIf { it.isNotBlank() }

    suspend fun session(): String? =
        tokenManager.session.firstOrNull()?.takeIf { it.isNotBlank() }
}
