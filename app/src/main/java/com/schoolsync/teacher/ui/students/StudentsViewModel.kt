package com.schoolsync.teacher.ui.students

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.schoolsync.teacher.data.model.ClassAssignment
import com.schoolsync.teacher.data.model.StudentInfo as ModelStudentInfo
import com.schoolsync.teacher.data.repository.StudentRepository
import com.schoolsync.teacher.data.repository.TeacherRepository
import com.schoolsync.teacher.data.repository.firestore.StudentFirestoreRepository
import com.schoolsync.teacher.util.RoleHelper
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val address: String = ""
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
    private val studentRepository: StudentRepository, // TODO: Remove RTDB fallback after Firestore validation
    private val teacherRepository: TeacherRepository,
    private val studentFirestoreRepo: StudentFirestoreRepository
) : ViewModel() {

    companion object {
        private const val TAG = "StudentsVM"
    }

    private val _uiState = MutableStateFlow(StudentsUiState())
    val uiState: StateFlow<StudentsUiState> = _uiState.asStateFlow()

    // Cached assignments for role-based permission checks
    private var cachedAssignments: List<ClassAssignment> = emptyList()

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
                // TODO: Remove RTDB fallback after Firestore validation
                studentFirestoreRepo.getStudentsByClass(
                    className = state.selectedClassName,
                    section = state.selectedSection
                ).fold(
                    onSuccess = { studentDocs ->
                        if (studentDocs.isNotEmpty()) {
                            val studentInfos = studentDocs.map { doc ->
                                StudentInfo(
                                    studentId = doc.id,
                                    name = doc.name,
                                    rollNo = doc.rollNo.toIntOrNull() ?: 0,
                                    className = doc.className,
                                    section = doc.section,
                                    fatherName = doc.fatherName,
                                    motherName = doc.motherName,
                                    phone = doc.phone,
                                    email = doc.email,
                                    dob = doc.dob,
                                    gender = doc.gender,
                                    admissionDate = doc.admissionDate,
                                    profilePicUrl = doc.profilePic
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
                        // TODO: Remove RTDB fallback after Firestore validation
                        loadStudentsFromRtdb()
                    }
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    // TODO: Remove RTDB fallback after Firestore validation
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
        _uiState.update { it.copy(selectedStudent = student) }
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
}
