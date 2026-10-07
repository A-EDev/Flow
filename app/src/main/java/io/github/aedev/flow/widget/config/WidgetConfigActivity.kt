package io.github.aedev.flow.widget.config

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.glance.appwidget.GlanceAppWidgetManager
import dagger.hilt.android.AndroidEntryPoint
import io.github.aedev.flow.data.local.AppFontPreferences
import io.github.aedev.flow.data.local.LocalDataManager
import io.github.aedev.flow.ui.screens.widgets.PlaylistConfigScreen
import io.github.aedev.flow.ui.screens.widgets.QuickActionsConfigScreen
import io.github.aedev.flow.ui.startup.FlowTheme
import io.github.aedev.flow.ui.startup.themeSettings
import io.github.aedev.flow.widget.playlist.PlaylistWidgetReceiver
import io.github.aedev.flow.widget.quickactions.QuickActionsWidgetReceiver
import javax.inject.Inject

/**
 * The launcher opens this when a configurable widget is placed or long-pressed to reconfigure.
 * Every choice is saved as it is made, so leaving always reports success.
 */
@AndroidEntryPoint
class WidgetConfigActivity : ComponentActivity() {
    @Inject
    lateinit var appFontPreferences: AppFontPreferences

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, result())
        val provider =
            AppWidgetManager
                .getInstance(this)
                .getAppWidgetInfo(appWidgetId)
                ?.provider
                ?.className
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID || provider == null) {
            finish()
            return
        }
        val glanceId = GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId)
        val theme = LocalDataManager(applicationContext).themeSettings(appFontPreferences)
        setContent {
            val settings = theme.collectAsState(initial = null).value ?: return@setContent
            FlowTheme(settings) {
                when (provider) {
                    QuickActionsWidgetReceiver::class.java.name -> QuickActionsConfigScreen(glanceId, onDone = ::done)
                    PlaylistWidgetReceiver::class.java.name -> PlaylistConfigScreen(glanceId, onDone = ::done)
                    else -> done()
                }
            }
        }
    }

    private fun done() {
        setResult(RESULT_OK, result())
        finish()
    }

    private fun result() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}
