package com.schoolsync.teacher.util

/**
 * Application-wide constants: Firebase RTDB paths, API endpoints, etc.
 */
object Constants {

    /** Firebase Realtime Database path segments. */
    object Firebase {
        // Root nodes
        const val SCHOOLS = "Schools"
        const val TEACHERS = "Users/Teachers"
        const val USERS_PARENTS = "Users/Parents"
        const val SCHOOL_CODES_INDEX = "Indexes/School_codes"

        // Under a school node
        const val STUDENTS = "Students"
        const val STUDENT_LIST = "Students/List"
        const val ATTENDANCE = "Attendance"
        const val TIMETABLE = "Time_table"
        const val EXAMS = "Exams"
        const val SUBJECT_ASSIGNMENTS = "Academic/Subject_Assignments"
        const val RESULTS_MARKS = "Results/Marks"
        const val NOTICES = "Communication/Notices"
        const val MESSAGES_INBOX = "Communication/Messages/Inbox"
        const val MESSAGES_CHAT = "Communication/Messages/Chat"
        const val SOCIAL_STORIES = "Social/Stories"
        const val SOCIAL_STORY_VIEWS = "Social/StoryViews"
        const val SOCIAL_HIGHLIGHTS = "Social/Highlights"

        // HR / Staff
        const val STAFF_LEAVE_RECORDS = "HR/Staff_Leave/Records"
        const val STAFF_LEAVE_BALANCE = "HR/Staff_Leave/Balance"

        // Fees
        const val CLASSES_FEES = "Accounts/Fees/Classes Fees"
        const val PENDING_FEES = "Accounts/Pending_fees"
        const val FEE_DEMANDS = "Fees/Demands"
        const val FEE_DEFAULTERS = "Fees/Defaulters"
        const val STUDENT_FEE_ITEMS = "Fees/Student_Fee_Items"

        // Homework
        const val HOMEWORK = "Homework"
        const val HOMEWORK_STATUS = "HomeworkStatus"

        // Red Flags
        const val STUDENT_FLAGS = "StudentFlags"

        // Gallery
        const val GALLERY_ALBUMS = "Gallery/Albums"
        const val GALLERY_MEDIA = "Gallery/Media"
    }

    /** Firestore collection names */
    object Firestore {
        const val SCHOOLS = "schools"
        const val STAFF = "staff"
        const val STUDENTS = "students"
        const val PARENTS = "parents"
        const val SECTIONS = "sections"
        const val USERS = "users"
        const val ATTENDANCE = "attendance"
        const val ATTENDANCE_SUMMARY = "attendanceSummary"
        const val HOMEWORK = "homework"
        const val SUBMISSIONS = "submissions"
        const val LEAVE_APPLICATIONS = "leaveApplications"
        const val ATTENDANCE_REGULARIZATIONS = "attendanceRegularizations"
        const val EXAMS = "exams"
        const val EXAM_SCHEDULE = "examSchedule"
        const val MARKS = "marks"
        const val MARKS_AUDIT = "marksAudit"          // append-only marks change trail
        const val EXAM_RESULT_META = "examResultMeta"  // result staleness / publication-dirty
        const val RESULTS = "results"
        const val TIMETABLES = "timetables"
        const val FEE_STRUCTURES = "feeStructures"
        const val FEE_DEMANDS = "feeDemands"
        const val FEE_DEFAULTERS = "feeDefaulters"
        const val FEE_RECEIPTS = "feeReceipts"
        const val PAYMENT_INTENTS = "paymentIntents"
        const val FEE_CARRY_FORWARD = "feeCarryForward"
        const val FEE_REMINDER_LOG = "feeReminderLog"
        const val SCHOLARSHIP_AWARDS = "scholarshipAwards"
        const val CIRCULARS = "circulars"
        const val NOTICES_FS = "notices"  // Admin Notice Board writes here (type=notice)
        const val CIRCULAR_READS = "circularReads"
        const val NOTIFICATIONS = "notifications"
        const val EVENTS = "events"
        const val GALLERY_ALBUMS = "galleryAlbums"
        const val GALLERY_MEDIA = "galleryMedia"
        const val STORIES = "stories"
        const val PTM_CONFIG = "ptmConfig"
        const val PTM_BOOKINGS = "ptmBookings"
        const val MESSAGE_TEMPLATES = "messageTemplates"
        const val SUBJECT_ASSIGNMENTS = "subjectAssignments"

        // ── Phase 7: Transport ─────────────────────────────────────────
        const val ROUTES = "routes"
        const val VEHICLES = "vehicles"
        const val STUDENT_ROUTES = "studentRoutes"
        const val TRIP_LOGS = "tripLogs"
        const val GEO_FENCES = "geoFences"
        const val SOS_ALERTS = "sosAlerts"

        // ── Phase 8: Campus Life ───────────────────────────────────────
        const val HOSTEL_ROOMS = "hostelRooms"
        const val HOSTEL_ALLOCATIONS = "hostelAllocations"
        const val MEAL_MENUS = "mealMenus"
        const val HOSTEL_COMPLAINTS = "hostelComplaints"
        const val LIBRARY_BOOKS = "libraryBooks"
        const val LIBRARY_ISSUES = "libraryIssues"
        const val LIBRARY_FINES = "libraryFines"
        const val INCIDENTS = "incidents"
        const val MERIT_POINTS = "meritPoints"
        const val BEHAVIOR_SUMMARY = "behaviorSummary"

        // ── Phase 9: HR ────────────────────────────────────────────────
        const val SALARY_SLIPS = "salarySlips"
        const val APPRAISALS = "appraisals"
        const val TRAININGS = "trainings"
        const val TRAINING_REGISTRATIONS = "trainingRegistrations"
        const val RECRUITMENTS = "hrJobs"  // canonical: admin Hr.php writes here, dual-emits camelCase mirror

        // ── Phase 10: Admissions ───────────────────────────────────────
        const val ADMISSION_CONFIG = "admissionConfig"
        const val ADMISSION_APPLICATIONS = "admissionApplications"
        const val ADMISSION_MERIT_LISTS = "admissionMeritLists"

        // ── Phase 11: Advanced ─────────────────────────────────────────
        const val ASSETS = "assets"
        const val INVENTORY = "inventory"
        const val PURCHASE_ORDERS = "purchaseOrders"
        const val PURCHASE_REQUESTS = "purchaseRequests"
        const val VENDORS = "vendors"
        const val SURVEYS = "surveys"
        const val SURVEY_RESPONSES = "surveyResponses"
        const val SCHOOL_EVENTS = "events"  // renamed to avoid duplicate with Phase 5 EVENTS
        const val LOST_FOUND = "lostFound"

        // ── Phase 12: Analytics ────────────────────────────────────────
        const val DASHBOARDS = "dashboards"
        const val AUDIT_LOGS = "auditLogs"
        const val RBAC_ROLES = "rbacRoles"

        // ── Phase B (RTDB elimination): Student Red Flags ──────────────
        const val STUDENT_FLAGS = "studentFlags"

        // ── Phase 6/7: Academic Planner ────────────────────────────────
        const val LESSON_PLANS = "lessonPlans"
        const val CURRICULUM = "curriculum"        // parent docs (topicIds[] + counters)
        const val ACADEMIC_AUDIT_LOG = "academicAuditLog"

        // ── Homework — teacher evaluations of non-submitters ───────────
        const val TEACHER_MARKS = "teacherMarks"
    }

    /** RTDB URL */
    const val FIREBASE_DATABASE_URL =
        "https://graders-1c047-default-rtdb.asia-southeast1.firebasedatabase.app"

    // ── RTDB key helpers ─────────────────────────────────────────────
    // RTDB stores classes as "Class 8th/Section A". These ensure the
    // prefix is always present regardless of what format the backend
    // returns (raw "8th"/"A" or already-prefixed "Class 8th"/"Section A").

    /**
     * Ensure className is in canonical form: "8" → "Class 8th",
     * "8th" → "Class 8th", "Class 8th" → "Class 8th",
     * "LKG" → "Class LKG" (non-numeric stays as-is, just gets the prefix).
     *
     * Empty input returns empty (callers expect that for class-wide assignments).
     */
    fun classKey(className: String): String {
        val trimmed = className.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.startsWith("Class ", ignoreCase = true)) return trimmed
        // Add ordinal suffix for pure numeric keys (the Config/Classes catalog
        // stores key="8" but label="Class 8th" — and the Teacher app needs the
        // label form to match Firestore docs written by the admin panel).
        val withOrdinal = appendOrdinalSuffixIfNeeded(trimmed)
        return "Class $withOrdinal"
    }

    /**
     * Ensure section is in canonical form: "A" → "Section A",
     * "Section A" → "Section A". Empty input returns empty (so class-wide
     * assignments don't end up with the literal string "Section ").
     */
    fun sectionKey(section: String): String {
        val trimmed = section.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.startsWith("Section ", ignoreCase = true)) return trimmed
        return "Section $trimmed"
    }

    /**
     * Collision-safe document-id token, byte-identical to the admin panel's
     * Firestore_service::idToken — substr(sha1(canonicalize(raw)), 0, 16).
     * canonicalize = trim → collapse internal whitespace → Unicode NFC.
     *
     * Used to build marks / template / result doc-ids that match exactly what
     * the admin writes, so the apps and the admin panel touch the SAME document
     * (and never produce an invalid id — a raw class/section like
     * "Class 8th/Section A" would otherwise put a '/' inside the id, which
     * Firestore rejects as an "invalid document reference").
     */
    fun idToken(raw: String): String {
        var s = raw.trim().replace(Regex("\\s+"), " ")
        s = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFC)
        val digest = java.security.MessageDigest.getInstance("SHA-1")
            .digest(s.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.substring(0, 16)
    }

    /**
     * "1" → "1st", "2" → "2nd", "3" → "3rd", "4" → "4th", … "21" → "21st".
     * Anything non-numeric (LKG, Nursery, 12th, etc.) is returned unchanged.
     */
    private fun appendOrdinalSuffixIfNeeded(s: String): String {
        val n = s.toIntOrNull() ?: return s
        val mod100 = n % 100
        if (mod100 in 11..13) return "${n}th"
        return when (n % 10) {
            1 -> "${n}st"
            2 -> "${n}nd"
            3 -> "${n}rd"
            else -> "${n}th"
        }
    }
}
