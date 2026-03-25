package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

data class HostelAllocationDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val roomId: String = "",
    val roomNumber: String = "",
    val bedNo: String = "",
    val mealPlan: String = "vegetarian",
    val allocatedAt: String = "",
    val status: String = "active"
)
