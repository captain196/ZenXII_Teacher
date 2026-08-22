package com.schoolsync.teacher.data.maps

import com.google.firebase.firestore.PropertyName

/**
 * `mapsConfig/{schoolId}_{schoolId}` — the centralized Maps & Location
 * configuration. See [docId] for why the school id appears twice.
 *
 * Mirrors the admin panel's `Maps_config_service` contract, and is deliberately
 * identical to the Parent app's copy so all three surfaces resolve a provider
 * by exactly the same rules. A divergence here would mean an administrator's
 * single setting produced different providers on different devices.
 *
 * Only the PUBLIC half of the configuration is here. Server keys live in a
 * separate `mapsSecrets` document that no client may read.
 *
 * ─── TEACHER SCOPE ───────────────────────────────────────────────────────
 * The Teacher app has no map surface today. This is PLUMBING ONLY: it can
 * resolve its provider and cache the answer, so when a driver-facing map or
 * navigation screen is built it inherits the central configuration instead of
 * introducing a fourth place where a provider gets chosen.
 */
data class MapProviderConfig(
    val schoolId: String = "",

    val mode: String = MODE_GLOBAL,

    @get:PropertyName("global_provider")
    @set:PropertyName("global_provider")
    var globalProvider: String = "",

    @get:PropertyName("platform_provider")
    @set:PropertyName("platform_provider")
    var platformProvider: Map<String, String> = emptyMap(),

    var providers: Map<String, Map<String, Any?>> = emptyMap(),

    @get:PropertyName("default_center")
    @set:PropertyName("default_center")
    var defaultCenter: Map<String, Double?> = emptyMap(),

    @get:PropertyName("default_zoom")
    @set:PropertyName("default_zoom")
    var defaultZoom: Double = 15.0,
) {
    /** Same resolution rules as the server and the Parent app. */
    fun resolveProvider(platform: String): String {
        if (mode == MODE_GLOBAL) return globalProvider
        val p = platformProvider[platform].orEmpty()
        return if (p.isNotBlank()) p else globalProvider
    }

    fun isEnabled(provider: String): Boolean =
        (providers[provider]?.get("enabled") as? Boolean) == true

    @Suppress("UNCHECKED_CAST")
    fun publicCredentials(provider: String): Map<String, String> {
        val raw = providers[provider]?.get("public") as? Map<String, Any?> ?: return emptyMap()
        return raw.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()
    }

    companion object {
        const val COLLECTION = "mapsConfig"
        const val MODE_GLOBAL = "global"
        const val MODE_PLATFORM = "platform"

        /** This app's platform key. Must match `Maps_config_service::PLATFORMS`. */
        const val PLATFORM_TEACHER = "teacher"

        /**
         * Reserved for the future Driver app. Registered server-side already,
         * so a driver build can resolve independently on day one.
         */
        const val PLATFORM_DRIVER = "driver"

        /**
         * P0 (2026-08-16) — the document id the admin panel actually writes.
         *
         * Every write goes through `Maps_config_service` →
         * `Firestore_service::setEntity('mapsConfig', $schoolId, …)`, and
         * `setEntity` prefixes the school id itself:
         *
         *     docId(entityId) = "{schoolId}_{entityId}"
         *
         * The entity id for this document IS the school id, so the canonical
         * path is `mapsConfig/{schoolId}_{schoolId}`.
         *
         * This used to return the bare school id — a document that has never
         * existed, so the listener always resolved to null. Kept byte-identical
         * to the Parent app's copy: a divergence here is exactly the drift this
         * shared contract exists to prevent.
         */
        fun docId(schoolId: String): String = "${schoolId}_$schoolId"
    }
}
