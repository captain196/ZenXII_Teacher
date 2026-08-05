package com.schoolsync.teacher.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide unread/pending counts keyed by nav-rail route.
 *
 * ViewModels publish counts via [setCount] whenever their list data updates.
 * The nav rail reads [counts] and renders a badge per route.
 *
 * Pragmatic stand-in until the backend exposes aggregated counts — each writer
 * derives its number from the visible list it already loads. Mirrors the
 * Parent app's BadgeBus so both apps behave the same.
 */
@Singleton
class BadgeBus @Inject constructor() {
    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val counts: StateFlow<Map<String, Int>> = _counts.asStateFlow()

    fun setCount(route: String, count: Int) {
        _counts.update { current ->
            if (current[route] == count) current else current + (route to count)
        }
    }
}
