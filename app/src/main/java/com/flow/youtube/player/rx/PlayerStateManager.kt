package com.flow.youtube.player.rx

import androidx.media3.common.PlaybackException
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import java.util.concurrent.TimeUnit

/**
 * Reactive state manager for complex player state management.
 * Based on NewPipe's RxJava3 patterns for handling player events.
 */
class PlayerStateManager {

    private val disposables = CompositeDisposable()

    // State subjects
    private val playerStateSubject = BehaviorSubject.createDefault(PlayerReactiveState())
    private val errorSubject = PublishSubject.create<PlaybackException>()
    private val bufferingSubject = PublishSubject.create<Boolean>()
    private val progressSubject = PublishSubject.create<ProgressUpdate>()

    // Public observables
    val playerState: Observable<PlayerReactiveState> = playerStateSubject.hide()
    val errors: Observable<PlaybackException> = errorSubject.hide()
    val buffering: Observable<Boolean> = bufferingSubject.hide()
    val progress: Observable<ProgressUpdate> = progressSubject.hide()

    /**
     * Updates the player state reactively.
     */
    fun updateState(updater: (PlayerReactiveState) -> PlayerReactiveState) {
        val currentState = playerStateSubject.value ?: PlayerReactiveState()
        val newState = updater(currentState)
        playerStateSubject.onNext(newState)
    }

    /**
     * Emits an error event.
     */
    fun emitError(error: PlaybackException) {
        errorSubject.onNext(error)
    }

    /**
     * Emits a buffering state change.
     */
    fun emitBuffering(isBuffering: Boolean) {
        bufferingSubject.onNext(isBuffering)
    }

    /**
     * Emits a progress update.
     */
    fun emitProgress(currentMs: Long, durationMs: Long, bufferedMs: Long) {
        progressSubject.onNext(ProgressUpdate(currentMs, durationMs, bufferedMs))
    }

    /**
     * Creates an observable that emits state changes with debouncing.
     */
    fun observeStateChanges(debounceMs: Long = 100): Observable<PlayerReactiveState> {
        return playerState
            .debounce(debounceMs, TimeUnit.MILLISECONDS)
            .distinctUntilChanged()
    }

    /**
     * Creates an observable that combines multiple state indicators.
     */
    fun observePlaybackStatus(): Observable<PlaybackStatus> {
        return Observable.combineLatest(
            playerState.map { it.isPlaying },
            buffering.map { it },
            playerState.map { it.error != null }
        ) { isPlaying, isBuffering, hasError ->
            when {
                hasError -> PlaybackStatus.ERROR
                isBuffering -> PlaybackStatus.BUFFERING
                isPlaying -> PlaybackStatus.PLAYING
                else -> PlaybackStatus.PAUSED
            }
        }.distinctUntilChanged()
    }

    /**
     * Creates an observable for error recovery attempts.
     */
    fun observeErrorRecovery(): Observable<RecoveryEvent> {
        return errors
            .flatMap { error ->
                // Attempt recovery based on error type
                when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> {
                        // Network errors - retry with exponential backoff
                        Observable.timer(1, TimeUnit.SECONDS)
                            .map { RecoveryEvent.RETRY }
                            .onErrorReturn { RecoveryEvent.FAILED }
                    }
                    PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                        // Live stream lag - seek to live
                        Observable.just(RecoveryEvent.SEEK_TO_LIVE)
                    }
                    else -> {
                        // Other errors - mark as unrecoverable
                        Observable.just(RecoveryEvent.UNRECOVERABLE)
                    }
                }
            }
    }

    /**
     * Creates an observable that monitors buffering performance.
     */
    fun observeBufferingPerformance(): Observable<BufferingStats> {
        return buffering
            .switchMap { isBuffering ->
                if (isBuffering) {
                    Observable.timer(1, TimeUnit.SECONDS)
                        .repeat()
                        .takeUntil(buffering.filter { !it })
                        .map { BufferingStats(System.currentTimeMillis(), true) }
                } else {
                    Observable.just(BufferingStats(System.currentTimeMillis(), false))
                }
            }
    }

    /**
     * Adds a disposable to be managed by this state manager.
     */
    fun addDisposable(disposable: Disposable) {
        disposables.add(disposable)
    }

    /**
     * Creates a Single that completes when a specific state condition is met.
     */
    fun awaitState(predicate: (PlayerReactiveState) -> Boolean): Single<PlayerReactiveState> {
        return playerState
            .filter(predicate)
            .firstOrError()
    }

    /**
     * Creates an observable that emits when the player is ready for playback.
     */
    fun observeReadyForPlayback(): Observable<Boolean> {
        return playerState
            .map { it.isPrepared && it.error == null }
            .distinctUntilChanged()
    }

    /**
     * Disposes all managed resources.
     */
    fun dispose() {
        disposables.dispose()
        playerStateSubject.onComplete()
        errorSubject.onComplete()
        bufferingSubject.onComplete()
        progressSubject.onComplete()
    }
}

/**
 * Reactive player state data class.
 */
data class PlayerReactiveState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isPrepared: Boolean = false,
    val currentPosition: Long = 0,
    val duration: Long = 0,
    val bufferedPosition: Long = 0,
    val error: PlaybackException? = null,
    val videoId: String? = null,
    val recoveryAttempted: Boolean = false
)

/**
 * Progress update data class.
 */
data class ProgressUpdate(
    val currentMs: Long,
    val durationMs: Long,
    val bufferedMs: Long
)

/**
 * Playback status enum.
 */
enum class PlaybackStatus {
    PLAYING, PAUSED, BUFFERING, ERROR
}

/**
 * Recovery event enum.
 */
enum class RecoveryEvent {
    RETRY, SEEK_TO_LIVE, UNRECOVERABLE, FAILED
}

/**
 * Buffering statistics.
 */
data class BufferingStats(
    val timestamp: Long,
    val isBuffering: Boolean
)