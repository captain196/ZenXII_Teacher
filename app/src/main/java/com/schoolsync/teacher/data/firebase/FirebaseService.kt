package com.schoolsync.teacher.data.firebase

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Query
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Generic Firebase RTDB helper service.
 * Provides typed read, listen (Flow), write, update, push, and runTransaction operations.
 */
@Singleton
class FirebaseService @Inject constructor(
    private val database: FirebaseDatabase
) {
    /**
     * Get a DatabaseReference for a given path.
     */
    fun ref(path: String): DatabaseReference = database.getReference(path)

    /**
     * One-shot read of a value at the given path, deserialized to type T.
     * Returns null if the snapshot does not exist.
     */
    suspend inline fun <reified T> readValue(path: String): T? {
        return readSnapshot(path).getValue(T::class.java)
    }

    /**
     * One-shot read of a DataSnapshot at the given path.
     */
    suspend fun readSnapshot(path: String): DataSnapshot {
        return suspendCancellableCoroutine { cont ->
            val ref = database.getReference(path)
            ref.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (cont.isActive) cont.resume(snapshot)
                }

                override fun onCancelled(error: DatabaseError) {
                    if (cont.isActive) cont.resumeWithException(error.toException())
                }
            })
        }
    }

    /**
     * One-shot read of a Query result as a DataSnapshot.
     */
    suspend fun readQuery(query: Query): DataSnapshot {
        return suspendCancellableCoroutine { cont ->
            query.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (cont.isActive) cont.resume(snapshot)
                }

                override fun onCancelled(error: DatabaseError) {
                    if (cont.isActive) cont.resumeWithException(error.toException())
                }
            })
        }
    }

    /**
     * Real-time listener as a Flow. Emits DataSnapshot on every change.
     * The listener is automatically removed when the Flow collector is cancelled.
     */
    fun listen(path: String): Flow<DataSnapshot> = callbackFlow {
        val ref = database.getReference(path)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Real-time listener on a Query as a Flow.
     */
    fun listenQuery(query: Query): Flow<DataSnapshot> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        query.addValueEventListener(listener)
        awaitClose { query.removeEventListener(listener) }
    }

    /**
     * Write a value at the given path. Overwrites any existing data.
     */
    suspend fun setValue(path: String, value: Any?) {
        suspendCancellableCoroutine { cont ->
            database.getReference(path).setValue(value)
                .addOnSuccessListener { if (cont.isActive) cont.resume(Unit) }
                .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        }
    }

    /**
     * Update multiple children at the given path.
     */
    suspend fun updateChildren(path: String, updates: Map<String, Any?>) {
        suspendCancellableCoroutine { cont ->
            database.getReference(path).updateChildren(updates)
                .addOnSuccessListener { if (cont.isActive) cont.resume(Unit) }
                .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        }
    }

    /**
     * Push a new child at the given path and set its value.
     * Returns the generated key.
     */
    suspend fun pushValue(path: String, value: Any): String {
        val ref = database.getReference(path).push()
        val key = ref.key ?: throw IllegalStateException("Failed to generate push key")
        suspendCancellableCoroutine { cont ->
            ref.setValue(value)
                .addOnSuccessListener { if (cont.isActive) cont.resume(Unit) }
                .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        }
        return key
    }

    /**
     * Delete data at the given path.
     */
    suspend fun removeValue(path: String) {
        suspendCancellableCoroutine { cont ->
            database.getReference(path).removeValue()
                .addOnSuccessListener { if (cont.isActive) cont.resume(Unit) }
                .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        }
    }

    /**
     * Run a Firebase transaction on the given path.
     * The [transform] function receives the current value as a String? and returns the new value.
     *
     * CRITICAL: This is used for attendance writes where atomic read-modify-write is required.
     * The attendance string must be read, modified at a specific index, and written back atomically.
     *
     * @param path Firebase RTDB path
     * @param transform Function that takes current value (String?) and returns new value (String)
     * @return The committed value, or null if the transaction was aborted
     */
    suspend fun runStringTransaction(
        path: String,
        transform: (currentValue: String?) -> String
    ): String? {
        return suspendCancellableCoroutine { cont ->
            val ref = database.getReference(path)
            ref.runTransaction(object : Transaction.Handler {
                override fun doTransaction(mutableData: MutableData): Transaction.Result {
                    val currentValue = mutableData.getValue(String::class.java)
                    val newValue = transform(currentValue)
                    mutableData.value = newValue
                    return Transaction.success(mutableData)
                }

                override fun onComplete(
                    error: DatabaseError?,
                    committed: Boolean,
                    currentData: DataSnapshot?
                ) {
                    if (error != null) {
                        if (cont.isActive) cont.resumeWithException(error.toException())
                    } else if (committed) {
                        if (cont.isActive) cont.resume(currentData?.getValue(String::class.java))
                    } else {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            })
        }
    }

    /**
     * Run a generic Firebase transaction on the given path.
     * The [transform] function receives MutableData and should modify it in place.
     */
    suspend fun runTransaction(
        path: String,
        transform: (MutableData) -> Transaction.Result
    ): DataSnapshot? {
        return suspendCancellableCoroutine { cont ->
            val ref = database.getReference(path)
            ref.runTransaction(object : Transaction.Handler {
                override fun doTransaction(mutableData: MutableData): Transaction.Result {
                    return transform(mutableData)
                }

                override fun onComplete(
                    error: DatabaseError?,
                    committed: Boolean,
                    currentData: DataSnapshot?
                ) {
                    if (error != null) {
                        if (cont.isActive) cont.resumeWithException(error.toException())
                    } else {
                        if (cont.isActive) cont.resume(if (committed) currentData else null)
                    }
                }
            })
        }
    }
}
