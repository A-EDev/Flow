package io.github.aedev.flow.ui.screens.music

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.models.YTItem
import io.github.aedev.flow.innertube.pages.BrowseResult
import io.github.aedev.flow.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class YouTubeBrowseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val browseId: String = savedStateHandle.get<String>("browseId") ?: ""
    private val params: String? = savedStateHandle.get<String>("params")
    
    private val _uiState = MutableStateFlow(YouTubeBrowseUiState())
    val uiState: StateFlow<YouTubeBrowseUiState> = _uiState.asStateFlow()
    
    init {
        if (browseId.isNotEmpty()) {
            loadBrowseContent()
        }
    }
    
    private fun loadBrowseContent() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                YouTube.browse(browseId, params)
                    .onSuccess { result ->
                        Log.d("YouTubeBrowse", "Loaded ${result.items.size} sections for browseId: $browseId")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            title = result.title,
                            sections = result.items
                        )
                    }
                    .onFailure { error ->
                        Log.e("YouTubeBrowse", "Failed to load browse content", error)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = error.message ?: context.getString(R.string.error_failed_to_load_content)
                        )
                    }
            } catch (e: Exception) {
                Log.e("YouTubeBrowse", "Exception loading browse content", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: context.getString(R.string.error_generic_hint)
                )
            }
        }
    }
    
    fun retry() {
        if (browseId.isNotEmpty()) {
            loadBrowseContent()
        }
    }
}

data class YouTubeBrowseUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val title: String? = null,
    val sections: List<BrowseResult.Item> = emptyList()
)
