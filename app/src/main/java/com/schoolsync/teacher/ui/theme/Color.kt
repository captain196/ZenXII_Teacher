package com.schoolsync.teacher.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * SchoolSync Teacher color tokens -- Light & Dark.
 * All screens read from [LocalAppColors] so they auto-switch.
 */
data class AppColors(
    val isDark: Boolean,

    // Background gradient
    val bgStart: Color,
    val bgMid: Color,
    val bgEnd: Color,

    // Glass card
    val glass: Color,
    val glassBorder: Color,
    val glassLight: Color,

    // Primary palette (slate blue)
    val slateBluePrimary: Color,
    val slateBlueLight: Color,
    val slateBlueDark: Color,

    // Accent -- Teacher teal
    val accent: Color,
    val accentSecondary: Color,
    val accentSurface: Color,

    // Text
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textOnAccent: Color,

    // Status / Semantic
    val success: Color,
    val successSurface: Color,
    val warning: Color,
    val warningSurface: Color,
    val error: Color,
    val errorSurface: Color,
    val info: Color,
    val infoSurface: Color,

    // Attendance
    val attPresent: Color,
    val attAbsent: Color,
    val attLeave: Color,
    val attHoliday: Color,
    val attTardy: Color,
    val attVacation: Color,

    // Surface & Containers
    val surfaceDark: Color,
    val surfaceCard: Color,
    val surfaceElevated: Color,
    val divider: Color,

    // Navigation
    val navRailBg: Color,
    val navSelected: Color,
    val navUnselected: Color,

    // Subject colors for timetable
    val subjectMath: Color,
    val subjectScience: Color,
    val subjectEnglish: Color,
    val subjectHindi: Color,
    val subjectSocial: Color,
    val subjectComputer: Color,
    val subjectPhysEd: Color,
    val subjectArt: Color,
    val subjectDefault: Color,

    // Status bar
    val statusBarColor: Color,
    val lightStatusBar: Boolean,
)

// ---- DARK PALETTE — Soft Blue & Cloud ----
val TeacherDarkColors = AppColors(
    isDark = true,

    bgStart = Color(0xFF0E1620),
    bgMid = Color(0xFF121C28),
    bgEnd = Color(0xFF16222F),

    glass = Color(0x80182636),
    glassBorder = Color(0x803A4D63),
    glassLight = Color(0x40182636),

    slateBluePrimary = Color(0xFF2E4A6E),
    slateBlueLight = Color(0xFF4A6B96),
    slateBlueDark = Color(0xFF1C3048),

    accent = Color(0xFF7FA3D1),
    accentSecondary = Color(0xFF5B7DA8),
    accentSurface = Color(0x1A7FA3D1),

    textPrimary = Color(0xFFE8EDF3),
    textSecondary = Color(0xFFA6B0BE),
    textTertiary = Color(0xFF5E6878),
    textOnAccent = Color(0xFF0E1620),

    success = Color(0xFF3FBE6D),
    successSurface = Color(0x1A3FBE6D),
    warning = Color(0xFFF0A93C),
    warningSurface = Color(0x14F0A93C),
    error = Color(0xFFE5685C),
    errorSurface = Color(0x14E5685C),
    info = Color(0xFF5B8BD0),
    infoSurface = Color(0x1A5B8BD0),

    attPresent = Color(0xFF3FBE6D),
    attAbsent = Color(0xFFE5685C),
    attLeave = Color(0xFFF0A93C),
    attHoliday = Color(0xFF9B8FD8),
    attTardy = Color(0xFFE0925A),
    attVacation = Color(0xFF4FC3A1),

    surfaceDark = Color(0xFF0C1420),
    surfaceCard = Color(0xFF15212F),
    surfaceElevated = Color(0xFF1F2E40),
    divider = Color(0xFF2A3644),

    navRailBg = Color(0xFF0B131D),
    navSelected = Color(0xFF7FA3D1),
    navUnselected = Color(0xFF5E6878),

    subjectMath = Color(0xFF6FA0DC),
    subjectScience = Color(0xFF3FBE6D),
    subjectEnglish = Color(0xFFD2A85A),
    subjectHindi = Color(0xFFE5685C),
    subjectSocial = Color(0xFF9B8FD8),
    subjectComputer = Color(0xFF4FC3A1),
    subjectPhysEd = Color(0xFFE0925A),
    subjectArt = Color(0xFFEC4899),
    subjectDefault = Color(0xFF2E4A6E),

    statusBarColor = Color(0xFF0E1620),
    lightStatusBar = false,
)

// ---- LIGHT PALETTE — Soft Blue & Cloud ----
val TeacherLightColors = AppColors(
    isDark = false,

    bgStart = Color(0xFFF4F6F9),
    bgMid = Color(0xFFFAFBFC),
    bgEnd = Color(0xFFE9EEF5),

    glass = Color(0xCCFFFFFF),
    glassBorder = Color(0xA6D2DAE6),
    glassLight = Color(0x40FFFFFF),

    slateBluePrimary = Color(0xFF3E5F8A),
    slateBlueLight = Color(0xFF5B7DA8),
    slateBlueDark = Color(0xFF2E4A6E),

    accent = Color(0xFF3E5F8A),
    accentSecondary = Color(0xFF5B7DA8),
    accentSurface = Color(0x1A3E5F8A),

    textPrimary = Color(0xFF27303D),
    textSecondary = Color(0xFF6A7382),
    textTertiary = Color(0xFF9AA1AC),
    textOnAccent = Color(0xFFFFFFFF),

    success = Color(0xFF2F8F57),
    successSurface = Color(0x1E2F8F57),
    warning = Color(0xFFC08328),
    warningSurface = Color(0x14C08328),
    error = Color(0xFFBC5048),
    errorSurface = Color(0x14BC5048),
    info = Color(0xFF3E5F8A),
    infoSurface = Color(0x1A3E5F8A),

    attPresent = Color(0xFF2F8F57),
    attAbsent = Color(0xFFBC5048),
    attLeave = Color(0xFFC08328),
    attHoliday = Color(0xFF6E63B8),
    attTardy = Color(0xFFC06A3C),
    attVacation = Color(0xFF2F8B86),

    surfaceDark = Color(0xFFE9EEF5),
    surfaceCard = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFFFFFFF),
    divider = Color(0xFFE7EBF1),

    navRailBg = Color(0xCCFFFFFF),
    navSelected = Color(0xFF3E5F8A),
    navUnselected = Color(0xFF9AA1AC),

    subjectMath = Color(0xFF3E5F8A),
    subjectScience = Color(0xFF2F8F57),
    subjectEnglish = Color(0xFFB8893C),
    subjectHindi = Color(0xFFBC5048),
    subjectSocial = Color(0xFF6E63B8),
    subjectComputer = Color(0xFF2F8B86),
    subjectPhysEd = Color(0xFFC06A3C),
    subjectArt = Color(0xFFC2185B),
    subjectDefault = Color(0xFF3E5F8A),

    statusBarColor = Color(0xFFF4F6F9),
    lightStatusBar = true,
)

val LocalAppColors = staticCompositionLocalOf { TeacherDarkColors }

// ─── Backward-compatible theme-aware aliases ────────────────────────────────
// These used to be plain `val`s pinned to the DARK palette, which made every
// screen importing them break in light mode. They are now @Composable property
// getters that read the active palette via [LocalAppColors], so any existing
// screen that imports e.g. `Teal` automatically light/dark switches with no
// per-screen edits required. Callers must invoke them inside @Composable
// scope, which all `*Screen.kt` files already do.

// Background gradient
val BgStart: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.bgStart
val BgEnd: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.bgEnd

// Glass morphism
val Glass: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.glass
val GlassBorder: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.glassBorder
val GlassLight: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.glassLight

// Primary palette
val SlateBluePrimary: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.slateBluePrimary
val SlateBlueLight: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.slateBlueLight
val SlateBlueDark: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.slateBlueDark

// Accent -- Teal
val Teal: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.accent
val TealLight: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.accentSecondary
val TealDark: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.accentSecondary
val TealSurface: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.accentSurface

// Text
val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.textPrimary
val TextSecondary: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.textSecondary
val TextTertiary: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.textTertiary
val TextOnAccent: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.textOnAccent

// Status
val SuccessGreen: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.success
val SuccessGreenSurface: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.successSurface
val WarningAmber: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.warning
val WarningAmberSurface: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.warningSurface
val ErrorRed: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.error
val ErrorRedSurface: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.errorSurface
val InfoBlue: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.info
val InfoBlueSurface: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.infoSurface

// Attendance status colors
val AttendancePresent: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.attPresent
val AttendanceAbsent: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.attAbsent
val AttendanceLeave: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.attLeave
val AttendanceHoliday: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.attHoliday
val AttendanceTardy: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.attTardy
val AttendanceVacation: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.attVacation

// Surface & Containers
val SurfaceDark: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.surfaceDark
val SurfaceCard: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.surfaceCard
val SurfaceElevated: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.surfaceElevated
val Divider: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.divider

// Navigation
val NavRailBg: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.navRailBg
val NavSelected: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.navSelected
val NavUnselected: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.navUnselected

// Subject colors for timetable
val SubjectMath: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.subjectMath
val SubjectScience: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.subjectScience
val SubjectEnglish: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.subjectEnglish
val SubjectHindi: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.subjectHindi
val SubjectSocial: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.subjectSocial
val SubjectComputer: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.subjectComputer
val SubjectPhysEd: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.subjectPhysEd
val SubjectArt: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.subjectArt
val SubjectDefault: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.subjectDefault
