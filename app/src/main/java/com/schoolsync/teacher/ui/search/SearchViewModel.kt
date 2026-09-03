package com.schoolsync.teacher.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.repository.TeacherRepository
import com.schoolsync.teacher.data.repository.firestore.CommunicationFirestoreRepository
import com.schoolsync.teacher.data.repository.firestore.EventsFirestoreRepository
import com.schoolsync.teacher.data.repository.firestore.HomeworkFirestoreRepository
import com.schoolsync.teacher.data.repository.firestore.StudentFirestoreRepository
import com.schoolsync.teacher.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.annotation.StringRes
import com.schoolsync.teacher.R
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.schoolsync.teacher.util.localizedString

/** Buckets a search hit falls into. `ordinal` drives the section order on screen. */
enum class SearchCategory(@StringRes val labelRes: Int, val emoji: String) {
    STUDENT(R.string.nav_students, "🎓"),
    CLASS(R.string.search_cat_my_classes, "🏫"),
    HOMEWORK(R.string.mod_homework, "📝"),
    NOTICE(R.string.nav_notices, "📢"),
    EVENT(R.string.nav_events, "🎉"),
    FEATURE(R.string.search_cat_goto, "🧭"),
}

/** One row in the search results — carries the route to navigate to on tap. */
data class SearchResult(
    val id: String,
    val category: SearchCategory,
    val title: String,
    val subtitle: String,
    val emoji: String,
    val route: String,
)

data class SearchUiState(
    val query: String = "",
    val isLoading: Boolean = true,
    val results: List<SearchResult> = emptyList(),        // query non-blank
    val browseFeatures: List<SearchResult> = emptyList(), // shown when query blank
)

/**
 * Global in-app search for the Teacher app. Everything is filtered CLIENT-SIDE
 * over data fetched once on entry (Firestore has no substring search), plus a
 * static catalogue of app features. Categories: Students, My Classes, Homework,
 * Notices, Events, and jump-to-any-feature.
 *
 * Mirrors the Parent app's search (same in-memory `Indexed`/haystack pattern,
 * same UI), scoped to what a teacher works with.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val teacherRepository: TeacherRepository,
    private val studentRepo: StudentFirestoreRepository,
    private val homeworkRepo: HomeworkFirestoreRepository,
    private val communicationRepo: CommunicationFirestoreRepository,
    private val eventRepo: EventsFirestoreRepository,
    // Resolves user-facing copy in the app's chosen language; the
    // application Context is locale-wrapped by LocaleManager.
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    /** A result plus its lowercased searchable text ("haystack"). */
    private data class Indexed(val result: SearchResult, val haystack: String)

    private val featureIndex: List<Indexed> = buildFeatureIndex()
    private var dynamicIndex: List<Indexed> = emptyList()

    private val _uiState = MutableStateFlow(
        SearchUiState(browseFeatures = featureIndex.map { it.result })
    )
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init { loadDynamic() }

    fun onQueryChange(q: String) {
        _uiState.update { it.copy(query = q) }
        recompute(q)
    }

    fun clearQuery() = onQueryChange("")

    private fun recompute(raw: String) {
        val query = raw.trim().lowercase()
        if (query.isEmpty()) {
            _uiState.update { it.copy(results = emptyList()) }
            return
        }
        val matched = (dynamicIndex + featureIndex)
            .filter { it.haystack.contains(query) }
            .sortedWith(
                // Section order first, then title-prefix matches ahead of
                // mid-word matches so the most relevant hit leads each group.
                compareBy(
                    { it.result.category.ordinal },
                    { !it.result.title.lowercase().startsWith(query) },
                    { it.result.title.lowercase() }
                )
            )
            .map { it.result }
        _uiState.update { it.copy(results = matched) }
    }

    private fun loadDynamic() {
        viewModelScope.launch {
            val items = mutableListOf<Indexed>()

            // Assigned classes drive students / classes / homework scope.
            val assignments = teacherRepository.getAssignedClasses().getOrNull().orEmpty()
            val sections = assignments
                .map { it.className to it.section }
                .filter { it.first.isNotBlank() && it.second.isNotBlank() }
                .distinct()

            // ── My Classes ── one row per (class, section, subject) assignment.
            assignments.forEach { a ->
                val classLabel = "${a.className.removePrefix("Class ")} " +
                    a.section.removePrefix("Section ")
                items += Indexed(
                    SearchResult(
                        id = "class_${a.className}_${a.section}_${a.subject}",
                        category = SearchCategory.CLASS,
                        title = appContext.localizedString(R.string.search_class_fmt, classLabel).trim(),
                        subtitle = listOfNotNull(
                            a.subject.ifBlank { null },
                            if (a.classTeacher) appContext.localizedString(R.string.search_class_teacher) else null
                        ).joinToString(" · "),
                        emoji = SearchCategory.CLASS.emoji,
                        route = Route.Timetable.route,
                    ),
                    haystack = "${a.className} ${a.section} ${a.subject}".lowercase()
                )
            }

            // ── Students ── roster of every assigned section (parallel fetch).
            val studentLists = coroutineScope {
                sections.map { (cls, sec) ->
                    async { studentRepo.getStudentsByClass(cls, sec).getOrNull().orEmpty() }
                }.awaitAll()
            }
            studentLists.flatten().forEach { s ->
                val sid = s.studentId.ifBlank { s.id }
                val cls = s.className.removePrefix("Class ")
                val sec = s.section.removePrefix("Section ")
                items += Indexed(
                    SearchResult(
                        id = "student_${sid.ifBlank { s.name }}",
                        category = SearchCategory.STUDENT,
                        title = s.name.ifBlank { appContext.localizedString(R.string.search_unnamed_student) },
                        subtitle = listOfNotNull(
                            "$cls $sec".trim().ifBlank { null },
                            s.rollNo.ifBlank { null }?.let { "Roll $it" }
                        ).joinToString(" · "),
                        emoji = SearchCategory.STUDENT.emoji,
                        route = Route.Students.route,
                    ),
                    haystack = "${s.name} ${s.rollNo} ${s.fatherName} $sid".lowercase()
                )
            }

            // ── Homework ── active homework across the teacher's sections.
            val homeworkLists = coroutineScope {
                sections.map { (cls, sec) ->
                    async {
                        val sectionKey = "$cls/$sec"
                        homeworkRepo.getHomework(sectionKey).getOrNull().orEmpty()
                    }
                }.awaitAll()
            }
            homeworkLists.flatten().distinctBy { it.id }.forEach { hw ->
                items += Indexed(
                    SearchResult(
                        id = "hw_${hw.id}",
                        category = SearchCategory.HOMEWORK,
                        title = hw.title.ifBlank { appContext.localizedString(R.string.search_untitled_generic) },
                        subtitle = listOfNotNull(
                            hw.subject.ifBlank { null },
                            "${hw.className.removePrefix("Class ")} " +
                                hw.section.removePrefix("Section ")
                        ).joinToString(" · "),
                        emoji = SearchCategory.HOMEWORK.emoji,
                        route = Route.Homework.route,
                    ),
                    haystack = "${hw.title} ${hw.subject} ${hw.className} ${hw.section}".lowercase()
                )
            }

            // ── Notices ──
            communicationRepo.getCirculars(limit = 50).getOrNull()?.forEach { n ->
                items += Indexed(
                    SearchResult(
                        id = "notice_${n.id}",
                        category = SearchCategory.NOTICE,
                        title = n.title.ifBlank { appContext.localizedString(R.string.search_untitled_notice) },
                        subtitle = n.author.ifBlank { n.category },
                        emoji = SearchCategory.NOTICE.emoji,
                        route = Route.Notices.route,
                    ),
                    haystack = "${n.title} ${n.body} ${n.description} ${n.author} ${n.category}".lowercase()
                )
            }

            // ── Events ──
            eventRepo.getEvents().getOrNull()?.forEach { e ->
                items += Indexed(
                    SearchResult(
                        id = "event_${e.id}",
                        category = SearchCategory.EVENT,
                        title = e.title.ifBlank { appContext.localizedString(R.string.search_untitled_event) },
                        subtitle = listOfNotNull(
                            e.startDate.ifBlank { null },
                            e.location.ifBlank { null }
                        ).joinToString(" · "),
                        emoji = SearchCategory.EVENT.emoji,
                        route = Route.Events.route,
                    ),
                    haystack = "${e.title} ${e.description} ${e.location} ${e.category}".lowercase()
                )
            }

            dynamicIndex = items.distinctBy { it.result.id }
            _uiState.update { it.copy(isLoading = false) }
            recompute(_uiState.value.query)
        }
    }

    /** Static catalogue of app features, each with synonyms for fuzzy recall. */
    private fun buildFeatureIndex(): List<Indexed> {
        fun feature(
            englishTitle: String,
            @StringRes titleRes: Int,
            emoji: String,
            route: String,
            vararg keywords: String
        ): Indexed {
            val localized = appContext.localizedString(titleRes)
            return Indexed(
                SearchResult(
                    id = "feat_$route",
                    category = SearchCategory.FEATURE,
                    title = localized,
                    subtitle = appContext.localizedString(R.string.search_open_fmt, localized),
                    emoji = emoji,
                    route = route,
                ),
                // Both titles go in the haystack: the English one keeps every
                // existing query working, the localized one lets a staff member
                // search in the language the app is actually showing them. The
                // `keywords` stay English synonyms - they are match-only and
                // never rendered.
                haystack = (listOf(englishTitle, localized) + keywords)
                    .joinToString(" ").lowercase(java.util.Locale.ROOT)
            )
        }
        // Every line below is `feature(englishTitle, titleRes, emoji, route, ...keywords)`.
        // The trailing keywords are MATCH-ONLY English synonyms — never rendered —
        // so they stay English on purpose. i18n-ignore
        // Each feature() takes (englishTitle, titleRes, ...). The FIRST arg is the
        // English haystack entry — deliberately English so existing queries keep
        // matching; the titleRes beside it is what renders. i18n-ignore
        return listOf(
            feature("Take Attendance", R.string.dash_take_attendance, "📅", Route.Attendance.route,  // i18n-ignore: English haystack entry
                "attendance", "present", "absent", "mark", "roll call"),
            feature("Marks", R.string.nav_marks, "📊", Route.Marks.route,  // i18n-ignore: English haystack entry
                "marks", "grades", "grade", "score", "exam", "result", "assessment"),
            feature("Homework", R.string.mod_homework, "📝", Route.Homework.route,  // i18n-ignore: English haystack entry
                "homework", "assignment", "assignments", "diary", "task", "work"),
            feature("Timetable", R.string.mod_timetable, "🗓️", Route.Timetable.route,  // i18n-ignore: English haystack entry
                "timetable", "schedule", "routine", "periods", "class schedule"),
            feature("Students", R.string.nav_students, "🎓", Route.Students.route,  // i18n-ignore: English haystack entry
                "students", "student", "roster", "class list", "children", "pupils"),
            feature("Chat", R.string.nav_chat, "💬", Route.Messages.route,  // i18n-ignore: English haystack entry
                "chat", "message", "messages", "parent", "conversation", "inbox"),
            feature("Notices", R.string.nav_notices, "📢", Route.Notices.route,  // i18n-ignore: English haystack entry
                "notices", "notice", "circular", "circulars", "announcement", "news"),
            feature("Leave", R.string.nav_leave, "🏖️", Route.Leave.route,  // i18n-ignore: English haystack entry
                "leave", "absence", "apply leave", "sick", "time off"),
            feature("Red Flags", R.string.dash_red_flags, "🚩", Route.RedFlags.route,  // i18n-ignore: English haystack entry
                "red flags", "flags", "discipline", "warning", "behaviour", "behavior"),
            feature("My Attendance", R.string.mod_my_attendance, "🕒", Route.MyAttendance.route,  // i18n-ignore: English haystack entry
                "my attendance", "clock in", "punch", "check in", "self attendance"),
            feature("Lesson Plan", R.string.mod_lesson_plan, "📖", Route.LessonPlan.route,  // i18n-ignore: English haystack entry
                "lesson plan", "lesson", "plan", "syllabus", "topics", "today's lessons"),
            feature("Gallery", R.string.nav_gallery, "🖼️", Route.Gallery.route,  // i18n-ignore: English haystack entry
                "gallery", "photos", "photo", "pictures", "album", "images"),
            feature("Events", R.string.nav_events, "🎉", Route.Events.route,  // i18n-ignore: English haystack entry
                "events", "event", "holiday", "celebration", "calendar"),
            feature("Library", R.string.nav_library, "📚", Route.Library.route,  // i18n-ignore: English haystack entry
                "library", "books", "book", "borrow", "catalogue"),
            feature("PTM", R.string.nav_ptm, "👥", Route.Ptm.route,  // i18n-ignore: English haystack entry
                "ptm", "parent teacher meeting", "meeting", "appointment"),
            feature("Payslips", R.string.mod_payslips, "🧾", Route.Payslips.route,  // i18n-ignore: English haystack entry
                "payslip", "payslips", "salary", "pay", "income", "earnings"),
        )
    }
}
