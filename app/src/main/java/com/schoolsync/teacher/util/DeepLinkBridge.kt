package com.schoolsync.teacher.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One-shot deep-link channel for FCM-tapped notifications.
 *
 * FCMService can't touch Compose navigation directly. MainActivity reads
 * the tapped notification's intent extras and calls [publish] with the
 * target route. The nav graph observes [pending] and navigates once the
 * post-login scaffold is up, then calls [consume] so a later tab switch
 * doesn't re-route.
 *
 * Targets are plain `Route.route` strings — "events", "notices", etc.
 */
object DeepLinkBridge {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun publish(route: String) { _pending.value = route }
    fun consume() { _pending.value = null }
}
