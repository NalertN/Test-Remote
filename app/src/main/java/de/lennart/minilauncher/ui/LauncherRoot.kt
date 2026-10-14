package de.lennart.minilauncher.ui

import android.Manifest
import android.appwidget.AppWidgetProviderInfo
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.lennart.minilauncher.LauncherViewModel
import de.lennart.minilauncher.Screen

@Composable
fun LauncherRoot(viewModel: LauncherViewModel, onBindWidget: (AppWidgetProviderInfo) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Ask for calendar access once; if denied, the agenda simply stays hidden.
    var calendarAsked by rememberSaveable { mutableStateOf(false) }
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.startWatchingCalendar()
    }
    LaunchedEffect(Unit) {
        if (!calendarAsked && !viewModel.hasCalendarPermission()) {
            calendarAsked = true
            calendarPermission.launch(Manifest.permission.READ_CALENDAR)
        }
    }

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
                // The app list slides in from the left and the agenda from the right, matching the swipes.
                fun offset(screen: Screen): ((Int) -> Int)? = when (screen) {
                    Screen.Drawer -> { width -> -width / 5 }
                    Screen.Agenda -> { width -> width / 5 }
                    else -> null
                }
                val enter = fadeIn(tween(220)) +
                    (offset(targetState)?.let { slideInHorizontally(tween(220), it) } ?: EnterTransition.None)
                val exit = fadeOut(tween(160)) +
                    (offset(initialState)?.let { slideOutHorizontally(tween(160), it) } ?: ExitTransition.None)
                enter togetherWith exit
            },
            label = "screen",
        ) { screen ->
            when (screen) {
                Screen.Home -> HomeScreen(
                    favorites = state.favorites,
                    agenda = viewModel.agenda.takeUnless { state.agendaOnOwnPage },
                    onEventClick = viewModel::openEvent,
                    widgetId = state.widgetId,
                    onWidgetLongPress = { viewModel.widgetMenuOpen = true },
                    onLaunch = viewModel::launch,
                    onAppLongPress = { viewModel.menuApp = it },
                    onOpenDrawer = viewModel::openDrawer,
                    onOpenAgenda = viewModel::openAgenda.takeIf { state.agendaOnOwnPage && viewModel.agenda != null },
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
                Screen.Agenda -> AgendaScreen(
                    days = viewModel.agenda.orEmpty(),
                    onEventClick = viewModel::openEvent,
                    onClose = viewModel::goHome,
                )
                Screen.Settings -> SettingsScreen(
                    favorites = state.favorites,
                    hiddenApps = state.hiddenApps,
                    hasWidget = state.widgetId != null,
                    onChooseWidget = viewModel::openWidgetPicker,
                    onRemoveWidget = viewModel::removeWidget,
                    calendarAllowed = viewModel.hasCalendarPermission(),
                    onAllowCalendar = viewModel::openCalendarPermissionSettings,
                    agendaOnOwnPage = state.agendaOnOwnPage,
                    onAgendaOnOwnPageChange = viewModel::setAgendaOnOwnPage,
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

        if (viewModel.widgetMenuOpen) {
            WidgetActionsDialog(
                onReplace = viewModel::openWidgetPicker,
                onRemove = viewModel::removeWidget,
                onDismiss = { viewModel.widgetMenuOpen = false },
            )
        }

        viewModel.widgetOptions?.let { options ->
            WidgetPickerDialog(options, onPick = onBindWidget, onDismiss = viewModel::closeWidgetPicker)
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
