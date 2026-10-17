package __APP_ID__

import android.app.Application
import __PKG__.core.common.config.AppConfig
import __PKG__.di.initKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin(AppConfig(baseUrl = BuildConfig.BASE_URL, isDebug = BuildConfig.DEBUG)) {
            androidContext(this@MainApplication)
            if (BuildConfig.DEBUG) androidLogger()
        }
    }
}
