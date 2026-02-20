package com.flow.youtube.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.navigation.NavController
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
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
                            navController.navigate("shorts?startVideoId=$deeplinkVideoId")
                        } else {
                            navController.navigate("player/$deeplinkVideoId")
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

@Composable
fun OfflineMonitor(
    context: Context,
    navController: NavController,
    snackbarHostState: SnackbarHostState,
    currentRoute: State<String>
) {
    LaunchedEffect(Unit) {
        while (true) {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            
            if (!hasInternet) {
                val route = currentRoute.value
                val isSafeRoute = route == "downloads" || 
                                  route.startsWith("player") || 
                                  route.startsWith("musicPlayer") ||
                                  route == "settings"
                
                if (!isSafeRoute) {
                    val result = snackbarHostState.showSnackbar(
                        message = "No internet connection found",
                        actionLabel = "Downloads",
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                         navController.navigate("downloads") {
                            launchSingleTop = true
                        }
                    }
                }
            }
            delay(10000) // Check every 10 seconds
        }
    }
}