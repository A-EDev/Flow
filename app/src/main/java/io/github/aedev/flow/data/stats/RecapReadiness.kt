package io.github.aedev.flow.data.stats

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.recommendation.music.MusicBrainEngine
import kotlinx.coroutines.flow.first
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

private val Context.recapPreferences by preferencesDataStore(name = "flow_recap")
private val LAST_SHOWN = stringPreferencesKey("last_shown_month")

/**
 * Whether a month or a year has just closed with enough in it to be worth a recap, checked only when a screen
 * that offers it opens. No job, alarm or notification ever runs for it.
 */
@Singleton
class RecapReadiness
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val videoStats: VideoStatsRecorder,
        private val musicBrain: MusicBrainEngine,
    ) {
        /**
         * The recap waiting to be offered: in January last year's, when it held enough; otherwise the
         * month that just closed. Null when nothing is waiting or it was already opened or dismissed.
         */
        suspend fun readyPeriod(now: YearMonth = YearMonth.now()): RecapPeriod? {
            val shown = context.recapPreferences.data.first()[LAST_SHOWN]
            if (now.monthValue == JANUARY) {
                val year = now.year - 1
                val activity = (1..MONTHS_PER_YEAR).sumOf { activityIn(LedgerTime.monthKey(YearMonth.of(year, it))) }
                if (shown != year.toString() && activity >= MIN_YEAR_ACTIVITY) return RecapPeriod.Year(year)
            }
            val closed = now.minusMonths(1)
            val key = LedgerTime.monthKey(closed)
            if (shown == key) return null
            return RecapPeriod.Month(closed).takeIf { activityIn(key) >= MIN_ACTIVITY }
        }

        suspend fun markShown(period: RecapPeriod) {
            val key =
                when (period) {
                    is RecapPeriod.Month -> LedgerTime.monthKey(period.month)
                    is RecapPeriod.Year -> period.year.toString()
                    RecapPeriod.AllTime -> return
                }
            context.recapPreferences.edit { it[LAST_SHOWN] = key }
        }

        private suspend fun activityIn(monthKey: String): Int = videoStats.monthViews(monthKey) + musicBrain.monthPlays(monthKey)

        private companion object {
            const val MIN_ACTIVITY = 20
            const val MIN_YEAR_ACTIVITY = 100
            const val JANUARY = 1
            const val MONTHS_PER_YEAR = 12
        }
    }
