package io.github.aedev.flow.data.local

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retryWhen
import java.io.IOException
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

private const val SAFE_DATA_STORE_TAG = "SafeDataStore"
private const val READ_RETRY_ATTEMPTS = 3
private const val READ_RETRY_DELAY_MS = 100L

fun safePreferencesDataStore(name: String): ReadOnlyProperty<Context, DataStore<Preferences>> {
    val delegate =
        preferencesDataStore(
            name = name,
            corruptionHandler =
                ReplaceFileCorruptionHandler { corruption ->
                    Log.w(SAFE_DATA_STORE_TAG, "Resetting corrupted preferences DataStore: $name", corruption)
                    emptyPreferences()
                },
        )
    var wrapped: DataStore<Preferences>? = null
    return object : ReadOnlyProperty<Context, DataStore<Preferences>> {
        override fun getValue(
            thisRef: Context,
            property: KProperty<*>,
        ): DataStore<Preferences> =
            wrapped ?: synchronized(this) {
                wrapped ?: ReadSafeDataStore(name, delegate.getValue(thisRef, property)).also { wrapped = it }
            }
    }
}

internal class ReadSafeDataStore(
    private val name: String,
    private val delegate: DataStore<Preferences>,
    private val retryDelayMs: Long = READ_RETRY_DELAY_MS,
) : DataStore<Preferences> {
    // A failed read must not crash the collector. Retrying first keeps observers subscribed to
    // later writes; only a read that keeps failing falls back to defaults and ends the stream.
    override val data: Flow<Preferences> =
        delegate.data
            .retryWhen { cause, attempt ->
                val retry = cause is IOException && attempt < READ_RETRY_ATTEMPTS
                if (retry) delay(retryDelayMs * (attempt + 1))
                retry
            }.catch { cause ->
                if (cause !is IOException) throw cause
                Log.w(SAFE_DATA_STORE_TAG, "Reading preferences DataStore failed, using defaults: $name", cause)
                emit(emptyPreferences())
            }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = delegate.updateData(transform)
}
