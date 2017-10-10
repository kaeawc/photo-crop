package io.kaeawc.photocrop.main

import io.kaeawc.photocrop.db.Photo
import java.lang.ref.WeakReference

open class MainPresenter {

    open var weakView: WeakReference<View>? = null
    open val interactor = MainInteractor()

    open fun onCreate(view: View) {
        weakView = WeakReference(view)
    }

    open fun onResume() {
        weakView?.get()?.showPhotos(interactor.getPhotos())
    }

    open fun onPause() {
        weakView?.clear()
    }

    open fun onStop() {
        weakView = null
    }

    interface View {
        fun showPhotos(photos: List<Photo>)
    }
}
