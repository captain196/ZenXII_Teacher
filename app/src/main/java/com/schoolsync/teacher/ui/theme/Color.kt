package com.schoolsync.teacher.ui.theme

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

// ---- DARK PALETTE ----
val TeacherDarkColors = AppColors(
    isDark = true,

    bgStart = Color(0xFF0E1822),
    bgMid = Color(0xFF162030),
    bgEnd = Color(0xFF1A2838),

    glass = Color(0x801E2A3A),
    glassBorder = Color(0x80324155),
    glassLight = Color(0x401E2A3A),

    slateBluePrimary = Color(0xFF3D4F5F),
    slateBlueLight = Color(0xFF4E6373),
    slateBlueDark = Color(0xFF2C3E4E),

    accent = Color(0xFF6FB3B2),
    accentSecondary = Color(0xFF3D8B8A),
    accentSurface = Color(0x1A6FB3B2),

    textPrimary = Color(0xFFE4ECF4),
    textSecondary = Color(0xFF8AA0B8),
    textTertiary = Color(0xFF5A7A96),
    textOnAccent = Color(0xFF0E1822),

    success = Color(0xFF4ADE80),
    successSurface = Color(0x1A4ADE80),
    warning = Color(0xFFF5C842),
    warningSurface = Color(0x14F5C842),
    error = Color(0xFFF87171),
    errorSurface = Color(0x14F87171),
    info = Color(0xFF3B82F6),
    infoSurface = Color(0x1A3B82F6),

    attPresent = Color(0xFF22C55E),
    attAbsent = Color(0xFFEF4444),
    attLeave = Color(0xFFF59E0B),
    attHoliday = Color(0xFF8B5CF6),
    attTardy = Color(0xFFFF6B35),
    attVacation = Color(0xFF06B6D4),

    surfaceDark = Color(0xFF121E2B),
    surfaceCard = Color(0xFF1A2838),
    surfaceElevated = Color(0xFF223344),
    divider = Color(0xFF2A3A4A),

    navRailBg = Color(0xFF0B1420),
    navSelected = Color(0xFF6FB3B2),
    navUnselected = Color(0xFF5A7A96),

    subjectMath = Color(0xFF3B82F6),
    subjectScience = Color(0xFF22C55E),
    subjectEnglish = Color(0xFFF59E0B),
    subjectHindi = Color(0xFFEF4444),
    subjectSocial = Color(0xFF8B5CF6),
    subjectComputer = Color(0xFF06B6D4),
    subjectPhysEd = Color(0xFFFF6B35),
    subjectArt = Color(0xFFEC4899),
    subjectDefault = Color(0xFF3D4F5F),

    statusBarColor = Color(0xFF0E1822),
    lightStatusBar = false,
)

// ---- LIGHT PALETTE ----
val TeacherLightColors = AppColors(
    isDark = false,

    bgStart = Color(0xFFC8D8E8),
    bgMid = Color(0xFFE8EEF4),
    bgEnd = Color(0xFFD4DEE8),

    glass = Color(0x73FFFFFF),           // rgba(255,255,255,0.45)
    glassBorder = Color(0xA6FFFFFF),     // rgba(255,255,255,0.65)
    glassLight = Color(0x40FFFFFF),

    slateBluePrimary = Color(0xFF3D4F5F),
    slateBlueLight = Color(0xFF4E6373),
    slateBlueDark = Color(0xFF2C3E4E),

    accent = Color(0xFF3D8B8A),
    accentSecondary = Color(0xFF6FB3B2),
    accentSurface = Color(0x1A3D8B8A),

    textPrimary = Color(0xFF1A2A3A),
    textSecondary = Color(0xFF5A6A7A),
    textTertiary = Color(0xFF8A9AAA),
    textOnAccent = Color(0xFFFFFFFF),

    success = Color(0xFF2D9D5A),
    successSurface = Color(0x1E2D9D5A),
    warning = Color(0xFFD4880A),
    warningSurface = Color(0x14D4880A),
    error = Color(0xFFCC3333),
    errorSurface = Color(0x14CC3333),
    info = Color(0xFF2563EB),
    infoSurface = Color(0x1A2563EB),

    attPresent = Color(0xFF2D9D5A),
    attAbsent = Color(0xFFCC3333),
    attLeave = Color(0xFFD4880A),
    attHoliday = Color(0xFF6B52C4),
    attTardy = Color(0xFFD4725C),
    attVacation = Color(0xFF1D9E8F),

    surfaceDark = Color(0xFFF0EFED),
    surfaceCard = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFFFFFFF),
    divider = Color(0x0D000000),

    navRailBg = Color(0x59FFFFFF),
    navSelected = Color(0xFF3D8B8A),
    navUnselected = Color(0xFF8A9AAA),

    subjectMath = Color(0xFF2563EB),
    subjectScience = Color(0xFF2D9D5A),
    subjectEnglish = Color(0xFFD4880A),
    subjectHindi = Color(0xFFCC3333),
    subjectSocial = Color(0xFF6B52C4),
    subjectComputer = Color(0xFF1D9E8F),
    subjectPhysEd = Color(0xFFD4725C),
    subjectArt = Color(0xFFD4509E),
    subjectDefault = Color(0xFF3D4F5F),

    statusBarColor = Color(0xFFC8D8E8),
    lightStatusBar = true,
)

val LocalAppColors = staticCompositionLocalOf { TeacherDarkColors }

// ---- Backward-compatible top-level aliases ----
// These point to the DARK palette so existing screens keep compiling.
// New screens should use LocalAppColors.current instead.

// Background gradient
val BgStart = TeacherDarkColors.bgStart
val BgEnd = TeacherDarkColors.bgEnd

// Glass morphism
val Glass = TeacherDarkColors.glass
val GlassBorder = TeacherDarkColors.glassBorder
val GlassLight = TeacherDarkColors.glassLight

// Primary palette
val SlateBluePrimary = TeacherDarkColors.slateBluePrimary
val SlateBlueLight = TeacherDarkColors.slateBlueLight
val SlateBlueDark = TeacherDarkColors.slateBlueDark

// Accent -- Teal
val Teal = TeacherDarkColors.accent
val TealLight = TeacherDarkColors.accentSecondary
val TealDark = TeacherDarkColors.accentSecondary
val TealSurface = TeacherDarkColors.accentSurface

// Text
val TextPrimary = TeacherDarkColors.textPrimary
val TextSecondary = TeacherDarkColors.textSecondary
val TextTertiary = TeacherDarkColors.textTertiary
val TextOnAccent = TeacherDarkColors.textOnAccent

// Status
val SuccessGreen = TeacherDarkColors.success
val SuccessGreenSurface = TeacherDarkColors.successSurface
val WarningAmber = TeacherDarkColors.warning
val WarningAmberSurface = TeacherDarkColors.warningSurface
val ErrorRed = TeacherDarkColors.error
val ErrorRedSurface = TeacherDarkColors.errorSurface
val InfoBlue = TeacherDarkColors.info
val InfoBlueSurface = TeacherDarkColors.infoSurface

// Attendance status colors
val AttendancePresent = TeacherDarkColors.attPresent
val AttendanceAbsent = TeacherDarkColors.attAbsent
val AttendanceLeave = TeacherDarkColors.attLeave
val AttendanceHoliday = TeacherDarkColors.attHoliday
val AttendanceTardy = TeacherDarkColors.attTardy
val AttendanceVacation = TeacherDarkColors.attVacation

// Surface & Containers
val SurfaceDark = TeacherDarkColors.surfaceDark
val SurfaceCard = TeacherDarkColors.surfaceCard
val SurfaceElevated = TeacherDarkColors.surfaceElevated
val Divider = TeacherDarkColors.divider

// Navigation
val NavRailBg = TeacherDarkColors.navRailBg
val NavSelected = TeacherDarkColors.navSelected
val NavUnselected = TeacherDarkColors.navUnselected

// Subject colors for timetable
val SubjectMath = TeacherDarkColors.subjectMath
val SubjectScience = TeacherDarkColors.subjectScience
val SubjectEnglish = TeacherDarkColors.subjectEnglish
val SubjectHindi = TeacherDarkColors.subjectHindi
val SubjectSocial = TeacherDarkColors.subjectSocial
val SubjectComputer = TeacherDarkColors.subjectComputer
val SubjectPhysEd = TeacherDarkColors.subjectPhysEd
val SubjectArt = TeacherDarkColors.subjectArt
val SubjectDefault = TeacherDarkColors.subjectDefault
