package io.github.aedev.flow.utils

import java.text.Normalizer

private val DiacriticsRegex = Regex("\\p{Mn}+")

/** Case- and accent-insensitive form of [this], so "resolucion" finds "Resolución". */
fun String.foldForSearch(): String =
    Normalizer
        .normalize(this, Normalizer.Form.NFD)
        .replace(DiacriticsRegex, "")
        .lowercase()
