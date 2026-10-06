package io.github.aedev.flow.data.sponsordetection

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SponsorModelRepositoryEntryPoint {
    fun sponsorModelRepository(): SponsorModelRepository
}
