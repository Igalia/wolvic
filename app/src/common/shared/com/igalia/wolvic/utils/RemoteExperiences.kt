package com.igalia.wolvic.utils

data class Experience(
    val title: String,
    val thumbnail: String,
    val url: String,
    val device: String
)

data class Category(
    val translations: Map<String, String>,
    val items: List<Experience>
)

data class RemoteExperiences(
    var categories: Map<String, Category> = emptyMap()
) {
    companion object {
        const val CATEGORY_HEYVR = "heyvr"
        const val CATEGORY_HEYVR_DISPLAY_NAME = "HeyVR"
    }

    fun getCategoryNames(): List<String> =
        categories.keys.toList()

    fun getExperiencesForCategory(category: String): List<Experience> =
        categories[category]?.items ?: emptyList()

    fun getAllExperiences(): List<Experience> =
        categories.values.flatMap { it.items }

    fun getTranslationsForCategory(category: String): Map<String, String> =
        categories[category]?.translations ?: emptyMap()

    // If the translation is not available, return the English name or the category's id
    fun getCategoryNameForLanguage(category: String, languageCode: String): String? =
        categories[category]?.translations?.get(languageCode)
            ?: categories[category]?.translations?.get("en")
            ?: category

    fun setRemoteExperiences(regularExperiences: RemoteExperiences) {
        // preserve the existing HeyVR category
        val heyVRCategory = categories[CATEGORY_HEYVR]

        val updatedCategories = regularExperiences.categories.toMutableMap()

        if (heyVRCategory != null) {
            updatedCategories[CATEGORY_HEYVR] = heyVRCategory
        }

        categories = updatedCategories
    }

    fun setHeyVRExperiences(heyVRExperiences: List<Experience>) {
        val translations = mapOf("en" to CATEGORY_HEYVR_DISPLAY_NAME)

        val heyVRCategory = Category(translations, heyVRExperiences)

        categories = categories.toMutableMap().apply {
            put(CATEGORY_HEYVR, heyVRCategory)
        }
    }
}
