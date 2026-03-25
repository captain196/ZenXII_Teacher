package com.schoolsync.teacher.ui.fees

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.model.ClassFeeOverview
import com.schoolsync.teacher.data.model.FeeDefaulter
import com.schoolsync.teacher.data.model.StudentFeeStatus
import com.schoolsync.teacher.data.repository.FeeRepository
import com.schoolsync.teacher.data.repository.TeacherRepository
import com.schoolsync.teacher.ui.attendance.ClassSection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FeesUiState(
    val isLoading: Boolean = false,
    val availableClasses: List<ClassSection> = emptyList(),
    val selectedClass: ClassSection? = null,
    val classOverview: ClassFeeOverview? = null,
    val studentStatuses: List<StudentFeeStatus> = emptyList(),
    val defaulters: List<FeeDefaulter> = emptyList(),
    val selectedView: String = "summary", // "summary" or "defaulters"
    val errorMessage: String? = null
)

@HiltViewModel
class FeesTeacherViewModel @Inject constructor(
    private val feeRepository: FeeRepository,
    private val teacherRepository: TeacherRepository
) : ViewModel() {

    companion object {
        private const val TAG = "FeesTeacherVM"
    }

    private val _uiState = MutableStateFlow(FeesUiState())
    val uiState: StateFlow<FeesUiState> = _uiState.asStateFlow()

    init {
        loadAssignedClasses()
    }

    private fun loadAssignedClasses() {
        viewModelScope.launch {
            try {
                teacherRepository.getAssignedClasses().fold(
                    onSuccess = { assignments ->
                        Log.d(TAG, "Loaded ${assignments.size} assignments")
                        val classSections = assignments
                            .map { ClassSection(it.className, it.section) }
                            .distinct()
                        Log.d(TAG, "Distinct classes: ${classSections.map { it.displayName }}")
                        val firstClass = classSections.firstOrNull()
                        _uiState.update {
                            it.copy(
                                availableClasses = classSections,
                                selectedClass = firstClass
                            )
                        }
                        if (firstClass != null) {
                            loadFees(firstClass.className, firstClass.section)
                        }
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Failed to load assignments: ${e.message}", e)
                        _uiState.update { it.copy(errorMessage = e.message) }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load classes", e)
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun selectClass(classSection: ClassSection) {
        if (_uiState.value.selectedClass == classSection) return
        _uiState.update { it.copy(selectedClass = classSection) }
        loadFees(classSection.className, classSection.section)
    }

    fun loadFees(className: String, section: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                val overviewDeferred = async { feeRepository.getClassFeeOverview(className, section) }
                val statusesDeferred = async { feeRepository.getStudentFeeStatuses(className, section) }
                val defaultersDeferred = async { feeRepository.getDefaulterList(className, section) }

                val overviewResult = overviewDeferred.await()
                val statusesResult = statusesDeferred.await()
                val defaultersResult = defaultersDeferred.await()

                _uiState.update { current ->
                    current.copy(
                        classOverview = overviewResult.getOrNull(),
                        studentStatuses = statusesResult.getOrElse { emptyList() },
                        defaulters = defaultersResult.getOrElse { emptyList() },
                        isLoading = false,
                        errorMessage = overviewResult.exceptionOrNull()?.message
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load fees", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message
                    )
                }
            }
        }
    }

    fun switchView(view: String) {
        _uiState.update { it.copy(selectedView = view) }
    }

    fun refresh() {
        val selected = _uiState.value.selectedClass ?: return
        loadFees(selected.className, selected.section)
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
