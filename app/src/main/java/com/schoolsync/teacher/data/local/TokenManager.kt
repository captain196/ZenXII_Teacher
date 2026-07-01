package com.schoolsync.teacher.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.auth.FirebaseAuth
import com.schoolsync.teacher.data.model.LoginUser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "schoolsync_teacher_prefs"
)

/**
 * DataStore-based profile storage.
 * Stores essential teacher profile fields and school info.
 * Auth state is managed by Firebase Auth (not stored here).
 */
@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        // Teacher identity
        val KEY_USER_ID = stringPreferencesKey("user_id")
        val KEY_NAME = stringPreferencesKey("name")
        val KEY_EMAIL = stringPreferencesKey("email")
        val KEY_PHONE = stringPreferencesKey("phone")
        val KEY_ROLE = stringPreferencesKey("role")
        val KEY_PROFILE_PIC = stringPreferencesKey("profile_pic")

        // School
        val KEY_SCHOOL_ID = stringPreferencesKey("school_id")
        val KEY_SCHOOL_CODE = stringPreferencesKey("school_code")
        val KEY_SCHOOL_DISPLAY_NAME = stringPreferencesKey("school_display_name")
        // parent_db_key: numeric login code used for Users/Parents/{key} and
        // Users/Admin/{key} RTDB paths. Distinct from KEY_SCHOOL_CODE which now
        // stores the schoolId (= the path segment under Schools/...).
        val KEY_PARENT_DB_KEY = stringPreferencesKey("parent_db_key")

        // Teacher-specific
        val KEY_POSITION = stringPreferencesKey("position")
        val KEY_DESIGNATION = stringPreferencesKey("designation")
        val KEY_DEPARTMENT = stringPreferencesKey("department")
        val KEY_CLASSES_ASSIGNED = stringPreferencesKey("classes_assigned")
        val KEY_SUBJECTS = stringPreferencesKey("subjects")

        // Session
        val KEY_SESSION = stringPreferencesKey("session")

        // Device
        val KEY_DEVICE_ID = stringPreferencesKey("device_id")

        // Theme
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")  // "system", "light", "dark"

        // Force-change-password flag (mirrored from Firebase Auth custom claim
        // at login). Cleared by the force-change flow once the user picks
        // a new password.
        val KEY_MUST_CHANGE_PASSWORD = booleanPreferencesKey("must_change_password")
    }

    private val dataStore = context.dataStore

    // --- Profile Flows ---

    val userId: Flow<String?> = dataStore.data.map { it[KEY_USER_ID] }
    val userName: Flow<String?> = dataStore.data.map { it[KEY_NAME] }
    val schoolId: Flow<String?> = dataStore.data.map { it[KEY_SCHOOL_ID] }
    val schoolCode: Flow<String?> = dataStore.data.map { it[KEY_SCHOOL_CODE] }
    val schoolDisplayName: Flow<String?> = dataStore.data.map { it[KEY_SCHOOL_DISPLAY_NAME] }
    val parentDbKey: Flow<String?> = dataStore.data.map { it[KEY_PARENT_DB_KEY] }
    val session: Flow<String?> = dataStore.data.map { it[KEY_SESSION] }
    val deviceId: Flow<String?> = dataStore.data.map { it[KEY_DEVICE_ID] }
    val profilePic: Flow<String?> = dataStore.data.map { it[KEY_PROFILE_PIC] }
    val position: Flow<String?> = dataStore.data.map { it[KEY_POSITION] }
    val designation: Flow<String?> = dataStore.data.map { it[KEY_DESIGNATION] }
    val department: Flow<String?> = dataStore.data.map { it[KEY_DEPARTMENT] }

    /**
     * Check if user is logged in (Firebase Auth has a current user).
     */
    val isLoggedIn: Flow<Boolean> = dataStore.data.map {
        FirebaseAuth.getInstance().currentUser != null
    }

    // --- Save Methods ---

    /**
     * Save teacher profile from login response.
     */
    suspend fun saveProfile(user: LoginUser) {
        dataStore.edit { prefs ->
            prefs[KEY_USER_ID] = user.userId
            prefs[KEY_NAME] = user.name
            prefs[KEY_EMAIL] = user.email ?: ""
            prefs[KEY_PHONE] = user.phone ?: ""
            prefs[KEY_ROLE] = user.role
            prefs[KEY_PROFILE_PIC] = user.profilePic ?: ""
            prefs[KEY_SCHOOL_ID] = user.schoolId
            prefs[KEY_SCHOOL_DISPLAY_NAME] = user.schoolDisplayName ?: ""
            prefs[KEY_POSITION] = user.position ?: ""
            prefs[KEY_DEPARTMENT] = user.department ?: ""
            prefs[KEY_CLASSES_ASSIGNED] = user.classesAssigned?.joinToString(",") ?: ""
            prefs[KEY_SUBJECTS] = user.subjects?.joinToString(",") ?: ""
            // schoolCode may come from the login response or be resolved from Firebase
            user.schoolCode?.let { prefs[KEY_SCHOOL_CODE] = it }
        }
    }

    /**
     * Save the Firebase school code. NOTE: as of the 2026-04-08 fix, this
     * stores the schoolId (= the path segment under Schools/...) — NOT the
     * numeric login code. Use [saveParentDbKey] for the login code.
     */
    suspend fun saveSchoolCode(schoolCode: String) {
        dataStore.edit { prefs ->
            prefs[KEY_SCHOOL_CODE] = schoolCode
        }
    }

    /**
     * Save the parent_db_key (login code, e.g. "10001"). Used for
     * Users/Parents/{key} and Users/Admin/{key} RTDB paths.
     */
    suspend fun saveParentDbKey(parentDbKey: String) {
        dataStore.edit { prefs ->
            prefs[KEY_PARENT_DB_KEY] = parentDbKey
        }
    }

    /**
     * Save academic session.
     */
    suspend fun saveSession(session: String) {
        dataStore.edit { prefs ->
            prefs[KEY_SESSION] = session
        }
    }

    /**
     * Save device ID.
     */
    suspend fun saveDeviceId(deviceId: String) {
        dataStore.edit { prefs ->
            prefs[KEY_DEVICE_ID] = deviceId
        }
    }

    // --- Theme ---

    /** Theme mode: "system", "light", or "dark" */
    val themeMode: Flow<String> = dataStore.data.map { it[KEY_THEME_MODE] ?: "system" }

    suspend fun saveThemeMode(mode: String) {
        dataStore.edit { it[KEY_THEME_MODE] = mode }
    }

    // --- Force-change-password ---

    val mustChangePassword: Flow<Boolean> = dataStore.data.map {
        it[KEY_MUST_CHANGE_PASSWORD] ?: false
    }

    suspend fun saveMustChangePassword(value: Boolean) {
        dataStore.edit { it[KEY_MUST_CHANGE_PASSWORD] = value }
    }

    /**
     * Clear all stored data (on logout).
     */
    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }
}
