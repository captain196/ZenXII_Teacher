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

// ---- DARK PALETTE — Corporate Navy + Gold ----
val TeacherDarkColors = AppColors(
    isDark = true,

    bgStart = Color(0xFF0A1428),
    bgMid = Color(0xFF0F1F3A),
    bgEnd = Color(0xFF13294B),

    glass = Color(0x801E3352),
    glassBorder = Color(0x80445A7A),
    glassLight = Color(0x401E3352),

    slateBluePrimary = Color(0xFF2A4266),
    slateBlueLight = Color(0xFF3D5B82),
    slateBlueDark = Color(0xFF152744),

    accent = Color(0xFFD4AF37),
    accentSecondary = Color(0xFFB8941F),
    accentSurface = Color(0x1AD4AF37),

    textPrimary = Color(0xFFF5EEDB),
    textSecondary = Color(0xFFA8B5C8),
    textTertiary = Color(0xFF5A6A80),
    textOnAccent = Color(0xFF0A1428),

    success = Color(0xFF3FBE6D),
    successSurface = Color(0x1A3FBE6D),
    warning = Color(0xFFF59E0B),
    warningSurface = Color(0x14F59E0B),
    error = Color(0xFFE74C3C),
    errorSurface = Color(0x14E74C3C),
    info = Color(0xFF3B82F6),
    infoSurface = Color(0x1A3B82F6),

    attPresent = Color(0xFF3FBE6D),
    attAbsent = Color(0xFFE74C3C),
    attLeave = Color(0xFFF59E0B),
    attHoliday = Color(0xFF9B7ED8),
    attTardy = Color(0xFFFF8C42),
    attVacation = Color(0xFF4FC3A1),

    surfaceDark = Color(0xFF0D1A30),
    surfaceCard = Color(0xFF16263F),
    surfaceElevated = Color(0xFF22365A),
    divider = Color(0xFF2A3A4A),

    navRailBg = Color(0xFF0A1222),
    navSelected = Color(0xFFD4AF37),
    navUnselected = Color(0xFF5A6A80),

    subjectMath = Color(0xFF4A90D9),
    subjectScience = Color(0xFF3FBE6D),
    subjectEnglish = Color(0xFFD4AF37),
    subjectHindi = Color(0xFFE74C3C),
    subjectSocial = Color(0xFF9B7ED8),
    subjectComputer = Color(0xFF4FC3A1),
    subjectPhysEd = Color(0xFFFF8C42),
    subjectArt = Color(0xFFEC4899),
    subjectDefault = Color(0xFF2A4266),

    statusBarColor = Color(0xFF0A1428),
    lightStatusBar = false,
)

// ---- LIGHT PALETTE — Corporate Navy + Gold ----
val TeacherLightColors = AppColors(
    isDark = false,

    bgStart = Color(0xFFF7F4ED),
    bgMid = Color(0xFFFBFAF5),
    bgEnd = Color(0xFFF0EAD8),

    glass = Color(0xCCFFFFFF),
    glassBorder = Color(0xA6E8DDB8),
    glassLight = Color(0x40FFFFFF),

    slateBluePrimary = Color(0xFF0F2949),
    slateBlueLight = Color(0xFF1E4372),
    slateBlueDark = Color(0xFF0A1C35),

    accent = Color(0xFF0F2949),
    accentSecondary = Color(0xFFB8941F),
    accentSurface = Color(0x1A0F2949),

    textPrimary = Color(0xFF0A1428),
    textSecondary = Color(0xFF4A5568),
    textTertiary = Color(0xFF8A95A5),
    textOnAccent = Color(0xFFFFFFFF),

    success = Color(0xFF1B8742),
    successSurface = Color(0x1E1B8742),
    warning = Color(0xFFB8740A),
    warningSurface = Color(0x14B8740A),
    error = Color(0xFFB82D25),
    errorSurface = Color(0x14B82D25),
    info = Color(0xFF1E4B9E),
    infoSurface = Color(0x1A1E4B9E),

    attPresent = Color(0xFF1B8742),
    attAbsent = Color(0xFFB82D25),
    attLeave = Color(0xFFB8740A),
    attHoliday = Color(0xFF5A3FA8),
    attTardy = Color(0xFFC25C3E),
    attVacation = Color(0xFF1A8570),

    surfaceDark = Color(0xFFEDE6D0),
    surfaceCard = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFFFFFFF),
    divider = Color(0x0D000000),

    navRailBg = Color(0x59FFFFFF),
    navSelected = Color(0xFF0F2949),
    navUnselected = Color(0xFF8A95A5),

    subjectMath = Color(0xFF1E4B9E),
    subjectScience = Color(0xFF1B8742),
    subjectEnglish = Color(0xFFB8740A),
    subjectHindi = Color(0xFFB82D25),
    subjectSocial = Color(0xFF5A3FA8),
    subjectComputer = Color(0xFF1A8570),
    subjectPhysEd = Color(0xFFC25C3E),
    subjectArt = Color(0xFFC2185B),
    subjectDefault = Color(0xFF0F2949),

    statusBarColor = Color(0xFFF7F4ED),
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
