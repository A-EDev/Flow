package io.github.aedev.flow.data.sponsordetection

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SponsorModelRepository
    @Inject
    constructor() {
        val state: StateFlow<SponsorModelState> = MutableStateFlow(SponsorModelState.NotInstalled)

        fun refresh() = Unit

        fun download() = Unit

        fun delete() = Unit
    }
