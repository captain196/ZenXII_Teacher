package com.schoolsync.teacher.util

/**
 * The current staff member's effective capabilities (mirrors the server
 * staffCapabilities doc). [loaded] = false means UNKNOWN — the app fails OPEN
 * (shows everything) so nothing breaks before the RBAC rollout populates caps.
 */
data class Capabilities(
    val modules: Set<String> = emptySet(),
    val levels: Map<String, String> = emptyMap(),
    val loaded: Boolean = false,
) {
    fun hasModule(module: String): Boolean = modules.contains(module)

    fun levelOf(module: String): String? = levels[module]

    companion object {
        /** Unknown/not-yet-loaded → gating treats everything as visible. */
        val UNKNOWN = Capabilities(loaded = false)
    }
}

/**
 * Maps app nav routes to the RBAC module that gates them, and decides visibility.
 *
 * Rules:
 *  - Self-service & shell routes (own payslip/leave/attendance, profile, search,
 *    dashboard) are NEVER gated — identity-bound, per the RBAC design.
 *  - When capabilities are UNKNOWN (not loaded), everything is shown (fail-open).
 *  - An unmapped route is shown (only explicitly-mapped modules gate).
 *  - Otherwise the route is visible iff its module is in the capability set.
 *
 * UI only — the Firestore rules enforce the real per-module boundary server-side.
 */
object ModuleGate {

    /** Routes always available: self-service + app shell. Never gated. */
    private val ALWAYS = setOf(
        "dashboard", "home", "profile", "search", "settings",
        "payslips", "leave", "my_attendance", "appraisals", "recruitment",
    )

    /** Nav route (base segment) → required RBAC module. */
    private val ROUTE_MODULE = mapOf(
        "attendance" to "Attendance",
        "marks" to "Examinations",
        "results" to "Results",
        "timetable" to "Academic",
        "lesson_plan" to "Academic",
        "students" to "SIS",
        "messages" to "Communication",
        "notices" to "Communication",
        "events" to "Events",
        "ptm" to "Events",
        "gallery" to "Events",
        "homework" to "Homework",
        "redflags" to "Red Flags",
        "stories" to "Stories",
        "fees" to "Fees",
        "library" to "Library",
    )

    /** Every RBAC module some route maps to — lets [canEdit]/[canManage] accept a raw module name too. */
    private val KNOWN_MODULES: Set<String> = ROUTE_MODULE.values.toSet()

    /** Base route segment, stripping any "/{arg}" or "?query". */
    private fun base(route: String): String =
        route.substringBefore("/").substringBefore("?").trim()

    /** The module a route requires, or null if it is never gated. */
    fun moduleFor(route: String): String? = ROUTE_MODULE[base(route)]

    /**
     * Resolve either a nav route (e.g. "marks") OR a raw RBAC module name
     * (e.g. "Examinations") to its module. Returns null when neither matches
     * (i.e. the target is never gated).
     */
    private fun resolveModule(routeOrModule: String): String? {
        ROUTE_MODULE[base(routeOrModule)]?.let { return it }
        return routeOrModule.takeIf { it in KNOWN_MODULES }
    }

    /** Whether the route should be visible/navigable given the caps. */
    fun canAccess(route: String, caps: Capabilities): Boolean {
        val b = base(route)
        if (b in ALWAYS) return true
        if (!caps.loaded) return true              // unknown → fail-open (UX)
        val module = ROUTE_MODULE[b] ?: return true // unmapped → show
        return caps.hasModule(module)
    }

    /**
     * Whether the current staff may WRITE (create / edit / delete / save) on the
     * given route or module. Level-aware: only `edit` and `manage` grantees can
     * write; a `view`-only grantee gets read access but no write affordances.
     *
     * Fail-open is preserved:
     *  - UNKNOWN caps (not loaded)  → true (legacy accounts unchanged).
     *  - Route/module never gated   → true.
     *  - Module held but NO level entry → true.
     * Fail-closed only in the explicit case: module held AND level == `view`.
     *
     * ⚠️ Known asymmetry with the server, for the "module held but NO level entry"
     * case ONLY: this returns true (allow), whereas firestore.rules
     * `hasCapabilityLevel()` reads `levels.get(module, 'view')` and therefore
     * DENIES. So a UI affordance shown here would be rejected by the rules.
     *
     * This state is unreachable in practice: functions/staffCapabilities.js writes a
     * `levels[m]` entry for EVERY module it grants — role-resolved grants default to
     * 'manage' when the role carries no permissionLevels, the legacy schools.roles
     * fallback also defaults to 'manage', and per-person overrides always carry a
     * level. So `modules` can never contain a module missing from `levels`.
     *
     * Do NOT "fix" this by loosening the rule — the rule failing closed is correct.
     * If staffCapabilities.js ever stops emitting a level per module, fix the WRITER.
     * Pinned by a regression test: firebase-rules/tests/student_flags.test.js
     * ("module held with NO level entry → denied").
     */
    fun canEdit(routeOrModule: String, caps: Capabilities): Boolean {
        if (!caps.loaded) return true                         // unknown → fail-open
        val module = resolveModule(routeOrModule) ?: return true  // ungated → allow
        if (!caps.hasModule(module)) return false             // module explicitly absent
        val level = caps.levelOf(module)?.lowercase() ?: return true // no level → full
        return level == "edit" || level == "manage"
    }

    /**
     * Whether the current staff holds the highest (`manage`) level on the route or
     * module — for destructive / governance affordances (publish, delete, approve).
     * Same fail-open contract as [canEdit]; fail-closed when level is `view`/`edit`.
     */
    fun canManage(routeOrModule: String, caps: Capabilities): Boolean {
        if (!caps.loaded) return true
        val module = resolveModule(routeOrModule) ?: return true
        if (!caps.hasModule(module)) return false
        val level = caps.levelOf(module)?.lowercase() ?: return true
        return level == "manage"
    }
}
