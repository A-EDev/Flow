package io.github.aedev.flow.utils

import org.schabi.newpipe.extractor.Image

fun List<Image>?.bestImageUrl(): String =
    this.orEmpty()
        .maxByOrNull { maxOf(it.width, it.height) }
        ?.url
        .orEmpty()

fun List<Image>?.distinctBestImageUrls(limit: Int = 2): List<String> =
    this.orEmpty()
        .asSequence()
        .filter { !it.url.isNullOrBlank() }
        .sortedByDescending { maxOf(it.width, it.height) }
        .distinctBy { it.url.avatarImageIdentityKey() }
        .mapNotNull { it.url }
        .take(limit)
        .toList()

internal fun String?.avatarImageIdentityKey(): String =
    orEmpty()
        .substringBefore("?")
        .replace(Regex("=s\\d+.*$"), "")
