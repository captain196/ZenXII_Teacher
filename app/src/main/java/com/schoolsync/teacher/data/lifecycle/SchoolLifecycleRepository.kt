package com.schoolsync.teacher.data.lifecycle

import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observes the school's lifecycle state from `tenantPublic/{schoolId}`.
 *
 * `tenantPublic` is the EXISTING thin, client-readable mirror the backend
 * already maintains — `B2_registry_service::write_lifecycle_state()` and
 * `set_admin_disabled()` fan the commercial signals into it, and
 * `Institution_lifecycle::transition()` fans in `institutionState`. This
 * repository therefore introduces NO new mirror, NO new collection, and no
 * RTDB; it consumes what is already published.
 *
 * It is also the one collection that stays readable when the rest of the
 * tenant is gated (`firestore.rules` keeps `tenantPublic` client-readable for
 * one's own school), which is precisely what lets the app explain a
 * suspension instead of dying on PERMISSION_DENIED everywhere else.
 */
@Singleton
class SchoolLifecycleRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager,
) {

    /**
     * Live school access state.
     *
     * Emits [SchoolAccess.Unknown] rather than throwing when the mirror cannot
     * be read (offline, absent doc, listener error). That is a deliberate
     * FAIL-OPEN: the server and firestore.rules remain the real enforcement,
     * so an unreadable convenience mirror must never lock a legitimate user
     * out of an app that would otherwise work.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeSchoolAccess(): Flow<SchoolAccess> =
        tokenManager.schoolId
            .flatMapLatest { schoolId ->
                if (schoolId.isNullOrBlank()) {
                    flowOf(SchoolAccess.Unknown)
                } else {
                    firestoreService.observeDocument(TENANT_PUBLIC, schoolId)
                        .map { snapshot -> snapshot?.let(::mapSnapshot) ?: SchoolAccess.Unknown }
                }
            }
            .catch { emit(SchoolAccess.Unknown) }

    /** One-shot read, for cold-start routing where a listener is overkill. */
    suspend fun currentSchoolAccess(): SchoolAccess {
        val schoolId = tokenManager.schoolId.first()
        if (schoolId.isNullOrBlank()) return SchoolAccess.Unknown
        return try {
            val doc = firestoreService.getDocument(TENANT_PUBLIC, schoolId)
            doc?.let(::mapSnapshot) ?: SchoolAccess.Unknown
        } catch (e: Exception) {
            SchoolAccess.Unknown
        }
    }

    private fun mapSnapshot(snapshot: com.google.firebase.firestore.DocumentSnapshot): SchoolAccess {
        val institution = snapshot.getString(FIELD_INSTITUTION_STATE)
        val commercial = snapshot.getString(FIELD_LIFECYCLE_STATE)
        val adminDisabled = snapshot.getBoolean(FIELD_ADMIN_DISABLED)
        val resolved = SchoolLifecycle.resolve(institution, commercial, adminDisabled)

        // ── TA-D2 ────────────────────────────────────────────────────────
        // A LOCK-OUT MUST REST ON CURRENT, SERVER-CONFIRMED STATE.
        //
        // Firestore's `.get()` falls back to the local cache whenever the
        // server is unreachable, and a listener re-emits cached data while
        // offline. That cached copy can be arbitrarily old. Treating it as
        // authoritative means a device that merely lost connectivity can tell
        // a teacher "your school's service is on hold" on the strength of a
        // value nothing has re-confirmed — a network failure presenting itself
        // as a commercial suspension, which is the one transition this state
        // machine must never make.
        //
        // Being unable to re-confirm is exactly what Unknown means, and
        // Unknown is fail-open (canUseApp = true), so the app keeps working
        // and firestore.rules remains the real gate. The moment a SERVER read
        // succeeds, a genuine suspension is honoured immediately.
        //
        // Deliberately narrow: only a RESTRICTIVE verdict is downgraded.
        // Cached data saying "everything is fine" is harmless to trust, so
        // Operational/Closing keep working offline exactly as before.
        if (snapshot.metadata.isFromCache && !resolved.canUseApp) {
            return SchoolAccess.Unknown
        }

        // Carry the effective date through when the school is closing, so the
        // notice can name a real date instead of a vague warning.
        return if (resolved is SchoolAccess.Closing) {
            SchoolAccess.Closing(snapshot.getString(FIELD_EFFECTIVE_DATE))
        } else {
            resolved
        }
    }

    private companion object {
        const val TENANT_PUBLIC = "tenantPublic"
        const val FIELD_INSTITUTION_STATE = "institutionState"
        const val FIELD_LIFECYCLE_STATE = "lifecycleState"
        const val FIELD_ADMIN_DISABLED = "adminDisabled"
        const val FIELD_EFFECTIVE_DATE = "institutionEffectiveDate"
    }
}
