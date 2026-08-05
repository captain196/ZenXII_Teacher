package com.schoolsync.teacher.data.repository.firestore

import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.firestore.StaffCapabilitiesDoc
import com.schoolsync.teacher.util.Capabilities
import com.schoolsync.teacher.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide source of the current staff member's capabilities, read live from the
 * server-maintained `staffCapabilities/{uid}` doc (uid == staffId).
 *
 * Fail-open by design: when the doc is ABSENT (Cloud Function not yet deployed /
 * new staff before it fires) or on any read error, capabilities are UNKNOWN and
 * gating shows everything — so existing users never lose access before the RBAC
 * rollout populates the doc. Once populated, the UI gates by [Capabilities.modules].
 * This is UX only; the Firestore rules are the real per-module boundary.
 */
@Singleton
class CapabilityRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val capabilities: StateFlow<Capabilities> =
        tokenManager.userId
            .flatMapLatest { uid ->
                if (uid.isNullOrBlank()) {
                    flowOf(Capabilities.UNKNOWN)
                } else {
                    firestoreService
                        .observeDocumentAs<StaffCapabilitiesDoc>(Constants.Firestore.STAFF_CAPABILITIES, uid)
                        .map { doc ->
                            if (doc == null) Capabilities.UNKNOWN            // absent → fail-open
                            else Capabilities(
                                modules = doc.modules.toSet(),
                                levels = doc.levels,
                                loaded = true,
                            )
                        }
                        .catch { emit(Capabilities.UNKNOWN) }               // read error → fail-open
                }
            }
            .stateIn(scope, SharingStarted.Eagerly, Capabilities.UNKNOWN)
}
