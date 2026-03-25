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
        const val EXAMS = "exams"
        const val EXAM_SCHEDULE = "examSchedule"
        const val MARKS = "marks"
        const val RESULTS = "results"
        const val TIMETABLES = "timetables"
        const val FEE_STRUCTURES = "feeStructures"
        const val FEE_DEMANDS = "feeDemands"
        const val FEE_DEFAULTERS = "feeDefaulters"
        const val FEE_RECEIPTS = "feeReceipts"
        const val PAYMENT_INTENTS = "paymentIntents"
        const val CIRCULARS = "circulars"
        const val CIRCULAR_READS = "circularReads"
        const val NOTIFICATIONS = "notifications"
        const val EVENTS = "events"
        const val GALLERY_ALBUMS = "galleryAlbums"
        const val GALLERY_MEDIA = "galleryMedia"
        const val STORIES = "stories"
        const val PTM_CONFIG = "ptmConfig"
        const val PTM_BOOKINGS = "ptmBookings"
        const val MESSAGE_TEMPLATES = "messageTemplates"

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
        const val RECRUITMENTS = "recruitments"

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
    }

    /** RTDB URL */
    const val FIREBASE_DATABASE_URL =
        "https://graders-1c047-default-rtdb.asia-southeast1.firebasedatabase.app"

    // ── RTDB key helpers ─────────────────────────────────────────────
    // RTDB stores classes as "Class 8th/Section A". These ensure the
    // prefix is always present regardless of what format the backend
    // returns (raw "8th"/"A" or already-prefixed "Class 8th"/"Section A").

    /** Ensure className has "Class " prefix: "8th" → "Class 8th" */
    fun classKey(className: String): String =
        if (className.startsWith("Class ", ignoreCase = true)) className else "Class $className"

    /** Ensure section has "Section " prefix: "A" → "Section A" */
    fun sectionKey(section: String): String =
        if (section.startsWith("Section ", ignoreCase = true)) section else "Section $section"
}
