package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class MessageTemplateDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val name: String = "",
    val channel: String = "",         // sms, push, email, whatsapp
    val body: String = "",
    val mergeFields: List<String> = emptyList(),
    val category: String = "",
    val createdBy: String = "",
    @ServerTimestamp
    val createdAt: Timestamp? = null
)
