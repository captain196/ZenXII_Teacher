package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

data class LibraryIssueDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val bookId: String = "",
    val bookTitle: String = "",
    val barcode: String = "",
    val borrowerId: String = "",
    val borrowerName: String = "",
    val borrowerType: String = "student",
    val issueDate: String = "",
    val dueDate: String = "",
    val returnDate: String = "",
    val renewals: Int = 0,
    val maxRenewals: Int = 2,
    val fine: Double = 0.0,
    val status: String = "issued",         // issued, returned, overdue
    val issuedBy: String = "",
    val returnedTo: String = ""
)
