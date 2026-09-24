package io.github.aedev.flow.data.update

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** For the workers WorkManager constructs itself, which Hilt cannot inject. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface UpdateEntryPoint {
    fun updateRepository(): UpdateRepository
}
