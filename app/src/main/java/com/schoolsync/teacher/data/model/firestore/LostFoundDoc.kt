package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

data class LostFoundDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val type: String = "lost",             // lost, found
    val description: String = "",
    val category: String = "",
    val reportedBy: String = "",
    val reportedByName: String = "",
    val date: String = "",
    val location: String = "",
    val photo: String = "",
    val claimed: Boolean = false,
    val claimedBy: String = "",
    val status: String = "open",           // open, claimed, closed
    val createdAt: Any? = null
)
