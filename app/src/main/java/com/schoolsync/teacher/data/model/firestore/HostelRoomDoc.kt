package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

data class HostelRoomDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val floor: Int = 0,
    val wing: String = "",
    val roomNumber: String = "",
    val roomType: String = "dormitory",
    val capacity: Int = 0,
    val occupied: Int = 0,
    val beds: Map<String, HostelBedDoc> = emptyMap(),
    val wardenId: String = "",
    val amenities: List<String> = emptyList(),
    val status: String = "active"
)

data class HostelBedDoc(
    val studentId: String = "",
    val studentName: String = ""
)
