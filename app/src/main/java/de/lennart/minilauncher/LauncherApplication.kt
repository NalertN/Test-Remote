package de.lennart.minilauncher

import android.app.Application
import de.lennart.minilauncher.data.AppRepository
import de.lennart.minilauncher.data.SettingsRepository

class LauncherApplication : Application() {
    lateinit var appRepository: AppRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
        appRepository = AppRepository(this)
        settingsRepository = SettingsRepository(this)
    }
}
