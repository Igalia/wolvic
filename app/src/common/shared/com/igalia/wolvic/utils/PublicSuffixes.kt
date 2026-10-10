package com.igalia.wolvic.utils

import android.content.Context
import kotlinx.coroutines.runBlocking
import mozilla.components.lib.publicsuffixlist.PublicSuffixList

class PublicSuffixes(context: Context) {
    private val publicSuffixList = PublicSuffixList(context)

    fun prefetch() = publicSuffixList.prefetch()

    fun hasKnownPublicSuffix(host: String): Boolean = '.' in host && runBlocking {
        generateSequence(host.lowercase()) { it.substringAfter('.', "").ifEmpty { null } }
            .any { publicSuffixList.isPublicSuffix(it).await() }
    }
}
