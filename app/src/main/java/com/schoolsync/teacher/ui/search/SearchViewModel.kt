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

/** Buckets a search hit falls into. `ordinal` drives the section order on screen. */
enum class SearchCategory(val label: String, val emoji: String) {
    STUDENT("Students", "🎓"),
    CLASS("My Classes", "🏫"),
    HOMEWORK("Homework", "📝"),
    NOTICE("Notices", "📢"),
    EVENT("Events", "🎉"),
    FEATURE("Go to", "🧭"),
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
                        title = "Class $classLabel".trim(),
                        subtitle = listOfNotNull(
                            a.subject.ifBlank { null },
                            if (a.classTeacher) "Class Teacher" else null
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
                        title = s.name.ifBlank { "(Unnamed student)" },
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
                        title = hw.title.ifBlank { "(Untitled)" },
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
                        title = n.title.ifBlank { "(Untitled notice)" },
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
                        title = e.title.ifBlank { "(Untitled event)" },
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
            title: String,
            emoji: String,
            route: String,
            vararg keywords: String
        ): Indexed = Indexed(
            SearchResult(
                id = "feat_$route",
                category = SearchCategory.FEATURE,
                title = title,
                subtitle = "Open $title",
                emoji = emoji,
                route = route,
            ),
            haystack = (listOf(title) + keywords).joinToString(" ").lowercase()
        )
        return listOf(
            feature("Take Attendance", "📅", Route.Attendance.route,
                "attendance", "present", "absent", "mark", "roll call"),
            feature("Marks", "📊", Route.Marks.route,
                "marks", "grades", "grade", "score", "exam", "result", "assessment"),
            feature("Homework", "📝", Route.Homework.route,
                "homework", "assignment", "assignments", "diary", "task", "work"),
            feature("Timetable", "🗓️", Route.Timetable.route,
                "timetable", "schedule", "routine", "periods", "class schedule"),
            feature("Students", "🎓", Route.Students.route,
                "students", "student", "roster", "class list", "children", "pupils"),
            feature("Chat", "💬", Route.Messages.route,
                "chat", "message", "messages", "parent", "conversation", "inbox"),
            feature("Notices", "📢", Route.Notices.route,
                "notices", "notice", "circular", "circulars", "announcement", "news"),
            feature("Leave", "🏖️", Route.Leave.route,
                "leave", "absence", "apply leave", "sick", "time off"),
            feature("Red Flags", "🚩", Route.RedFlags.route,
                "red flags", "flags", "discipline", "warning", "behaviour", "behavior"),
            feature("My Attendance", "🕒", Route.MyAttendance.route,
                "my attendance", "clock in", "punch", "check in", "self attendance"),
            feature("Lesson Plan", "📖", Route.LessonPlan.route,
                "lesson plan", "lesson", "plan", "syllabus", "topics", "today's lessons"),
            feature("Gallery", "🖼️", Route.Gallery.route,
                "gallery", "photos", "photo", "pictures", "album", "images"),
            feature("Events", "🎉", Route.Events.route,
                "events", "event", "holiday", "celebration", "calendar"),
            feature("Library", "📚", Route.Library.route,
                "library", "books", "book", "borrow", "catalogue"),
            feature("PTM", "👥", Route.Ptm.route,
                "ptm", "parent teacher meeting", "meeting", "appointment"),
            feature("Payslips", "🧾", Route.Payslips.route,
                "payslip", "payslips", "salary", "pay", "income", "earnings"),
        )
    }
}
