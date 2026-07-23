package com.schoolsync.teacher.data.model.firestore

/**
 * Mirror of the server-maintained `staffCapabilities/{uid}` doc written by the
 * staffCapabilities Cloud Function (which mirrors the PHP resolve_role_access
 * union resolver). Keyed by the staff member's Firebase uid (== staffId).
 *
 * The APP reads its OWN doc to drive UI gating (which nav tiles/modules to show).
 * It is UX only — the Firestore rules enforce the real per-module boundary.
 * Absent doc (CF not yet deployed / new staff before the CF fires) → the app
 * treats capabilities as UNKNOWN and shows everything (fail-open), so nothing
 * breaks before the rollout populates it.
 *
 * All fields default so legacy/partial docs deserialise cleanly.
 */
data class StaffCapabilitiesDoc(
    val schoolId: String = "",
    val modules: List<String> = emptyList(),
    val levels: Map<String, String> = emptyMap(),
    val updatedAt: Any? = null,
)
