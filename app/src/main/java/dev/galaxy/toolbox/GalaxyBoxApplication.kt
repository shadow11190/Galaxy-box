package dev.galaxy.toolbox

import android.app.Application
import android.content.Context
import dev.galaxy.toolbox.ui.AppText
import rikka.shizuku.ShizukuProvider

class GalaxyBoxApplication : Application() {
    override fun attachBaseContext(base: Context) {
        ShizukuProvider.disableAutomaticSuiInitialization()
        super.attachBaseContext(base)
    }
    override fun onCreate() {
        super.onCreate()
        AppText.install(this)
    }
}
