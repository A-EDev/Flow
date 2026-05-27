package io.github.aedev.flow.ui.screens.personality

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.data.recommendation.FlowPersona
import io.github.aedev.flow.data.recommendation.UserBrain
import io.github.aedev.flow.data.repository.YouTubeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowPersonalityScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var brain by remember { mutableStateOf<UserBrain?>(null) }
    var persona by remember { mutableStateOf<FlowPersona?>(null) }
    var discoveryQueries by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showResetDialog by remember { mutableStateOf(false) }
    val channelNames = remember { mutableStateMapOf<String, String>() }

    suspend fun reloadBrain(initialize: Boolean = false) {
        if (initialize) FlowNeuroEngine.initialize(context)
        val snapshot = FlowNeuroEngine.getBrainSnapshot()
        brain = snapshot
        persona = FlowNeuroEngine.getPersona(snapshot)
        discoveryQueries = FlowNeuroEngine.generateDiscoveryQueries()
        isLoading = false
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val success = context.contentResolver.openOutputStream(uri)?.use { out ->
                FlowNeuroEngine.exportBrainToStream(out)
            } ?: false
            Toast.makeText(
                context,
                if (success) context.getString(R.string.profile_export_success) else context.getString(R.string.profile_export_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val success = context.contentResolver.openInputStream(uri)?.use { input ->
                FlowNeuroEngine.importBrainFromStream(context, input)
            } ?: false
            if (success) reloadBrain()
            Toast.makeText(
                context,
                if (success) context.getString(R.string.profile_import_success) else context.getString(R.string.profile_import_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        reloadBrain(initialize = true)
    }

    LaunchedEffect(brain?.channelScores) {
        val snapshot = brain ?: return@LaunchedEffect
        val idsToFetch = snapshot.channelScores.entries
            .sortedByDescending { it.value }
            .take(12)
            .map { it.key }
            .filter { it.isNotBlank() && !channelNames.containsKey(it) }

        if (idsToFetch.isEmpty()) return@LaunchedEffect

        val repository = YouTubeRepository.getInstance()
        val fetchedNames = withContext(Dispatchers.IO) {
            idsToFetch.mapNotNull { channelId ->
                runCatching {
                    repository.getChannelInfo(channelId)?.name?.let { channelId to it }
                }.getOrNull()
            }
        }
        fetchedNames.forEach { (channelId, name) -> channelNames[channelId] = name }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.btn_back)
                        )
                    }
                    Text(
                        text = stringResource(R.string.flow_control_center),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            isLoading = true
                            scope.launch { reloadBrain() }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.action_refresh)
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        val snapshot = brain
        if (isLoading || snapshot == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    strokeWidth = 3.dp
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 12.dp,
                end = 16.dp,
                bottom = 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "overview") {
                PersonalityOverviewSection(
                    brain = snapshot,
                    persona = persona
                )
            }
            item(key = "stats") {
                LearningStatsSection(brain = snapshot)
            }
            item(key = "taste") {
                TasteShapeSection(brain = snapshot)
            }
            item(key = "topics") {
                InterestWeightsSection(brain = snapshot)
            }
            item(key = "time") {
                TimePatternsSection(brain = snapshot)
            }
            item(key = "channels") {
                ChannelMemorySection(
                    brain = snapshot,
                    channelNames = channelNames
                )
            }
            item(key = "discovery") {
                DiscoveryStatusSection(
                    brain = snapshot,
                    queries = discoveryQueries
                )
            }
            if (snapshot.blockedTopics.isNotEmpty() || snapshot.blockedChannels.isNotEmpty()) {
                item(key = "blocked") {
                    BlockedContentSection(
                        brain = snapshot,
                        channelNames = channelNames,
                        onUnblockTopic = { topic ->
                            scope.launch {
                                FlowNeuroEngine.removeBlockedTopic(context, topic)
                                reloadBrain()
                            }
                        },
                        onUnblockChannel = { channelId ->
                            scope.launch {
                                FlowNeuroEngine.unblockChannel(context, channelId)
                                reloadBrain()
                            }
                        }
                    )
                }
            }
            item(key = "data") {
                ProfileDataSection(
                    onExport = {
                        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                        exportLauncher.launch("flow_brain_$timestamp.json")
                    },
                    onImport = {
                        importLauncher.launch(arrayOf("application/json", "text/plain"))
                    },
                    onReset = { showResetDialog = true }
                )
            }
        }
    }

    if (showResetDialog) {
        ResetProfileDialog(
            onDismiss = { showResetDialog = false },
            onConfirm = {
                scope.launch {
                    FlowNeuroEngine.resetBrain(context)
                    showResetDialog = false
                    reloadBrain()
                }
            }
        )
    }
}
