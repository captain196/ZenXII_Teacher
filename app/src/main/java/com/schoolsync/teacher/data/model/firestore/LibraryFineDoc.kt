package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

data class LibraryFineDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val issueId: String = "",
    val bookId: String = "",
    val bookTitle: String = "",
    val borrowerId: String = "",
    val borrowerName: String = "",
    val fineAmount: Double = 0.0,
    val reason: String = "overdue",
    val status: String = "pending",        // pending, paid, waived
    val paidAt: String = "",
    val waivedBy: String = ""
)
