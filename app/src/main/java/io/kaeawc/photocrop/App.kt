package io.kaeawc.photocrop

import android.app.Application
import okhttp3.OkHttpClient
import timber.log.Timber
import javax.inject.Inject

class App : Application() {

    @Inject lateinit var okhttp: OkHttpClient

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        Timber.i("Started app")
        AppComponent.init(this).inject(this)
        GlideProvider.registerComponents(this, okhttp)
    }
}
