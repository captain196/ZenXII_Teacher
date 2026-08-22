package com.schoolsync.teacher.data.maps

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.mapsDataStore: DataStore<Preferences> by preferencesDataStore(name = "maps_config_prefs")

/**
 * MapProviderManager — the Teacher app's single map-provider resolver.
 *
 * PLUMBING ONLY. The Teacher app renders no map today; this exists so that the
 * moment one is added — a driver navigation screen, a fleet view — it reads the
 * same central configuration as everything else rather than inventing a fourth
 * place to choose a provider.
 *
 * Resolution order matches the Parent app and the server exactly:
 *   1. Firestore `mapsConfig`   2. local cache   3. none
 *
 * There is no BuildConfig fallback here, and that is deliberate: the Teacher
 * app has never had a build-time map property, so inventing one now would
 * create precisely the hardcoded configuration source Phase B set out to
 * remove. Until an administrator selects a provider, this resolves to blank —
 * and a future map surface must render that state honestly rather than
 * defaulting to something nobody chose.
 */
@Singleton
class MapProviderManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager,
) {
    private val gson = Gson()
    private val store = context.mapsDataStore

    /** Live configuration, cache-first so a cold start does not wait on the network. */
    fun config(): Flow<MapProviderConfig?> = flow {
        emit(readCache())
        val schoolId = tokenManager.schoolCode.firstOrNull().orEmpty()
        if (schoolId.isBlank()) return@flow

        emitAll(
            firestoreService
                .observeDocumentAs<MapProviderConfig>(
                    MapProviderConfig.COLLECTION,
                    MapProviderConfig.docId(schoolId)
                )
                .map { remote ->
                    if (remote != null) writeCache(remote)
                    remote ?: readCache()
                }
                .catch { emit(readCache()) }
        )
    }

    /**
     * Active provider for a surface, defaulting to this app's platform key.
     *
     * `distinctUntilChanged` means a collector wakes only when the provider it
     * cares about actually changes — an unrelated configuration edit never
     * triggers a renderer rebuild.
     */
    fun providerFor(platform: String = MapProviderConfig.PLATFORM_TEACHER): Flow<String> =
        config().map { cfg ->
            val chosen = cfg?.resolveProvider(platform).orEmpty()
            if (chosen.isNotBlank() && cfg?.isEnabled(chosen) == true) chosen else ""
        }.distinctUntilChanged()

    suspend fun currentProvider(platform: String = MapProviderConfig.PLATFORM_TEACHER): String =
        providerFor(platform).firstOrNull().orEmpty()

    /** Public credentials for a provider. Never returns a server secret. */
    suspend fun credentialsFor(provider: String): Map<String, String> =
        config().firstOrNull()?.publicCredentials(provider) ?: emptyMap()

    // ── cache ────────────────────────────────────────────────────────────

    private suspend fun readCache(): MapProviderConfig? = try {
        store.data.firstOrNull()?.get(KEY_CONFIG)
            ?.let { gson.fromJson(it, MapProviderConfig::class.java) }
    } catch (_: Exception) {
        null   // an unparseable cache must never brick a future map surface
    }

    private suspend fun writeCache(cfg: MapProviderConfig) {
        try { store.edit { it[KEY_CONFIG] = gson.toJson(cfg) } } catch (_: Exception) { }
    }

    companion object {
        const val PROVIDER_MAPLIBRE = "maplibre"
        const val PROVIDER_MAPTILER = "maptiler"
        const val PROVIDER_GOOGLE   = "google"
        const val PROVIDER_MAPBOX   = "mapbox"
        const val PROVIDER_HERE     = "here"
        const val PROVIDER_TOMTOM   = "tomtom"

        private val KEY_CONFIG = stringPreferencesKey("maps_config_json")
    }
}
