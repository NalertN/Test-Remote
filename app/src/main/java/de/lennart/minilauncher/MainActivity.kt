package de.lennart.minilauncher

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import de.lennart.minilauncher.ui.LauncherRoot
import de.lennart.minilauncher.ui.LauncherTheme
import de.lennart.minilauncher.widget.WidgetBinder

class MainActivity : ComponentActivity() {
    private val viewModel: LauncherViewModel by viewModels()
    private val widgetHost get() = (application as LauncherApplication).widgetHost
    private val widgetBinder = WidgetBinder(this, { widgetHost }, onBound = { page, id -> viewModel.setWidget(page, id) })

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        widgetBinder.restoreState(savedInstanceState)
        if (!widgetBinder.isBinding) viewModel.releaseOrphanedWidgets()
        setContent {
            LauncherTheme {
                LauncherRoot(
                    viewModel,
                    onBindWidget = { info, page -> widgetBinder.bind(info, page) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Pressing Home while the launcher is in front delivers a new MAIN/HOME intent.
        if (intent.action == Intent.ACTION_MAIN) viewModel.goHome()
    }

    override fun onStart() {
        super.onStart()
        viewModel.refreshApps()
        runCatching { widgetHost.startListening() }
    }

    override fun onStop() {
        super.onStop()
        runCatching { widgetHost.stopListening() }
        // Always come back to the home screen after leaving the launcher.
        viewModel.goHome()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        widgetBinder.saveState(outState)
    }

    @Deprecated("Widget configure activities can only be started via AppWidgetHost, which reports here.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!widgetBinder.onActivityResult(requestCode, resultCode)) {
            @Suppress("DEPRECATION")
            super.onActivityResult(requestCode, resultCode, data)
        }
    }
}
