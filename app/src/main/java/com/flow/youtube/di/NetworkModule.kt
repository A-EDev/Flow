package com.flow.youtube.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Enhanced Network Module for Blazing Fast Performance
 * 
 * Optimizations:
 * - Connection pooling: Reuse connections for faster subsequent requests
 * - HTTP/2 support: Multiplexing for parallel requests on single connection
 * - Aggressive dispatcher: High concurrency for parallel fetching
 * - Optimized timeouts: Fast fail for unresponsive servers
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // Cache configuration
    private const val CACHE_SIZE_MB = 100L * 1024L * 1024L // 100MB cache
    
    // Connection pool configuration
    private const val MAX_IDLE_CONNECTIONS = 15
    private const val KEEP_ALIVE_DURATION_MINUTES = 5L
    
    // Timeout configuration (in seconds)
    private const val CONNECT_TIMEOUT = 15L
    private const val READ_TIMEOUT = 30L
    private const val WRITE_TIMEOUT = 30L
    private const val CALL_TIMEOUT = 60L
    
    // Dispatcher configuration
    private const val MAX_REQUESTS_TOTAL = 64 // Max concurrent requests
    private const val MAX_REQUESTS_PER_HOST = 10 // Max per host

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient {
        val cacheDirectory = File(context.cacheDir, "http_cache")
        val cache = Cache(cacheDirectory, CACHE_SIZE_MB)
        
        // Connection pool for connection reuse (massive speed improvement)
        val connectionPool = ConnectionPool(
            MAX_IDLE_CONNECTIONS,
            KEEP_ALIVE_DURATION_MINUTES,
            TimeUnit.MINUTES
        )
        
        // Dispatcher for high concurrency parallel requests
        val dispatcher = Dispatcher().apply {
            maxRequests = MAX_REQUESTS_TOTAL
            maxRequestsPerHost = MAX_REQUESTS_PER_HOST
        }

        return OkHttpClient.Builder()
            // Enable HTTP/2 for multiplexing (parallel streams on single connection)
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            
            // Connection pool for reuse
            .connectionPool(connectionPool)
            
            // High concurrency dispatcher
            .dispatcher(dispatcher)
            
            // Response caching
            .cache(cache)
            
            // Optimized timeouts
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT, TimeUnit.SECONDS)
            
            // Retry on connection failure
            .retryOnConnectionFailure(true)
            
            // Follow redirects
            .followRedirects(true)
            .followSslRedirects(true)
            
            .build()
    }
    
    /**
     * Provides a lightweight client for quick metadata requests
     * Uses shorter timeouts for fast-fail behavior
     */
    @Provides
    @Singleton
    @MetadataClient
    fun provideMetadataClient(@ApplicationContext context: Context): OkHttpClient {
        return OkHttpClient.Builder()
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .connectionPool(ConnectionPool(5, 2, TimeUnit.MINUTES))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}

/**
 * Qualifier annotation for metadata-specific HTTP client
 */
@javax.inject.Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MetadataClient
