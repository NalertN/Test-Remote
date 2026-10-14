package de.lennart.minilauncher

import android.app.Application
import de.lennart.minilauncher.data.AppRepository
import de.lennart.minilauncher.data.SettingsRepository
import de.lennart.minilauncher.widget.LauncherWidgetHost

class LauncherApplication : Application() {
    lateinit var appRepository: AppRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var widgetHost: LauncherWidgetHost
        private set

    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
        appRepository = AppRepository(this)
        settingsRepository = SettingsRepository(this)
        widgetHost = LauncherWidgetHost(this)
    }
}
