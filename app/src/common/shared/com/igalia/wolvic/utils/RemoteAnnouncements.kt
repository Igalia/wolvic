package com.igalia.wolvic.utils

data class Announcement(
    val id: String,
    val date: String,
    val title: Map<String, String>,
    val body: Map<String, String>,
    val image: String? = null,
    val link: String? = null
)

data class RemoteAnnouncements(
    var announcements: List<Announcement> = emptyList()
) {
    fun getAnnouncementTitleForLanguage(announcement: Announcement, languageCode: String): String =
        announcement.title[languageCode]
            ?: announcement.title["en"]
            ?: ""

    fun getAnnouncementBodyForLanguage(announcement: Announcement, languageCode: String): String =
        announcement.body[languageCode]
            ?: announcement.body["en"]
            ?: ""
}