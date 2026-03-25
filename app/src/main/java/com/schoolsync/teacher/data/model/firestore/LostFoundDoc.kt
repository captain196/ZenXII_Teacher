package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

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
    @ServerTimestamp
    val createdAt: Timestamp? = null
)
