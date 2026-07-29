package io.github.aedev.flow.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import io.github.aedev.flow.R
import io.github.aedev.flow.utils.NetworkConnectivityObserver
import kotlinx.coroutines.delay

@Composable
fun HandleDeepLinks(
    deeplinkVideoId: String?,
    isShort: Boolean,
    navController: NavController,
    onDeeplinkConsumed: () -> Unit
) {
    LaunchedEffect(deeplinkVideoId, isShort) {
        if (deeplinkVideoId != null) {
            val maxAttempts = 30
            var navigated = false
            for (attempt in 1..maxAttempts) {
                delay(100L)
                try {
                    if (navController.currentDestination != null) {
                        if (isShort) {
                            navController.navigate("shorts?startVideoId=$deeplinkVideoId") {
                                launchSingleTop = true
                            }
                        } else {
                            navController.navigate("player/$deeplinkVideoId") {
                                launchSingleTop = true
                            }
                        }
                        navigated = true
                        break
                    }
                } catch (e: Exception) {
                    android.util.Log.w(
                        "HandleDeepLinks",
                        "Navigation attempt $attempt failed for $deeplinkVideoId: ${e.message}"
                    )
                }
            }
            if (!navigated) {
                android.util.Log.e(
                    "HandleDeepLinks",
                    "Navigation failed after $maxAttempts attempts for: $deeplinkVideoId"
                )
            }
            onDeeplinkConsumed()
        }
    }
}

private const val OFFLINE_NOTICE_DELAY_MS = 3_000L

@Composable
fun OfflineMonitor(
    context: Context,
    navController: NavController,
    snackbarHostState: SnackbarHostState,
    currentRoute: State<String>
) {
    val connectivity = remember(context) { NetworkConnectivityObserver(context) }
    val isConnected by remember(connectivity) { connectivity.observeConnectivity() }
        .collectAsStateWithLifecycle(initialValue = true)
    val route = currentRoute.value

    LaunchedEffect(isConnected, route) {
        if (isConnected) return@LaunchedEffect

        val isSafeRoute = route == "downloads" ||
                          route.startsWith("player") ||
                          route.startsWith("musicPlayer") ||
                          route == "settings"
        if (isSafeRoute) return@LaunchedEffect

        delay(OFFLINE_NOTICE_DELAY_MS)

        val result = snackbarHostState.showSnackbar(
            message = context.getString(R.string.error_no_internet_found),
            actionLabel = context.getString(R.string.downloads_title),
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) {
            navController.navigate("downloads") {
                launchSingleTop = true
            }
        }
    }
}