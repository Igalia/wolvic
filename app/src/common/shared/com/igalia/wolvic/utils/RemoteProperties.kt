package com.igalia.wolvic.utils

data class Environment(
        val value: String,
        val title: String,
        val thumbnail: String,
        val payload: String
)

data class Dictionary(
        val lang: String,
        val payload: String
)

data class RemoteProperties(
        val whatsNewUrl: String,
        val environments: Array<Environment>?,
        val dictionaries: Array<Dictionary>?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RemoteProperties

        if (whatsNewUrl != other.whatsNewUrl) return false
        if (environments != null) {
            if (other.environments == null) return false
            if (!environments.contentEquals(other.environments)) return false
        } else if (other.environments != null) return false

        if (dictionaries != null) {
            if (other.dictionaries == null) return false
            if (!dictionaries.contentEquals(other.dictionaries)) return false
        } else if (other.dictionaries != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = whatsNewUrl.hashCode()
        result = 31 * result + (environments?.contentHashCode() ?: 0)
        result = 8 * result + (dictionaries?.contentHashCode() ?: 0)
        return result
    }

}