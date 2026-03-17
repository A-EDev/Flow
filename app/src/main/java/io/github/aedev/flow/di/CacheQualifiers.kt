package io.github.aedev.flow.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PlayerCache

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadCache
