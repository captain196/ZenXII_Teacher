package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

data class PtmConfigDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val session: String = "",
    val title: String = "",
    val date: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val slotDuration: Int = 15,
    val breakBetween: Int = 5,
    val applicableClasses: List<String> = emptyList(),
    val status: String = "draft",     // draft, booking_open, closed, completed
    val totalSlots: Int = 0,
    val bookedSlots: Int = 0,
    val createdAt: Any? = null
)
