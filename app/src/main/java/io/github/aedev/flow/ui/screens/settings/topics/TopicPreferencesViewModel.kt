package io.github.aedev.flow.ui.screens.settings.topics

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.data.recommendation.NeuroTopicCatalog
import io.github.aedev.flow.data.recommendation.TopicPreferencesRepository
import io.github.aedev.flow.ui.screens.settings.SettingsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class TopicPreferencesViewModel
    @Inject
    constructor(
        private val repository: TopicPreferencesRepository,
    ) : SettingsViewModel() {
        private val _interests = MutableStateFlow<Set<String>>(emptySet())
        val interests: StateFlow<Set<String>> = _interests.asStateFlow()

        private val _blocked = MutableStateFlow<Set<String>>(emptySet())
        val blocked: StateFlow<Set<String>> = _blocked.asStateFlow()

        /** Followed topics the catalogue does not list — the ones the user typed in. */
        val catalogueTopics: Set<String> = NeuroTopicCatalog.TOPIC_CATEGORIES.flatMap { it.topics }.toSet()

        init {
            refresh()
        }

        private fun refresh() {
            viewModelScope.launch {
                _interests.value = runCatching { repository.preferredTopics() }.getOrDefault(emptySet())
                _blocked.value = runCatching { repository.blockedTopics() }.getOrDefault(emptySet())
            }
        }

        fun toggleInterest(topic: String) =
            write {
                if (topic in _interests.value) repository.removePreferred(topic) else repository.addPreferred(topic)
                refresh()
            }

        fun addInterest(text: String) =
            write {
                val topic = text.trim()
                if (topic.isNotEmpty()) {
                    repository.addPreferred(topic)
                    refresh()
                }
            }

        fun removeInterest(topic: String) =
            write {
                repository.removePreferred(topic)
                refresh()
            }

        /** Blocked topics are matched without case, so they are stored lowercased like the engine does. */
        fun block(text: String) =
            write {
                val topic = text.trim().lowercase(Locale.getDefault())
                if (topic.isNotEmpty()) {
                    repository.addBlocked(topic)
                    refresh()
                }
            }

        fun unblock(topic: String) =
            write {
                repository.removeBlocked(topic)
                refresh()
            }
    }

/** Suggestions still worth offering: those not already blocked, compared the way blocking stores them. */
internal fun unblockedSuggestions(
    suggestions: List<String>,
    blocked: Set<String>,
    locale: Locale = Locale.getDefault(),
): List<String> {
    val blockedKeys = blocked.map { it.lowercase(locale) }.toSet()
    return suggestions.filter { it.lowercase(locale) !in blockedKeys }
}
