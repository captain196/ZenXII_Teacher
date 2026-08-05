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
    /**
     * A pending deep-link target. [arg] optionally carries a resource id from
     * the push payload (e.g. the tapped notice's id) so the destination screen
     * can auto-select it. Null [arg] ⇒ plain tab navigation.
     */
    data class Target(val route: String, val arg: String? = null)

    private val _pending = MutableStateFlow<Target?>(null)
    val pending: StateFlow<Target?> = _pending.asStateFlow()

    fun publish(route: String, arg: String? = null) { _pending.value = Target(route, arg) }
    fun consume() { _pending.value = null }
}
