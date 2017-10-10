package io.kaeawc.photocrop

import dagger.Component
import io.kaeawc.photocrop.crop.CropActivity
import io.kaeawc.photocrop.main.MainActivity
import javax.inject.Singleton

@Singleton
@Component(modules = [AppModule::class])
interface AppComponent {

    fun inject(app: App)
    fun inject(mainActivity: MainActivity)
    fun inject(cropActivity: CropActivity)

    companion object {
        fun init(app: App): AppComponent =
                DaggerAppComponent.builder()
                        .appModule(AppModule(app))
                        .build()
    }
}
