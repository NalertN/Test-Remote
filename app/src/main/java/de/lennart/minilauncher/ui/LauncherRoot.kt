package de.lennart.minilauncher.ui

import android.appwidget.AppWidgetProviderInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
fun LauncherRoot(viewModel: LauncherViewModel, onBindWidget: (AppWidgetProviderInfo, page: Int) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // On the home screen Back does nothing; elsewhere it returns home.
    BackHandler { viewModel.goHome() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val crash = viewModel.crashReport
        if (crash != null) {
            CrashScreen(crash, onDismiss = viewModel::dismissCrashReport)
            return@Box
        }

        AnimatedContent(
            targetState = viewModel.screen,
            transitionSpec = {
                // The second page slides in from the left, matching the swipe right that opens it;
                // the app list rises from the bottom, matching the swipe up.
                fun enter(screen: Screen): EnterTransition = when (screen) {
                    Screen.Second -> slideInHorizontally(tween(220)) { -it / 5 }
                    Screen.Drawer -> slideInVertically(tween(220)) { it / 5 }
                    else -> EnterTransition.None
                }
                fun exit(screen: Screen): ExitTransition = when (screen) {
                    Screen.Second -> slideOutHorizontally(tween(160)) { -it / 5 }
                    Screen.Drawer -> slideOutVertically(tween(160)) { it / 5 }
                    else -> ExitTransition.None
                }
                (fadeIn(tween(220)) + enter(targetState)) togetherWith (fadeOut(tween(160)) + exit(initialState))
            },
            label = "screen",
        ) { screen ->
            when (screen) {
                Screen.Home, Screen.Second -> {
                    val page = if (screen == Screen.Home) 0 else 1
                    FavoritesPage(
                        page = page,
                        favorites = state.favorites[page],
                        widgetId = state.widgetIds[page],
                        onLaunch = viewModel::launch,
                        onAppLongPress = { viewModel.menuApp = it },
                        onWidgetLongPress = {
                            if (state.widgetIds[page] == null) viewModel.openWidgetPicker(page)
                            else viewModel.widgetMenuPage = page
                        },
                        onSwipeUp = viewModel::openDrawer,
                        onSwipeRight = viewModel::openSecondPage.takeIf { page == 0 },
                        onSwipeLeft = viewModel::goHome.takeIf { page == 1 },
                        onOpenSettings = viewModel::openSettings,
                        onStartActivity = viewModel::startActivity,
                    )
                }
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
                    widgetIds = state.widgetIds,
                    onChooseWidget = viewModel::openWidgetPicker,
                    onRemoveWidget = viewModel::removeWidget,
                    onMoveFavorite = viewModel::moveFavorite,
                    onRemoveFavorite = viewModel::toggleFavorite,
                    onUnhide = { viewModel.setHidden(it, false) },
                    onStartActivity = viewModel::startActivity,
                    onClose = viewModel::goHome,
                )
            }
        }

        viewModel.menuApp?.let { app ->
            AppActionsDialog(
                app = app,
                isFavorite = state.favoriteKeys.map { app.key in it },
                isHidden = state.hiddenApps.any { it.key == app.key },
                onDismiss = { viewModel.menuApp = null },
                onToggleFavorite = { page -> viewModel.toggleFavorite(page, app) },
                onRename = { viewModel.renameApp = app },
                onToggleHidden = { hidden -> viewModel.setHidden(app, hidden) },
                onAppInfo = { viewModel.openAppInfo(app) },
                onUninstall = { viewModel.uninstall(app) },
            )
        }

        viewModel.widgetMenuPage?.let { page ->
            WidgetActionsDialog(
                page = page,
                onReplace = { viewModel.openWidgetPicker(page) },
                onRemove = { viewModel.removeWidget(page) },
                onDismiss = { viewModel.widgetMenuPage = null },
            )
        }

        val pickerPage = viewModel.widgetPickerPage
        val options = viewModel.widgetOptions
        if (pickerPage != null && options != null) {
            WidgetPickerDialog(
                options = options,
                onPick = { info ->
                    viewModel.closeWidgetPicker()
                    onBindWidget(info, pickerPage)
                },
                onDismiss = viewModel::closeWidgetPicker,
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
