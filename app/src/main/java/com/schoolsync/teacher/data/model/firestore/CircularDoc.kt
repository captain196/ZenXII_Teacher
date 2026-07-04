package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

data class CircularDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val title: String = "",
    val body: String = "",
    // Optional rich HTML — set by admin for circulars with styled posters (HR auto-circular
    // uses this). When non-blank and contains markup, the detail view renders it in a WebView.
    val description: String = "",
    val author: String = "",
    val authorId: String = "",
    val authorRole: String = "",         // e.g. "Admin", "HR Manager", "Principal"
    val category: String = "",        // General, Academic, Event, Administrative, Emergency
    val priority: String = "Normal",  // Normal, Important, Urgent
    val targetType: String = "All",   // All, class, role (legacy)
    val targetClasses: List<String> = emptyList(),
    val targetRoles: List<String> = emptyList(),
    // Canonical audience scoping (2026-07-03). e.g. ["all"], ["role:teacher"],
    // ["class:Class 8th"], ["user:STU123"]. A viewer sees the doc when their
    // own key set intersects this. Empty ⇒ legacy doc, fall back to targetType.
    val audienceKeys: List<String> = emptyList(),
    val attachmentUrl: String = "",
    val requireAcknowledgement: Boolean = false,
    val totalRecipients: Int = 0,
    val readCount: Int = 0,
    val channels: List<String> = emptyList(),
    val status: String = "sent",      // draft, sent
    val sentAt: Any? = null,
    val expiresAt: Any? = null
)
