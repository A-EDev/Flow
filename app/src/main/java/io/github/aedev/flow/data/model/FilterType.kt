package io.github.aedev.flow.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class FilterType {
    PK,
    LSC,
    HSC,
    LPQ,
    HPQ
}
