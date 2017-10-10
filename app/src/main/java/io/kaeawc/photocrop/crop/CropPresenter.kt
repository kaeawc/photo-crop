package io.kaeawc.photocrop.crop

import io.kaeawc.photocrop.db.Photo
import io.kaeawc.photocrop.main.MainInteractor
import java.lang.ref.WeakReference

open class CropPresenter {

    var weakView: WeakReference<View>? = null

    open val interactor = CropInteractor()

    open fun onCreate(view: View) {
        weakView = WeakReference(view)
    }

    open fun onResume() {
        weakView?.get()?.showPhoto(interactor.getPhoto())
    }

    open fun onPause() {
        weakView?.clear()
    }

    open fun onStop() {
        weakView = null
    }

    interface View {
        fun showPhoto(photo: Photo?)
    }
}
