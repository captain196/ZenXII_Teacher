package com.schoolsync.teacher.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
 * DataStore-based token and profile storage.
 * Stores auth tokens, Firebase token, and essential teacher profile fields.
 */
@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        // Auth tokens
        val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val KEY_FIREBASE_TOKEN = stringPreferencesKey("firebase_token")

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

        // Teacher-specific
        val KEY_POSITION = stringPreferencesKey("position")
        val KEY_DEPARTMENT = stringPreferencesKey("department")
        val KEY_CLASSES_ASSIGNED = stringPreferencesKey("classes_assigned")
        val KEY_SUBJECTS = stringPreferencesKey("subjects")

        // Session
        val KEY_SESSION = stringPreferencesKey("session")

        // Device
        val KEY_DEVICE_ID = stringPreferencesKey("device_id")

        // Theme
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")  // "system", "light", "dark"
    }

    val baseUrl: String = "https://project2-2-80nu.onrender.com/"

    private val dataStore = context.dataStore

    // --- Token Flows ---

    val accessToken: Flow<String?> = dataStore.data.map { it[KEY_ACCESS_TOKEN] }
    val refreshToken: Flow<String?> = dataStore.data.map { it[KEY_REFRESH_TOKEN] }
    val firebaseToken: Flow<String?> = dataStore.data.map { it[KEY_FIREBASE_TOKEN] }

    // --- Profile Flows ---

    val userId: Flow<String?> = dataStore.data.map { it[KEY_USER_ID] }
    val userName: Flow<String?> = dataStore.data.map { it[KEY_NAME] }
    val schoolId: Flow<String?> = dataStore.data.map { it[KEY_SCHOOL_ID] }
    val schoolCode: Flow<String?> = dataStore.data.map { it[KEY_SCHOOL_CODE] }
    val schoolDisplayName: Flow<String?> = dataStore.data.map { it[KEY_SCHOOL_DISPLAY_NAME] }
    val session: Flow<String?> = dataStore.data.map { it[KEY_SESSION] }
    val deviceId: Flow<String?> = dataStore.data.map { it[KEY_DEVICE_ID] }
    val profilePic: Flow<String?> = dataStore.data.map { it[KEY_PROFILE_PIC] }
    val position: Flow<String?> = dataStore.data.map { it[KEY_POSITION] }
    val department: Flow<String?> = dataStore.data.map { it[KEY_DEPARTMENT] }

    /**
     * Check if user is logged in (has an access token).
     */
    val isLoggedIn: Flow<Boolean> = dataStore.data.map { prefs ->
        !prefs[KEY_ACCESS_TOKEN].isNullOrBlank()
    }

    // --- Save Methods ---

    /**
     * Save auth tokens after login or refresh.
     */
    suspend fun saveTokens(
        accessToken: String,
        refreshToken: String,
        firebaseToken: String?
    ) {
        dataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = accessToken
            prefs[KEY_REFRESH_TOKEN] = refreshToken
            firebaseToken?.let { prefs[KEY_FIREBASE_TOKEN] = it }
        }
    }

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
     * Save the Firebase school code (resolved from Indexes/School_codes).
     */
    suspend fun saveSchoolCode(schoolCode: String) {
        dataStore.edit { prefs ->
            prefs[KEY_SCHOOL_CODE] = schoolCode
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

    /**
     * Clear all stored data (on logout).
     */
    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }
}
