package de.lennart.minilauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.lennart.minilauncher.LauncherViewModel
import de.lennart.minilauncher.Screen

@Composable
fun LauncherRoot(viewModel: LauncherViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // On the home screen Back does nothing; elsewhere it returns home.
    BackHandler { viewModel.goHome() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val crash = viewModel.crashReport
        if (crash != null) {
            CrashScreen(crash, onDismiss = viewModel::dismissCrashReport)
            return@Box
        }

        when (viewModel.screen) {
            Screen.Home -> HomeScreen(
                favorites = state.favorites,
                onLaunch = viewModel::launch,
                onAppLongPress = { viewModel.menuApp = it },
                onOpenDrawer = viewModel::openDrawer,
                onOpenSettings = viewModel::openSettings,
                onStartActivity = viewModel::startActivity,
            )
            Screen.Drawer -> AppDrawer(
                apps = viewModel.filteredApps(state.apps, viewModel.query),
                query = viewModel.query,
                onQueryChange = { viewModel.query = it },
                onLaunch = viewModel::launch,
                onAppLongPress = { viewModel.menuApp = it },
                onClose = viewModel::goHome,
            )
            Screen.Settings -> SettingsScreen(
                favorites = state.favorites,
                hiddenApps = state.hiddenApps,
                onMoveFavorite = viewModel::moveFavorite,
                onRemoveFavorite = viewModel::toggleFavorite,
                onUnhide = { viewModel.setHidden(it, false) },
                onStartActivity = viewModel::startActivity,
                onClose = viewModel::goHome,
            )
        }

        viewModel.menuApp?.let { app ->
            AppActionsDialog(
                app = app,
                isFavorite = app.key in state.favoriteKeys,
                isHidden = state.hiddenApps.any { it.key == app.key },
                onDismiss = { viewModel.menuApp = null },
                onToggleFavorite = { viewModel.toggleFavorite(app) },
                onRename = { viewModel.renameApp = app },
                onToggleHidden = { hidden -> viewModel.setHidden(app, hidden) },
                onAppInfo = { viewModel.openAppInfo(app) },
                onUninstall = { viewModel.uninstall(app) },
            )
        }

        viewModel.renameApp?.let { app ->
            RenameDialog(
                app = app,
                onDismiss = { viewModel.renameApp = null },
                onRename = { name ->
                    viewModel.rename(app, name)
                    viewModel.renameApp = null
                },
            )
        }
    }
}
