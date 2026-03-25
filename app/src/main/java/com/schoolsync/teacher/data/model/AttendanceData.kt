package com.schoolsync.teacher.data.model

/**
 * Attendance status codes used in the attendance string.
 * Each character in the string represents one day of the month.
 * Index 0 = day 1, index 1 = day 2, etc.
 *
 * Path: Schools/{schoolCode}/{session}/{class}/{section}/Students/{studentId}/Attendance/{Month}
 * Value: String like "PPAPL..." where each char is one day's status.
 */
enum class AttendanceStatus(val code: Char, val label: String) {
    PRESENT('P', "Present"),
    ABSENT('A', "Absent"),
    LEAVE('L', "Leave"),
    HOLIDAY('H', "Holiday"),
    TARDY('T', "Late"),
    VACATION('V', "Vacation");

    companion object {
        private val codeMap = entries.associateBy { it.code }

        fun fromCode(code: Char): AttendanceStatus {
            return codeMap[code.uppercaseChar()] ?: PRESENT
        }

        /**
         * Cycle to next status: P → A → L → H → T → V → P
         */
        fun next(current: AttendanceStatus): AttendanceStatus {
            val values = entries
            val nextIndex = (values.indexOf(current) + 1) % values.size
            return values[nextIndex]
        }

        /**
         * All valid codes for validation.
         */
        val validCodes: Set<Char> = entries.map { it.code }.toSet()
    }
}

/**
 * Decoded attendance data for a student for a specific month.
 */
data class AttendanceData(
    val studentId: String,
    val month: String,
    val rawString: String,
    val dailyStatus: List<AttendanceStatus>
) {
    companion object {
        /**
         * Decode a raw attendance string into per-day statuses.
         * "PPAPL" → [PRESENT, PRESENT, ABSENT, PRESENT, LEAVE]
         */
        fun decode(studentId: String, month: String, raw: String?): AttendanceData {
            val safeRaw = raw ?: ""
            val statuses = safeRaw.map { AttendanceStatus.fromCode(it) }
            return AttendanceData(
                studentId = studentId,
                month = month,
                rawString = safeRaw,
                dailyStatus = statuses
            )
        }

        /**
         * Encode a list of statuses back into a string.
         */
        fun encode(statuses: List<AttendanceStatus>): String {
            return statuses.joinToString("") { it.code.toString() }
        }

        /**
         * Build the updated attendance string for a specific day.
         * - Reads current string
         * - Pads with 'P' if dayOfMonth exceeds current length
         * - Replaces the character at (dayOfMonth - 1)
         *
         * @param currentString The existing attendance string (may be null/empty)
         * @param dayOfMonth 1-based day of the month
         * @param status The status to set
         * @return The updated attendance string
         */
        fun updateDay(
            currentString: String?,
            dayOfMonth: Int,
            status: AttendanceStatus
        ): String {
            val index = dayOfMonth - 1
            val builder = StringBuilder(currentString ?: "")

            // Pad with 'V' (Vacation) for unrecorded days — matches admin panel behaviour
            while (builder.length <= index) {
                builder.append(AttendanceStatus.VACATION.code)
            }

            // Replace the character at the target index
            builder.setCharAt(index, status.code)
            return builder.toString()
        }
    }

    /**
     * Get the status for a specific day (1-based).
     * Returns null if that day hasn't been recorded yet.
     */
    fun statusForDay(dayOfMonth: Int): AttendanceStatus? {
        val index = dayOfMonth - 1
        return dailyStatus.getOrNull(index)
    }

    /**
     * Summary counts for display.
     */
    val presentCount: Int get() = dailyStatus.count { it == AttendanceStatus.PRESENT }
    val absentCount: Int get() = dailyStatus.count { it == AttendanceStatus.ABSENT }
    val leaveCount: Int get() = dailyStatus.count { it == AttendanceStatus.LEAVE }
    val totalDays: Int get() = dailyStatus.size
}

/**
 * Represents the attendance state for a single student on a single day,
 * used for the attendance marking UI.
 */
data class StudentAttendanceEntry(
    val studentId: String,
    val studentName: String,
    val rollNo: String,
    val currentStatus: AttendanceStatus,
    val currentMonthString: String
)
