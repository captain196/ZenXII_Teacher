package com.schoolsync.teacher.data.model

data class Story(
    val storyId: String = "",
    val mediaUrl: String = "",
    val type: String = "image", // image, video
    val caption: String = "",
    val createdAt: Long = 0L,
    val expiresAt: Long = 0L,
    val teacherName: String = "",
    val teacherPic: String = "",
    val viewCount: Int = 0
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "mediaUrl" to mediaUrl,
        "type" to type,
        "caption" to caption,
        "createdAt" to createdAt,
        "expiresAt" to expiresAt,
        "teacherName" to teacherName,
        "teacherPic" to teacherPic
    )

    val isExpired: Boolean get() = System.currentTimeMillis() > expiresAt

    companion object {
        fun fromMap(storyId: String, data: Map<String, Any?>): Story = Story(
            storyId = storyId,
            mediaUrl = data["mediaUrl"]?.toString() ?: "",
            type = data["type"]?.toString() ?: "image",
            caption = data["caption"]?.toString() ?: "",
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
            expiresAt = (data["expiresAt"] as? Number)?.toLong() ?: 0L,
            teacherName = data["teacherName"]?.toString() ?: "",
            teacherPic = data["teacherPic"]?.toString() ?: ""
        )
    }
}
