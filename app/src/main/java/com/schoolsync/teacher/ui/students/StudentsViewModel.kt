package com.schoolsync.teacher.ui.students

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.schoolsync.teacher.data.model.ClassAssignment
import com.schoolsync.teacher.data.model.StudentInfo as ModelStudentInfo
import com.schoolsync.teacher.data.repository.StudentRepository
import com.schoolsync.teacher.data.repository.TeacherRepository
import com.schoolsync.teacher.data.repository.firestore.FeeFirestoreRepository
import com.schoolsync.teacher.data.repository.firestore.StudentFirestoreRepository
import com.schoolsync.teacher.util.RoleHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StudentInfo(
    val studentId: String,
    val name: String,
    val rollNo: Int,
    val className: String,
    val section: String,
    val fatherName: String = "",
    val motherName: String = "",
    val phone: String = "",
    val email: String = "",
    val dob: String = "",
    val gender: String = "",
    val admissionDate: String = "",
    val profilePicUrl: String = "",
    val address: String = "",
    /** Per-month paid flags (e.g. `{"April" -> 1, "May" -> 0}`). */
    val monthFee: Map<String, Int> = emptyMap()
)

data class StudentsUiState(
    val students: List<StudentInfo> = emptyList(),
    val filteredStudents: List<StudentInfo> = emptyList(),
    val availableClasses: List<Pair<String, String>> = emptyList(),
    val selectedClassName: String = "",
    val selectedSection: String = "",
    val searchQuery: String = "",
    val selectedStudent: StudentInfo? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isClassTeacher: Boolean = true,
    val assignedSubjects: List<String> = emptyList()
)

@HiltViewModel
class StudentsViewModel @Inject constructor(
    private val studentRepository: StudentRepository,
    private val teacherRepository: TeacherRepository,
    private val studentFirestoreRepo: StudentFirestoreRepository,
    private val feeFirestoreRepo: FeeFirestoreRepository
) : ViewModel() {

    companion object {
        private const val TAG = "StudentsVM"
    }

    private val _uiState = MutableStateFlow(StudentsUiState())
    val uiState: StateFlow<StudentsUiState> = _uiState.asStateFlow()

    // Cached assignments for role-based permission checks
    private var cachedAssignments: List<ClassAssignment> = emptyList()

    // Active listener job for the currently-selected student. Cancelled
    // and re-attached on every selectStudent() call so the monthFee chips
    // on StudentsScreen update reactively when admin or parent payments
    // change Firestore — no manual refresh needed.
    private var selectedDemandsJob: Job? = null

    init {
        loadClasses()
    }

    private fun loadClasses() {
        viewModelScope.launch {
            try {
                teacherRepository.getAssignedClasses().fold(
                    onSuccess = { assignments ->
                        cachedAssignments = assignments
                        val classes = RoleHelper.getAssignedClasses(assignments)
                        val firstClass = classes.firstOrNull()
                        val isClassTeacherForFirst = firstClass?.let {
                            RoleHelper.isClassTeacher(assignments, it.first, it.second)
                        } ?: true
                        val subjectsForFirst = firstClass?.let {
                            RoleHelper.getAssignedSubjects(assignments, it.first, it.second)
                        } ?: emptyList()
                        _uiState.update {
                            it.copy(
                                availableClasses = classes,
                                selectedClassName = firstClass?.first ?: "",
                                selectedSection = firstClass?.second ?: "",
                                isClassTeacher = isClassTeacherForFirst,
                                assignedSubjects = subjectsForFirst
                            )
                        }
                        if (classes.isNotEmpty()) {
                            loadStudents()
                        }
                    },
                    onFailure = { e ->
                        _uiState.update { it.copy(error = e.message) }
                    }
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun selectClass(className: String, section: String) {
        val isClassTeacherForClass = RoleHelper.isClassTeacher(
            cachedAssignments, className, section
        )
        val subjects = RoleHelper.getAssignedSubjects(cachedAssignments, className, section)
        _uiState.update {
            it.copy(
                selectedClassName = className,
                selectedSection = section,
                selectedStudent = null,
                isClassTeacher = isClassTeacherForClass,
                assignedSubjects = subjects
            )
        }
        loadStudents()
    }

    private fun loadStudents() {
        val state = _uiState.value
        if (state.selectedClassName.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // Primary: Firestore student data
                studentFirestoreRepo.getStudentsByClass(
                    className = state.selectedClassName,
                    section = state.selectedSection
                ).fold(
                    onSuccess = { studentDocs ->
                        if (studentDocs.isNotEmpty()) {
                            val studentInfos = studentDocs.map { doc ->
                                StudentInfo(
                                    // Prefer userId (e.g. "STU0001") over the
                                    // composite Firestore doc id ("{schoolId}_STU0001").
                                    // Some legacy admin writes only set studentId, so
                                    // chain through both before falling back to the doc id.
                                    studentId = doc.userId
                                        .ifBlank { doc.studentId }
                                        .ifBlank { doc.id },
                                    name = doc.name,
                                    rollNo = doc.rollNo.toIntOrNull() ?: 0,
                                    className = doc.className,
                                    section = doc.section,
                                    fatherName = doc.fatherName,
                                    motherName = doc.motherName,
                                    phone = doc.phone.ifBlank { doc.phoneNumber },
                                    email = doc.email,
                                    dob = doc.dob,
                                    gender = doc.gender,
                                    admissionDate = doc.admissionDate,
                                    profilePicUrl = doc.profilePic,
                                    address = flattenAddress(doc.address),
                                    monthFee = coerceMonthFee(doc.monthFee)
                                )
                            }.sortedBy { it.rollNo }

                            _uiState.update {
                                it.copy(
                                    students = studentInfos,
                                    filteredStudents = filterStudents(studentInfos, it.searchQuery),
                                    isLoading = false
                                )
                            }
                        } else {
                            // No data in Firestore, fallback to RTDB
                            loadStudentsFromRtdb()
                        }
                    },
                    onFailure = { firestoreError ->
                        Log.e(TAG, "Firestore getStudentsByClass failed, falling back: ${firestoreError.message}")
                        // Fallback: RTDB student data
                        loadStudentsFromRtdb()
                    }
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private suspend fun loadStudentsFromRtdb() {
        val state = _uiState.value
        studentRepository.getStudentsForClass(
            className = state.selectedClassName,
            section = state.selectedSection
        ).fold(
            onSuccess = { modelStudents ->
                val studentInfos = modelStudents.map { s ->
                    StudentInfo(
                        studentId = s.studentId,
                        name = s.displayName,
                        rollNo = s.rollNo.toIntOrNull() ?: 0,
                        className = s.className,
                        section = s.section,
                        fatherName = s.fatherName,
                        motherName = s.motherName,
                        phone = s.phone,
                        email = s.email,
                        dob = s.dob,
                        gender = s.gender,
                        admissionDate = s.admissionDate,
                        profilePicUrl = s.profilePic
                    )
                }.sortedBy { it.rollNo }

                _uiState.update {
                    it.copy(
                        students = studentInfos,
                        filteredStudents = filterStudents(studentInfos, it.searchQuery),
                        isLoading = false
                    )
                }
            },
            onFailure = { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        )
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                filteredStudents = filterStudents(it.students, query)
            )
        }
    }

    fun selectStudent(student: StudentInfo?) {
        // Detach previous student's listeners before swapping selection.
        selectedDemandsJob?.cancel()
        selectedDemandsJob = null

        _uiState.update { it.copy(selectedStudent = student) }
        if (student == null) return

        // Demands listener — derives a fresh per-month paid map from
        // Firestore so the FeeSnapshotCard chips update the moment a
        // parent payment lands. Replaces the stale `monthFee` value
        // from the initial roster load.
        selectedDemandsJob = viewModelScope.launch {
            feeFirestoreRepo.observeStudentFeeDemands(student.studentId)
                .collect { demands ->
                    val derived = demands
                        .groupBy { it.month.ifBlank { "Unknown" } }
                        .mapValues { (_, group) ->
                            // 1 = all demands for the month are paid; 0 otherwise
                            if (group.isNotEmpty() && group.all { it.status == "paid" }) 1 else 0
                        }
                    _uiState.update { current ->
                        val sel = current.selectedStudent
                        if (sel?.studentId == student.studentId) {
                            // SW4-companion-C defensive guard (2026-05-27): do
                            // not overwrite a populated monthFee with an empty
                            // derivation. Empty emissions come from (a) the
                            // upstream .onStart { emit(emptyList()) } priming
                            // pulse, or (b) the .catch fallback when the
                            // Firestore composite index is missing. In both
                            // cases the initial-roster monthFee (from
                            // StudentDoc.monthFee, backend-populated) is the
                            // best available state — keep it visible rather
                            // than blanking every chip.
                            if (derived.isEmpty() && sel.monthFee.isNotEmpty()) {
                                current
                            } else {
                                current.copy(selectedStudent = sel.copy(monthFee = derived))
                            }
                        } else current
                    }
                }
        }
    }

    private fun filterStudents(students: List<StudentInfo>, query: String): List<StudentInfo> {
        if (query.isBlank()) return students
        val lower = query.lowercase()
        return students.filter {
            it.name.lowercase().contains(lower) ||
                    it.rollNo.toString().contains(lower) ||
                    it.fatherName.lowercase().contains(lower) ||
                    it.studentId.lowercase().contains(lower)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun refresh() {
        loadStudents()
    }

    override fun onCleared() {
        super.onCleared()
        selectedDemandsJob?.cancel()
    }
}

/**
 * The admin panel writes student `address` two ways:
 *   - flat string ("123 Main St, Indore, MP 452001")
 *   - nested map ({street, city, state, pincode, country, …})
 *
 * Both shapes land in [StudentDoc.address] as `Any?`. This helper coerces
 * either into a single human-readable line for the profile screen, while
 * silently swallowing the third case (`null` / unknown shape) so a missing
 * address never blanks the screen.
 */
/**
 * `StudentDoc.monthFee` is declared as `Any?` because different writers
 * have emitted it as a map (new code) or never (legacy). This helper
 * coerces into a stable `Map<String, Int>` where 1 = paid, 0 = pending.
 */
private fun coerceMonthFee(raw: Any?): Map<String, Int> {
    if (raw !is Map<*, *>) return emptyMap()
    val out = mutableMapOf<String, Int>()
    raw.forEach { (k, v) ->
        val key = (k as? String)?.trim()?.takeIf(String::isNotBlank) ?: return@forEach
        val value = when (v) {
            is Number -> v.toInt()
            is Boolean -> if (v) 1 else 0
            is String -> v.toIntOrNull() ?: (if (v.equals("true", true) || v == "1" || v.equals("paid", true)) 1 else 0)
            else -> 0
        }
        out[key] = if (value >= 1) 1 else 0
    }
    return out
}

private fun flattenAddress(raw: Any?): String {
    if (raw == null) return ""
    if (raw is String) return raw
    if (raw is Map<*, *>) {
        // Common admin keys, in display order. Anything missing is skipped.
        val keys = listOf("street", "Address", "address", "city", "state", "pincode", "country")
        val parts = keys.mapNotNull { k ->
            val v = raw[k]?.toString()?.trim()
            if (v.isNullOrBlank()) null else v
        }
        if (parts.isNotEmpty()) return parts.joinToString(", ")
        // Unknown map shape — fall back to all values, in any order.
        return raw.values.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotBlank) }
            .joinToString(", ")
    }
    return raw.toString()
}
