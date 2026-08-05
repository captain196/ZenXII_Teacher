package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

/**
 * School event doc as written by the admin panel.
 *
 * Collection: `events`
 * Canonical shape matches the Parent app's EventDoc so both apps deserialise
 * identically and neither depends on the other's model.
 */
data class EventDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val session: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = "",
    val startDate: String = "",       // "2026-04-15"
    val endDate: String = "",
    val location: String = "",
    val organizer: String = "",
    val status: String = "scheduled", // canonical: scheduled / ongoing / completed / cancelled
    val mediaUrls: List<String> = emptyList(),
    val createdBy: String = "",
    val createdAt: Any? = null
)
