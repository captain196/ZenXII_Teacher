package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class PtmBookingDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val ptmId: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val parentId: String = "",
    val parentName: String = "",
    val teacherId: String = "",
    val teacherName: String = "",
    val sectionKey: String = "",
    val slotTime: String = "",
    val slotDate: String = "",
    val status: String = "booked",    // booked, completed, cancelled
    @ServerTimestamp
    val bookedAt: Timestamp? = null
)
