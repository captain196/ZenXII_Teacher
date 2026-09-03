package com.schoolsync.teacher.util

import android.content.Context
import com.schoolsync.teacher.R
import com.schoolsync.teacher.data.model.AttendanceStatus
import com.schoolsync.teacher.data.model.LeaveStatus

/**
 * Display labels for status enums.
 *
 * Extension functions in `util/`, deliberately NOT `@StringRes` properties on
 * the enums themselves: that would drag an `R` import into `data/model/`, make
 * those classes depend on the Android framework, and break the JVM unit tests
 * that run against them without a device.
 *
 * The enums' own constructor arguments stay exactly as they are —
 * `PRESENT('P', "Present")`, `APPROVED("approved", "Approved")`. `code` and
 * `value` are what get written to Firestore/RTDB and compared on read; `label`
 * remains the canonical English. Only rendering goes through here.
 *
 * This matters more in the staff app than in Parent: Teacher WRITES attendance,
 * so a translated code char would corrupt data rather than just look wrong.
 */

/** Attendance status in the app's language. */
fun AttendanceStatus.displayLabel(ctx: Context): String = when (this) {
    AttendanceStatus.PRESENT  -> ctx.getString(R.string.attendance_status_present)
    AttendanceStatus.ABSENT   -> ctx.getString(R.string.attendance_status_absent)
    AttendanceStatus.LEAVE    -> ctx.getString(R.string.attendance_status_leave)
    AttendanceStatus.HOLIDAY  -> ctx.getString(R.string.attendance_status_holiday)
    AttendanceStatus.TARDY    -> ctx.getString(R.string.attendance_status_late)
    AttendanceStatus.VACATION -> ctx.getString(R.string.attendance_status_vacation)
}

/** Leave-request status in the app's language. */
fun LeaveStatus.displayLabel(ctx: Context): String = when (this) {
    LeaveStatus.PENDING   -> ctx.getString(R.string.leave_status_pending)
    LeaveStatus.APPROVED  -> ctx.getString(R.string.leave_status_approved)
    LeaveStatus.REJECTED  -> ctx.getString(R.string.leave_status_rejected)
    LeaveStatus.CANCELLED -> ctx.getString(R.string.leave_status_cancelled)
}

// ── UI-layer mirror enums ───────────────────────────────────────────────────
// AttendanceScreen and LeaveScreen render their own UI enums rather than the
// data-model ones, so those need the same treatment. Their `code`/`value`
// constructor args (and `label`, kept as the canonical English) are untouched.

/** Attendance status in the app's language (UI mirror enum). */
fun com.schoolsync.teacher.ui.attendance.AttendanceStatus.displayLabel(ctx: Context): String =
    when (this) {
        com.schoolsync.teacher.ui.attendance.AttendanceStatus.PRESENT  -> ctx.getString(R.string.attendance_status_present)
        com.schoolsync.teacher.ui.attendance.AttendanceStatus.ABSENT   -> ctx.getString(R.string.attendance_status_absent)
        com.schoolsync.teacher.ui.attendance.AttendanceStatus.LEAVE    -> ctx.getString(R.string.attendance_status_leave)
        com.schoolsync.teacher.ui.attendance.AttendanceStatus.HOLIDAY  -> ctx.getString(R.string.attendance_status_holiday)
        com.schoolsync.teacher.ui.attendance.AttendanceStatus.TARDY    -> ctx.getString(R.string.attendance_status_late)
        com.schoolsync.teacher.ui.attendance.AttendanceStatus.VACATION -> ctx.getString(R.string.attendance_status_vacation)
    }

/** Attendance edit-window stage in the app's language. */
fun com.schoolsync.teacher.ui.attendance.Stage.displayLabel(ctx: Context): String =
    when (this) {
        com.schoolsync.teacher.ui.attendance.Stage.S1_FREE       -> ctx.getString(R.string.att_stage_free)
        com.schoolsync.teacher.ui.attendance.Stage.S2_RESTRICTED -> ctx.getString(R.string.att_stage_restricted)
        com.schoolsync.teacher.ui.attendance.Stage.S3_LOCKED     -> ctx.getString(R.string.att_window_locked)
        com.schoolsync.teacher.ui.attendance.Stage.UNKNOWN       -> ctx.getString(R.string.common_loading)
    }

/** Leave-request status in the app's language (UI mirror enum). */
fun com.schoolsync.teacher.ui.leave.LeaveStatus.displayLabel(ctx: Context): String =
    when (this) {
        com.schoolsync.teacher.ui.leave.LeaveStatus.PENDING   -> ctx.getString(R.string.leave_status_pending)
        com.schoolsync.teacher.ui.leave.LeaveStatus.APPROVED  -> ctx.getString(R.string.leave_status_approved)
        com.schoolsync.teacher.ui.leave.LeaveStatus.REJECTED  -> ctx.getString(R.string.leave_status_rejected)
        com.schoolsync.teacher.ui.leave.LeaveStatus.CANCELLED -> ctx.getString(R.string.leave_status_cancelled)
    }
