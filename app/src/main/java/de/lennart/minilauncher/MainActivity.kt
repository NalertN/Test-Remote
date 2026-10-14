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

class MainActivity : ComponentActivity() {
    private val viewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            LauncherTheme {
                LauncherRoot(viewModel)
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
    }

    override fun onStop() {
        super.onStop()
        // Always come back to the home screen after leaving the launcher.
        viewModel.goHome()
    }
}
