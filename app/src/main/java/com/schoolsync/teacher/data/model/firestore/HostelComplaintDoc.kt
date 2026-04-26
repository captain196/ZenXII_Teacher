package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

data class HostelComplaintDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val raisedBy: String = "",
    val roomId: String = "",
    val category: String = "",
    val subCategory: String = "",
    val description: String = "",
    val photos: List<String> = emptyList(),
    val priority: String = "medium",
    val status: String = "open",           // open, in_progress, resolved
    val assignedTo: String = "",
    val resolution: String = "",
    val createdAt: Any? = null,
    val resolvedAt: Any? = null
)
