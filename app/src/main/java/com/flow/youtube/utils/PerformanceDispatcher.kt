/*
 * Copyright (C) 2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 *
 * Flow is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * any later version.
 */

package com.flow.youtube.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Performance Dispatcher - Centralized coroutine management for fast performance
 * 
 * This module provides:
 * - Optimized thread pools for different workload types
 * - Parallel task execution with automatic error isolation
 * - Timeout protection for network operations
 * - Load balancing across available cores
 */
object PerformanceDispatcher {
    
    // Get available processors for optimal thread allocation
    private val availableProcessors = Runtime.getRuntime().availableProcessors()
    
    // Network I/O dispatcher - Optimized for high-concurrency network operations
    // Uses more threads than CPU cores since network ops are I/O bound
    private val networkExecutor = Executors.newFixedThreadPool(
        (availableProcessors * 4).coerceIn(8, 32)
    ) { runnable ->
        Thread(runnable, "FlowNetwork-${networkThreadCounter.incrementAndGet()}").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY
        }
    }
    private val networkThreadCounter = AtomicInteger(0)
    
    /**
     * Dispatcher optimized for network operations
     * Higher concurrency for I/O bound tasks
     */
    val networkIO: CoroutineDispatcher = networkExecutor.asCoroutineDispatcher()
    
    /**
     * Dispatcher for CPU-intensive parsing operations
     */
    val parsing: CoroutineDispatcher = Dispatchers.Default
    
    /**
     * Dispatcher for disk I/O operations (database, file)
     */
    val diskIO: CoroutineDispatcher = Dispatchers.IO
    
    /**
     * Main thread dispatcher for UI updates
     */
    val main: CoroutineDispatcher = Dispatchers.Main
    
    // Global supervisor scope for background tasks
    private val supervisorJob = SupervisorJob()
    val backgroundScope = CoroutineScope(diskIO + supervisorJob)
    
    /**
     * Execute multiple network tasks in parallel with error isolation
     * If one task fails, others continue executing
     * 
     * @param tasks List of suspend functions to execute
     * @param timeoutMs Maximum time for all tasks (default 30 seconds)
     * @return List of successful results (failed tasks return null)
     */
    suspend fun <T> parallelFetch(
        vararg tasks: suspend () -> T?,
        timeoutMs: Long = 30_000L
    ): List<T?> = supervisorScope {
        tasks.map { task ->
            async(networkIO) {
                withTimeoutOrNull(timeoutMs) {
                    try {
                        task()
                    } catch (e: Exception) {
                        android.util.Log.w("PerformanceDispatcher", "Parallel task failed: ${e.message}")
                        null
                    }
                }
            }
        }.awaitAll()
    }
    
    /**
     * Execute multiple network tasks in parallel and collect non-null results
     * 
     * @param tasks List of suspend functions to execute
     * @param timeoutMs Maximum time for all tasks
     * @return List of successful non-null results
     */
    suspend fun <T : Any> parallelFetchNonNull(
        vararg tasks: suspend () -> T?,
        timeoutMs: Long = 30_000L
    ): List<T> = parallelFetch(*tasks, timeoutMs = timeoutMs).filterNotNull()
    
    /**
     * Execute a list of tasks with a concurrency limit
     * Prevents overwhelming the network with too many concurrent requests
     * 
     * @param items Items to process
     * @param concurrencyLimit Maximum concurrent tasks
     * @param transform Transformation function for each item
     */
    suspend fun <T, R> parallelMap(
        items: List<T>,
        concurrencyLimit: Int = 6,
        transform: suspend (T) -> R?
    ): List<R> = supervisorScope {
        items
            .chunked(concurrencyLimit)
            .flatMap { chunk ->
                chunk.map { item ->
                    async(networkIO) {
                        try {
                            transform(item)
                        } catch (e: Exception) {
                            android.util.Log.w("PerformanceDispatcher", "Transform failed: ${e.message}")
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
            }
    }
    
    /**
     * Execute a task with automatic retry on failure
     * 
     * @param maxAttempts Maximum retry attempts
     * @param delayMs Delay between attempts (uses exponential backoff)
     * @param task The task to execute
     */
    suspend fun <T> withRetry(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 500L,
        maxDelayMs: Long = 5000L,
        task: suspend () -> T
    ): T? = withContext(networkIO) {
        var currentDelay = initialDelayMs
        repeat(maxAttempts) { attempt ->
            try {
                return@withContext task()
            } catch (e: Exception) {
                android.util.Log.w("PerformanceDispatcher", "Attempt ${attempt + 1}/$maxAttempts failed: ${e.message}")
                if (attempt < maxAttempts - 1) {
                    kotlinx.coroutines.delay(currentDelay)
                    currentDelay = (currentDelay * 2).coerceAtMost(maxDelayMs)
                }
            }
        }
        null
    }
    
    /**
     * Execute a task with timeout protection
     */
    suspend fun <T> withTimeout(
        timeoutMs: Long,
        task: suspend () -> T
    ): T? = withTimeoutOrNull(timeoutMs) {
        withContext(networkIO) { task() }
    }
    
    /**
     * Batch fetch with automatic chunking and parallel execution
     * Ideal for fetching content from multiple sources
     */
    suspend fun <T, R> batchFetch(
        items: List<T>,
        chunkSize: Int = 4,
        fetchFn: suspend (T) -> R?
    ): List<R> = supervisorScope {
        val results = mutableListOf<R>()
        items.chunked(chunkSize).forEach { chunk ->
            val chunkResults = chunk.map { item ->
                async(networkIO) {
                    try {
                        fetchFn(item)
                    } catch (e: Exception) {
                        null
                    }
                }
            }.awaitAll().filterNotNull()
            results.addAll(chunkResults)
        }
        results
    }
    
    /**
     * Race multiple tasks and return the first successful result
     * Useful for fallback strategies
     */
    suspend fun <T> race(
        vararg tasks: suspend () -> T?,
        timeoutMs: Long = 10_000L
    ): T? = supervisorScope {
        val deferreds = tasks.map { task ->
            async(networkIO) {
                withTimeoutOrNull(timeoutMs) {
                    try {
                        task()
                    } catch (e: Exception) {
                        null
                    }
                }
            }
        }
        
        // Wait for first non-null result
        for (deferred in deferreds) {
            val result = deferred.await()
            if (result != null) {
                // Cancel remaining tasks
                deferreds.forEach { it.cancel() }
                return@supervisorScope result
            }
        }
        null
    }
    
    /**
     * Cleanup resources when app is destroyed
     */
    fun shutdown() {
        supervisorJob.cancel()
        networkExecutor.shutdown()
    }
}

/**
 * Extension function for parallel list processing
 */
suspend fun <T, R> List<T>.parallelMap(
    concurrency: Int = 6,
    transform: suspend (T) -> R?
): List<R> = PerformanceDispatcher.parallelMap(this, concurrency, transform)

/**
 * Extension function for batch fetching
 */
suspend fun <T, R> List<T>.batchFetch(
    chunkSize: Int = 4,
    fetchFn: suspend (T) -> R?
): List<R> = PerformanceDispatcher.batchFetch(this, chunkSize, fetchFn)
