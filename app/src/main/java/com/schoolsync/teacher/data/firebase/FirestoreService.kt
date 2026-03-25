package com.schoolsync.teacher.data.firebase

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class FirestoreService @Inject constructor(
    @PublishedApi internal val firestore: FirebaseFirestore
) {

    // ── One-time reads ──────────────────────────────────────────────────

    suspend fun getDocument(collection: String, docId: String): DocumentSnapshot? =
        suspendCancellableCoroutine { cont ->
            firestore.collection(collection).document(docId).get()
                .addOnSuccessListener { snapshot ->
                    cont.resume(if (snapshot.exists()) snapshot else null)
                }
                .addOnFailureListener { e ->
                    cont.resumeWithException(e)
                }
        }

    suspend inline fun <reified T> getDocumentAs(collection: String, docId: String): T? =
        getDocument(collection, docId)?.toObject(T::class.java)

    suspend fun getDocumentMap(collection: String, docId: String): Map<String, Any?>? =
        getDocument(collection, docId)?.data

    suspend fun queryDocuments(
        collection: String,
        queryBuilder: (CollectionReference) -> Query
    ): QuerySnapshot = suspendCancellableCoroutine { cont ->
        val ref = firestore.collection(collection)
        queryBuilder(ref).get()
            .addOnSuccessListener { snapshot ->
                cont.resume(snapshot)
            }
            .addOnFailureListener { e ->
                cont.resumeWithException(e)
            }
    }

    suspend inline fun <reified T> queryDocumentsAs(
        collection: String,
        noinline queryBuilder: (CollectionReference) -> Query
    ): List<T> {
        val snapshot = queryDocuments(collection, queryBuilder)
        return snapshot.documents.mapNotNull { it.toObject(T::class.java) }
    }

    // ── Real-time listeners ─────────────────────────────────────────────

    fun observeDocument(collection: String, docId: String): Flow<DocumentSnapshot?> =
        callbackFlow {
            val registration = firestore.collection(collection).document(docId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        cancel("Firestore listener error", error)
                        return@addSnapshotListener
                    }
                    trySend(if (snapshot != null && snapshot.exists()) snapshot else null)
                }
            awaitClose { registration.remove() }
        }

    inline fun <reified T> observeDocumentAs(
        collection: String,
        docId: String
    ): Flow<T?> = callbackFlow {
        val registration = firestore.collection(collection).document(docId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    cancel("Firestore listener error", error)
                    return@addSnapshotListener
                }
                val obj = if (snapshot != null && snapshot.exists()) {
                    snapshot.toObject(T::class.java)
                } else null
                trySend(obj)
            }
        awaitClose { registration.remove() }
    }

    fun observeQuery(
        collection: String,
        queryBuilder: (CollectionReference) -> Query
    ): Flow<QuerySnapshot> = callbackFlow {
        val ref = firestore.collection(collection)
        val registration = queryBuilder(ref)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    cancel("Firestore listener error", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    trySend(snapshot)
                }
            }
        awaitClose { registration.remove() }
    }

    // ── Write operations ────────────────────────────────────────────────

    suspend fun setDocument(
        collection: String,
        docId: String,
        data: Any,
        merge: Boolean = false
    ): Unit = suspendCancellableCoroutine { cont ->
        val docRef = firestore.collection(collection).document(docId)
        val task = if (merge) {
            docRef.set(data, SetOptions.merge())
        } else {
            docRef.set(data)
        }
        task.addOnSuccessListener { cont.resume(Unit) }
            .addOnFailureListener { e -> cont.resumeWithException(e) }
    }

    suspend fun updateDocument(
        collection: String,
        docId: String,
        updates: Map<String, Any?>
    ): Unit = suspendCancellableCoroutine { cont ->
        firestore.collection(collection).document(docId)
            .update(updates)
            .addOnSuccessListener { cont.resume(Unit) }
            .addOnFailureListener { e -> cont.resumeWithException(e) }
    }

    suspend fun deleteDocument(
        collection: String,
        docId: String
    ): Unit = suspendCancellableCoroutine { cont ->
        firestore.collection(collection).document(docId)
            .delete()
            .addOnSuccessListener { cont.resume(Unit) }
            .addOnFailureListener { e -> cont.resumeWithException(e) }
    }

    // ── Utility ─────────────────────────────────────────────────────────

    fun docRef(collection: String, docId: String): DocumentReference =
        firestore.collection(collection).document(docId)

    fun serverTimestamp(): FieldValue = FieldValue.serverTimestamp()
}
