package com.schoolsync.teacher.data.repository

import android.util.Log
import com.schoolsync.teacher.data.firebase.FirebaseAuthManager
import com.schoolsync.teacher.data.firebase.FirebaseService
import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.model.LoginUser
import com.schoolsync.teacher.util.Constants
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles authentication via Firebase Auth directly (email/password).
 * No Node.js API dependency — reads profile and claims from Firebase.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val tokenManager: TokenManager,
    private val firebaseAuthManager: FirebaseAuthManager,
    private val firebaseService: FirebaseService,
    private val firestoreService: FirestoreService
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

            // 6. Resolve active academic session — uses schoolId for the path
            val session = resolveActiveSession(schoolId)
            if (session != null) {
                tokenManager.saveSession(session)
            } else {
                Log.w(TAG, "login: ActiveSession not found at Schools/$schoolId/Config/ActiveSession")
            }

            // 7. Phase 7z — register the current FCM token now that the
            // user is logged in. FCMService.onNewToken usually fires
            // BEFORE login (token generated at install time), so the
            // token registration silently bails. Pulling the current
            // token here on every successful login guarantees that
            // Users/Devices/{userId}/{deviceId}/fcmToken is populated.
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
        return try {
            firebaseAuthManager.signOut()
            tokenManager.clearAll()
            Result.success(Unit)
        } catch (e: Exception) {
            // Even on error, ensure local cleanup
            firebaseAuthManager.signOut()
            tokenManager.clearAll()
            Result.failure(e)
        }
    }

    /**
     * Change password via Firebase Auth.
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
     * Register the FCM token for this device.
     *
     * Phase 8a (2026-04-09): canonical store is the Firestore
     * `userDevices` collection. RTDB Users/Devices/{userId}/{deviceId}
     * stays as a mirror until Phase 9.
     */
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
                "appRole"    to "teacher"
            )

            // ── Phase 8a: WRITE Firestore FIRST (canonical) ──
            var firestoreOk = false
            try {
                firestoreService.setDocument("userDevices", docId, payload, merge = true)
                firestoreOk = true
                Log.d(TAG, "FCM token written to Firestore userDevices/$docId")
            } catch (e: Exception) {
                Log.e(TAG, "FCM token Firestore write failed", e)
            }

            // ── RTDB mirror (best-effort, stays until Phase 9) ──
            try {
                firebaseService.setValue(
                    "Users/Devices/$userId/$deviceId",
                    mapOf(
                        "fcmToken"   to fcmToken,
                        "status"     to "active",
                        "platform"   to "android",
                        "lastActive" to now
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "FCM token RTDB mirror failed (non-fatal)", e)
            }

            if (firestoreOk) Result.success(Unit)
            else Result.failure(Exception("FCM token write failed in both Firestore and RTDB"))
        } catch (e: Exception) {
            Log.e(TAG, "registerFcmToken exception", e)
            Result.failure(e)
        }
    }

    /**
     * Resolve the active academic session from Schools/{schoolId}/Config/ActiveSession.
     * Param name kept as `schoolId` to make the path expectation explicit.
     */
    private suspend fun resolveActiveSession(schoolId: String): String? {
        return try {
            val path = "${Constants.Firebase.SCHOOLS}/$schoolId/Config/ActiveSession"
            firebaseService.readValue<String>(path)
        } catch (e: Exception) {
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
