package com.schoolsync.teacher.data.repository

import android.content.Context
import android.util.Log
import com.schoolsync.teacher.data.firebase.FirebaseAuthManager
import com.schoolsync.teacher.data.firebase.FirebaseService
import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.LoginUser
import com.schoolsync.teacher.data.remote.AuthApi
import com.schoolsync.teacher.util.Constants
import com.schoolsync.teacher.util.LocaleManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import com.schoolsync.teacher.R
import com.schoolsync.teacher.util.localizedString

/**
 * Handles authentication via Firebase Auth directly (email/password).
 * No Node.js API dependency — reads profile and claims from Firebase.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val tokenManager: TokenManager,
    private val firebaseAuthManager: FirebaseAuthManager,
    private val firebaseService: FirebaseService,
    private val firestoreService: FirestoreService,
    private val authApi: AuthApi,
    @ApplicationContext private val appContext: Context,
) {
    companion object { private const val TAG = "AuthRepository" }

    /**
     * Login with userId and password using Firebase Auth.
     * On success:
     * 1. Signs in via Firebase Auth (synthetic email)
     * 2. Reads custom claims from ID token for role + school_id
     * 3. Resolves Firebase school code from Indexes/School_codes
     * 4. Reads teacher profile from RTDB
     * 5. Saves profile to TokenManager
     */
    suspend fun login(
        userId: String,
        password: String,
        deviceId: String
    ): Result<Unit> {
        return try {
            // 1. Firebase Auth sign-in
            firebaseAuthManager.signInWithEmailAndPassword(userId, password)
                ?: return Result.failure(Exception("Sign-in failed: no user returned"))

            // 2. Read custom claims from ID token
            val tokenResult = firebaseAuthManager.getIdTokenResult(forceRefresh = true)
            val claims = tokenResult.claims
            val role = claims["role"] as? String ?: "Teacher"
            val schoolId = claims["school_id"] as? String
                ?: claims["schoolId"] as? String
                ?: return Result.failure(Exception("No school_id in claims"))

            // Force-change-password gate — the CLAIM half. The staff-doc mirror is
            // OR-ed in below, once the profile has been read; the flag is cached
            // there rather than here so both signals are accounted for.
            val claimMustChange = when (val v = claims["must_change_password"]) {
                is Boolean -> v
                is String  -> v.equals("true", ignoreCase = true)
                else       -> false
            }

            // 3. Resolve parent_db_key for Users/Admin & Users/Parents paths.
            //    For new SCH_* schools this is the numeric login code (e.g. "10001").
            //    For legacy "Demo" school it's also the login code (e.g. "10004").
            //    Stored at Indexes/School_codes/{schoolId}.
            val parentDbKey = resolveParentDbKey(schoolId) ?: schoolId
            Log.d(TAG, "login: schoolId=$schoolId parentDbKey=$parentDbKey")

            // 4. Read teacher profile — Firestore-first per migration contract.
            //    Firestore: staff/{schoolId}_{userId}
            //    RTDB fallback: Users/Admin/{parentDbKey}/{userId}
            //    (The previous Schools/{schoolCode}/Teachers/{userId} path is
            //     never written by the admin panel — it was always missing.)
            val staffData = readStaffProfile(schoolId, parentDbKey, userId)

            // Fail closed when NO profile exists in Firestore OR RTDB. Previously
            // an empty map fell through to the `?: "Active"` default below, so a
            // staff member whose record had been deleted — but whose Firebase Auth
            // account survived — could still sign in, landing on a session with no
            // name, no role scoping and no assignments. Absence of a record is not
            // evidence of an active account.
            if (staffData.isEmpty()) {
                Log.w(TAG, "login: no staff profile for $userId in Firestore or RTDB, refusing login")
                firebaseAuthManager.signOut()
                return Result.failure(
                    Exception(appContext.localizedString(R.string.err_staff_record_missing))
                )
            }

            // Force-change gate = CLAIM **or** staff-doc mirror, matching what the
            // Parent app has always done and what SessionGuard enforces.
            //
            // Claim-only was a real hole: a wholesale claims re-mint can drop a
            // pending must_change_password while the mirror still says true (seen
            // in production on STA0078 and STA0094). Such a user used to sail past
            // the gate — and once SessionGuard began re-checking on every
            // foreground, they were logged straight back out, giving an endless
            // login → dashboard → logout loop. OR-ing the two makes the gate agree
            // with the guard, so the user lands on the force-change screen and
            // resolves the state instead of bouncing.
            //
            // Free: staffData was just read for the status check.
            val docMustChange = when (val v = staffData["mustChangePassword"]) {
                is Boolean -> v
                is String  -> v.equals("true", ignoreCase = true)
                else       -> false
            }
            val mustChange = claimMustChange || docMustChange
            tokenManager.saveMustChangePassword(mustChange)
            Log.d(TAG, "login: must_change_password=$mustChange (claim=$claimMustChange doc=$docMustChange)")

            // Belt-and-braces: explicit status gate. Firebase Auth's
            // `disabled=true` covers the normal deactivation path, but if
            // `_disable_firebase_user` failed admin-side (network blip, no
            // service-account creds, legacy non-Auth account) the staff is
            // Inactive in Firestore yet Auth still accepts the password.
            // Reject here so a single source of truth wins.
            val rawStatus = (staffData["status"] ?: staffData["Status"] ?: "Active") as? String ?: "Active"
            if (!rawStatus.equals("Active", ignoreCase = true)) {
                Log.w(TAG, "login: staff $userId status=$rawStatus, refusing login")
                firebaseAuthManager.signOut()
                return Result.failure(Exception(appContext.localizedString(R.string.err_account_deactivated)))
            }

            val loginUser = LoginUser(
                userId = userId,
                name = (staffData["Name"] ?: staffData["name"] ?: userId).toString(),
                email = (staffData["Email"] ?: staffData["email"]) as? String,
                phone = (staffData["Phone Number"] ?: staffData["phone"] ?: staffData["Phone"]) as? String,
                role = role,
                schoolId = schoolId,
                schoolDisplayName = (staffData["SchoolDisplayName"] ?: staffData["schoolDisplayName"]) as? String,
                profilePic = (staffData["ProfilePic"] ?: staffData["profilePic"] ?: staffData["Photo"]) as? String,
                position = (staffData["Position"] ?: staffData["position"]) as? String,
                department = (staffData["Department"] ?: staffData["department"]) as? String,
                classesAssigned = extractStringList(staffData, "ClassesAssigned", "classesAssigned"),
                subjects = extractStringList(staffData, "teaching_subjects", "Subjects", "subjects"),
                parentDbKey = parentDbKey,
                // schoolCode is the school root key (= schoolId for new schools,
                // = "Demo" for legacy). Used by every Schools/{schoolCode}/... path
                // in the rest of the app. NOT the numeric login code anymore.
                schoolCode = schoolId
            )

            // 5. Save profile + identifiers to TokenManager
            tokenManager.saveProfile(loginUser)
            tokenManager.saveSchoolCode(schoolId)        // = schoolId now
            tokenManager.saveParentDbKey(parentDbKey)    // for Users/Parents/Admin paths
            tokenManager.saveDeviceId(deviceId)

            // 6. Seed active academic session from Firestore schools/{schoolId}.currentSession.
            //    If absent/empty, SchoolFirestoreRepository.observeSchool() self-heals it
            //    on the first snapshot after MainActivity subscribes.
            val session = resolveActiveSession(schoolId)
            if (session != null) {
                tokenManager.saveSession(session)
            } else {
                Log.w(TAG, "login: currentSession absent/empty on schools/$schoolId — observeSchool will self-heal")
            }

            // 7. Register the current FCM token now that the user is
            // logged in. FCMService.onNewToken usually fires BEFORE
            // login (token generated at install time), so the token
            // registration silently bails. Pulling the current token
            // here on every successful login guarantees that the
            // canonical Firestore userDevices/{userId}_{safeDeviceId}
            // doc is populated.
            try {
                val token = com.google.firebase.messaging.FirebaseMessaging.getInstance()
                    .token
                    .await()
                if (token.isNotBlank()) {
                    val fcm = registerFcmToken(token, userId, deviceId)
                    if (fcm.isSuccess) {
                        Log.d(TAG, "FCM token registered for $userId on login")
                    } else {
                        Log.w(TAG, "FCM token registration failed on login: ${fcm.exceptionOrNull()?.message}")
                    }
                } else {
                    Log.w(TAG, "FCM token blank on login — skipping registration")
                }
            } catch (e: Exception) {
                Log.w(TAG, "FCM token fetch failed on login", e)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "login failed", e)
            Result.failure(e)
        }
    }

    /**
     * Read the teacher's staff profile honoring the Firestore-first contract.
     * Returns a flat Map<String, Any?> regardless of source so the caller can
     * use a single set of key lookups.
     */
    private suspend fun readStaffProfile(
        schoolId: String,
        parentDbKey: String,
        userId: String
    ): Map<String, Any?> {
        // Firestore first: staff/{schoolId}_{userId}
        try {
            val doc = firestoreService.getDocumentMap(
                Constants.Firestore.STAFF,
                "${schoolId}_$userId"
            )
            if (doc != null && doc.isNotEmpty()) {
                Log.d(TAG, "readStaffProfile: hit Firestore staff/${schoolId}_$userId")
                return doc
            }
        } catch (e: Exception) {
            Log.w(TAG, "readStaffProfile: Firestore lookup failed, falling back to RTDB", e)
        }

        // RTDB fallback: Users/Admin/{parentDbKey}/{userId}
        try {
            val rtdbPath = "Users/Admin/$parentDbKey/$userId"
            val snap = firebaseService.readSnapshot(rtdbPath)
            val map = snap.value as? Map<*, *>
            if (map != null) {
                Log.d(TAG, "readStaffProfile: hit RTDB $rtdbPath")
                @Suppress("UNCHECKED_CAST")
                return map as Map<String, Any?>
            }
        } catch (e: Exception) {
            Log.w(TAG, "readStaffProfile: RTDB lookup failed", e)
        }

        Log.w(TAG, "readStaffProfile: no profile found for $userId in Firestore OR RTDB")
        return emptyMap()
    }

    /**
     * Resolve the parent_db_key (login code, e.g. "10001") for a school.
     * Stored at Indexes/School_codes/{schoolId}.
     */
    private suspend fun resolveParentDbKey(schoolId: String): String? {
        return try {
            val path = "${Constants.Firebase.SCHOOL_CODES_INDEX}/$schoolId"
            firebaseService.readValue<String>(path)
        } catch (e: Exception) {
            Log.w(TAG, "resolveParentDbKey failed for $schoolId", e)
            null
        }
    }

    /**
     * Logout: sign out Firebase, clear local storage.
     */
    suspend fun logout(): Result<Unit> {
        // Clear local state BEFORE signing out of Firebase. SessionGuard watches
        // observeAuthState() and ends the session when Firebase drops currentUser
        // while we still believe we are signed in — exactly the state this method
        // passes through if signOut() runs first, which would make an ordinary
        // user-initiated logout surface "Your session has ended" as if something
        // had gone wrong.
        return try {
            tokenManager.clearAll()
            firebaseAuthManager.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            // Even on error, ensure local cleanup
            tokenManager.clearAll()
            firebaseAuthManager.signOut()
            Result.failure(e)
        }
    }

    /**
     * Change password via Firebase Auth (client-side).
     * For voluntary changes from a Settings screen — Firebase requires a
     * "recent login" window, so this can fail after a few minutes idle.
     * For admin-driven resets, use [clearMustChange] instead.
     */
    suspend fun changePassword(newPassword: String): Result<Unit> {
        return try {
            firebaseAuthManager.changePassword(newPassword)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Finalise an admin-driven password reset. Calls the server endpoint
     * /auth/clear_must_change which updates Firebase Auth and clears the
     * must_change_password custom claim atomically. The client then
     * refreshes its ID token + clears the local mustChangePassword flag.
     */
    suspend fun clearMustChange(newPassword: String): Result<Unit> {
        return try {
            val token = firebaseAuthManager.getIdTokenResult(forceRefresh = false).token
                ?: return Result.failure(Exception("Not signed in"))

            val res = authApi.clearMustChange(
                bearer = "Bearer $token",
                newPassword = newPassword,
            )

            if (!res.isSuccessful) {
                val body = res.errorBody()?.string().orEmpty()
                Log.w(TAG, "clearMustChange HTTP ${res.code()}: $body")
                val msg = try {
                    org.json.JSONObject(body).optString("message").ifBlank { "Reset failed (HTTP ${res.code()})." }
                } catch (_: Exception) {
                    "Reset failed (HTTP ${res.code()})."
                }
                return Result.failure(Exception(msg))
            }

            val payload = res.body()
            if (payload?.status != "success") {
                return Result.failure(Exception(payload?.message ?: "Reset failed."))
            }

            // Re-authenticate with the password we just set.
            //
            // The server changed it through the Admin SDK, which invalidates the
            // refresh token this client holds — it was minted against the OLD
            // password. Without this the session survives only until the token is
            // next refreshed, and SessionGuard now refreshes on every foreground:
            // a teacher would finish setting their password, switch apps, come
            // back, and be dumped on the Login screen seconds later.
            //
            // We hold the new password right here, so mint a fresh session.
            // Best-effort — on failure the user simply logs in again, which is
            // the pre-existing behaviour, never worse.
            val reauthId = tokenManager.userId.firstOrNull()
            if (!reauthId.isNullOrBlank()) {
                try {
                    firebaseAuthManager.signInWithEmailAndPassword(reauthId, newPassword)
                    Log.d(TAG, "clearMustChange: re-authenticated after password change")
                } catch (e: Exception) {
                    Log.w(TAG, "clearMustChange: re-auth failed; user will be asked to log in again", e)
                }
            }

            // Force-refresh the ID token so subsequent calls see the cleared claim.
            try { firebaseAuthManager.getIdTokenResult(forceRefresh = true) } catch (_: Exception) {}

            // Clear local flag so the navigation gate releases.
            tokenManager.saveMustChangePassword(false)

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "clearMustChange exception", e)
            Result.failure(e)
        }
    }

    /**
     * Register the FCM token for this device.
     *
     * Firestore `userDevices` is the sole canonical store. Doc id pattern
     * is `{userId}_{safeDeviceId}` to match the Push_service prune path
     * and the admin Device_management lookup.
     */
    /**
     * Mirror the chosen language to Firestore so push can be composed in it and
     * a reinstall can restore it.
     *
     * Every write is individually try/caught and the whole thing is
     * fire-and-forget: the device-local preference set by [LocaleManager] is
     * what actually renders the UI, so a rejected or offline write must never
     * block or undo a language change. `staff.prefLang` is guarded by a narrow
     * rules clause allowing only ['prefLang','updatedAt'] on a self-owned
     * document; `userDevices` has no affectedKeys() constraint.
     */
    suspend fun mirrorPreferredLanguage(tag: String, deviceId: String? = null) {
        if (!LocaleManager.isSupported(tag)) return
        try {
            val userId = tokenManager.userId.firstOrNull()
            if (userId.isNullOrBlank()) return
            val schoolId = tokenManager.schoolId.firstOrNull() ?: ""
            val now = java.time.OffsetDateTime.now().toString()

            try {
                firestoreService.setDocument(
                    Constants.Firestore.STAFF,
                    "${schoolId}_${userId}",
                    mapOf("prefLang" to tag, "updatedAt" to now),
                    merge = true
                )
            } catch (e: Exception) {
                Log.w(TAG, "prefLang mirror failed (non-fatal)", e)
            }

            // Update this device's row too, so the next push is already correct
            // rather than waiting for the next token refresh.
            if (!deviceId.isNullOrBlank()) {
                val safeDeviceId = deviceId.replace(Regex("[^A-Za-z0-9_\\-]"), "_")
                try {
                    firestoreService.setDocument(
                        "userDevices",
                        "${userId}_${safeDeviceId}",
                        mapOf("lang" to tag, "lastActive" to now),
                        merge = true
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "userDevices.lang mirror failed (non-fatal)", e)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "language mirror failed (non-fatal)", e)
        }
    }

    /**
     * Reinstall / account-switch restore. The device-local preference is gone
     * but `staff.prefLang` is not, so adopt it — unless the user has already
     * made an explicit choice on this device, which always wins.
     *
     * Call after a successful login, before the first screen renders.
     */
    suspend fun restoreLanguageFromServer() {
        if (LocaleManager.hasExplicitChoice(appContext)) return
        try {
            val userId = tokenManager.userId.firstOrNull()
            if (userId.isNullOrBlank()) return
            val schoolId = tokenManager.schoolId.firstOrNull() ?: ""
            val doc = firestoreService.getDocument(
                Constants.Firestore.STAFF, "${schoolId}_${userId}")
            val serverTag = doc?.getString("prefLang")
            LocaleManager.adoptFromServerIfUnset(appContext, serverTag)
        } catch (e: Exception) {
            Log.w(TAG, "language restore failed (non-fatal)", e)
        }
    }

    suspend fun registerFcmToken(fcmToken: String, userId: String, deviceId: String): Result<Unit> {
        return try {
            val schoolId = tokenManager.schoolId.firstOrNull() ?: ""
            val now = java.time.OffsetDateTime.now().toString()
            val safeDeviceId = deviceId.replace(Regex("[^A-Za-z0-9_\\-]"), "_")
            val docId = "${userId}_${safeDeviceId}"

            val payload = mapOf(
                "schoolId"   to schoolId,
                "userId"     to userId,
                "deviceId"   to deviceId,
                "fcmToken"   to fcmToken,
                "platform"   to "android",
                "status"     to "active",
                "lastActive" to now,
                "appRole"    to "teacher",
                // Language for server-side push composition. It lives HERE, on the
                // device doc, rather than only on staff/{...}.prefLang because the
                // Cloud Function's token resolvers already read these snapshots in
                // full to get fcmToken — bucketing a fan-out by language therefore
                // costs zero extra reads. Sourcing it from `staff` would be an N+1.
                "lang"       to LocaleManager.effectiveTag(appContext)
            )

            try {
                firestoreService.setDocument("userDevices", docId, payload, merge = true)
                Log.d(TAG, "FCM token written to Firestore userDevices/$docId")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "FCM token Firestore write failed", e)
                Result.failure(Exception("FCM token write failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "registerFcmToken exception", e)
            Result.failure(e)
        }
    }

    /**
     * Seed the active academic session from Firestore schools/{schoolId}.currentSession.
     *
     * Firestore-only as of 2026-05-29: the legacy RTDB read of
     * Schools/{schoolId}/Config/ActiveSession was removed per the NO-RTDB policy.
     * Returns null when currentSession is absent/empty — in that case
     * SchoolFirestoreRepository.observeSchool() self-heals the session from the
     * first schools/{schoolCode} snapshot after MainActivity subscribes.
     */
    private suspend fun resolveActiveSession(schoolId: String): String? {
        return try {
            val doc = firestoreService.getDocumentMap(Constants.Firestore.SCHOOLS, schoolId)
            (doc?.get("currentSession") as? String)?.trim()?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.w(TAG, "resolveActiveSession: Firestore currentSession read failed for $schoolId", e)
            null
        }
    }

    /**
     * Extract a list of strings from a profile map, trying multiple key variants.
     */
    private fun extractStringList(map: Map<*, *>?, vararg keys: String): List<String>? {
        if (map == null) return null
        for (key in keys) {
            val value = map[key]
            if (value is List<*>) {
                return value.filterIsInstance<String>()
            }
            if (value is String && value.isNotBlank()) {
                return value.split(",").map { it.trim() }
            }
        }
        return null
    }
}
