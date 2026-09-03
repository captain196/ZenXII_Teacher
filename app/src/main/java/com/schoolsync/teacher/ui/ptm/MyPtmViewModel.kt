package com.schoolsync.teacher.ui.ptm

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.teacher.data.model.firestore.PtmEventDoc
import com.schoolsync.teacher.data.repository.firestore.PtmFirestoreRepository
import com.schoolsync.teacher.data.repository.firestore.TeacherStudentView
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.schoolsync.teacher.R
import com.schoolsync.teacher.util.localizedString
import com.schoolsync.teacher.util.localizedPlural

data class MyPtmState(
    val isLoading: Boolean = true,
    val ptms: List<PtmEventDoc> = emptyList(),
    /** Per-student attendance rows keyed by ptmEventId, ordered by queue. */
    val rowsByPtm: Map<String, List<TeacherStudentView>> = emptyMap(),
    val error: String? = null,
    val toast: String? = null
)

@HiltViewModel
class MyPtmViewModel @Inject constructor(
    private val ptmRepo: PtmFirestoreRepository,
    // Resolves user-facing copy in the app's chosen language; the
    // application Context is locale-wrapped by LocaleManager.
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(MyPtmState())
    val state: StateFlow<MyPtmState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, toast = null) }
            try {
                val ptms = ptmRepo.getMyPtms().getOrElse {
                    Log.w("MyPtmVM", "getMyPtms failed", it)
                    _state.update { s -> s.copy(isLoading = false, error = it.message ?: appContext.localizedString(R.string.vm_ptm_load_failed)) }
                    return@launch
                }
                val byPtm = mutableMapOf<String, List<TeacherStudentView>>()
                for (ptm in ptms) {
                    val ptmId = ptm.ptmEventId.ifBlank { ptm.id }
                    val rows = ptmRepo.getRsvpsForMyPtm(ptm).getOrNull().orEmpty()
                    byPtm[ptmId] = rows
                }
                _state.update { it.copy(isLoading = false, ptms = ptms, rowsByPtm = byPtm) }
            } catch (e: Exception) {
                Log.e("MyPtmVM", "load exception", e)
                _state.update { it.copy(isLoading = false, error = e.message ?: appContext.localizedString(R.string.vm_ptm_load_failed)) }
            }
        }
    }

    /** Mark one student delivered. Optimistic UI flip; reload on failure. */
    fun markDelivered(ptmEventId: String, studentId: String) {
        viewModelScope.launch {
            // Optimistic
            updateRowStatus(ptmEventId, studentId, "delivered")
            val res = ptmRepo.markDelivered(ptmEventId, studentId, "delivered")
            if (res.isFailure) {
                Log.w("MyPtmVM", "markDelivered failed", res.exceptionOrNull())
                _state.update { it.copy(toast = appContext.localizedString(R.string.vm_ptm_mark_failed)) }
                load()
            }
        }
    }

    /** Mark one student no-show. */
    fun markNoShow(ptmEventId: String, studentId: String) {
        viewModelScope.launch {
            updateRowStatus(ptmEventId, studentId, "no-show")
            val res = ptmRepo.markDelivered(ptmEventId, studentId, "no-show")
            if (res.isFailure) {
                Log.w("MyPtmVM", "markNoShow failed", res.exceptionOrNull())
                _state.update { it.copy(toast = appContext.localizedString(R.string.vm_ptm_update_failed)) }
                load()
            }
        }
    }

    /** Bulk: mark every applied row delivered for this PTM. */
    fun markAllDelivered(ptmEventId: String) {
        viewModelScope.launch {
            val current = _state.value.rowsByPtm[ptmEventId].orEmpty()
            val toFlip = current.filter { it.status == "applied" }
            if (toFlip.isEmpty()) {
                _state.update { it.copy(toast = appContext.localizedString(R.string.vm_ptm_nothing)) }
                return@launch
            }
            // Optimistic flip-all
            val updated = current.map { v ->
                if (v.status == "applied") v.copy(status = "delivered") else v
            }
            _state.update { it.copy(rowsByPtm = it.rowsByPtm + (ptmEventId to updated)) }

            val res = ptmRepo.markAllDelivered(ptmEventId)
            res.fold(
                onSuccess = { count ->
                    _state.update { it.copy(toast = appContext.localizedPlural(R.plurals.ptm_marked_delivered, count, count)) }
                },
                onFailure = { err ->
                    Log.w("MyPtmVM", "markAllDelivered failed", err)
                    _state.update { it.copy(toast = appContext.localizedString(R.string.vm_ptm_bulk_failed)) }
                    load()
                }
            )
        }
    }

    private fun updateRowStatus(ptmEventId: String, studentId: String, newStatus: String) {
        val current = _state.value.rowsByPtm[ptmEventId].orEmpty()
        val updated = current.map { v ->
            if (v.rsvp.studentId == studentId) v.copy(status = newStatus) else v
        }
        _state.update { it.copy(rowsByPtm = it.rowsByPtm + (ptmEventId to updated)) }
    }

    /** Clear the toast slot once the screen has shown it. */
    fun consumeToast() {
        _state.update { it.copy(toast = null) }
    }
}
