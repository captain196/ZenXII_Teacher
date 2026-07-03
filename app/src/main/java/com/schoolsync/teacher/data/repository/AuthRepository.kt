package com.schoolsync.teacher.data.repository

import android.util.Log
import com.schoolsync.teacher.data.firebase.FirebaseAuthManager
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
 * Firestore-only: reads profile + claims from Firebase Auth and Firestore.
 * No RTDB dependency (removed in Phase 1 Logical Change 4B).
 */
@Singleton
class AuthRepository @Inject constructor(
    private val tokenManager: TokenManager,
    private val firebaseAuthManager: FirebaseAuthManager,
    private val firestoreService: FirestoreService
) {
    companion object { private const val TAG = "AuthRepository" }

    /**
     * Login with userId and password using Firebase Auth.
     * On success:
     * 1. Signs in via Firebase Auth (synthetic email)
     * 2. Reads custom claims from ID token for role + school_id
     * 3. Reads teacher profile from Firestore (staff/{schoolId}_{userId})
     * 4. Saves profile to TokenManager
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

            // 3. Read teacher profile from Firestore: staff/{schoolId}_{userId}.
            //    Firestore is the only datastore — the legacy RTDB profile
            //    fallback was removed in Phase 1 Logical Change 4B.
            val staffData = readStaffProfile(schoolId, userId)

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
                return Result.failure(Exception("Your account has been deactivated. Please contact the school office."))
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
                // schoolCode = schoolId — the canonical school key used across the app.
                schoolCode = schoolId
            )

            // 5. Save profile + identifiers to TokenManager
            tokenManager.saveProfile(loginUser)
            tokenManager.saveSchoolCode(schoolId)        // = schoolId now
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
        userId: String
    ): Map<String, Any?> {
        // Firestore is the only datastore: staff/{schoolId}_{userId}
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
            Log.w(TAG, "readStaffProfile: Firestore lookup failed for staff/${schoolId}_$userId", e)
        }

        Log.w(TAG, "readStaffProfile: no Firestore profile found for $userId")
        return emptyMap()
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
     * Firestore `userDevices` is the sole canonical store. Doc id pattern
     * is `{userId}_{safeDeviceId}` to match the Push_service prune path
     * and the admin Device_management lookup.
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
