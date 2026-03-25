package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

data class LibraryBookDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val title: String = "",
    val authors: List<String> = emptyList(),
    val isbn: String = "",
    val publisher: String = "",
    val edition: String = "",
    val year: Int = 0,
    val category: String = "",
    val subject: String = "",
    val language: String = "English",
    val barcode: String = "",
    val rfidTag: String = "",
    val location: String = "",
    val shelf: String = "",
    val totalCopies: Int = 0,
    val availableCopies: Int = 0,
    val issuedCopies: Int = 0,
    val coverImage: String = "",
    val status: String = "available"
)
