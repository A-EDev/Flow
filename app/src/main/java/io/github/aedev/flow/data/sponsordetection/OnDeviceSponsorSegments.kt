package io.github.aedev.flow.data.sponsordetection

import io.github.aedev.flow.data.model.SponsorBlockSegment

internal const val ON_DEVICE_UUID_PREFIX = "flow-ml-"

/** Model predictions are suggestions: the player offers a skip button for them and never skips on its own. */
fun SponsorBlockSegment.isOnDevicePrediction(): Boolean = uuid.startsWith(ON_DEVICE_UUID_PREFIX)
