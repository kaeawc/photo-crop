package io.kaeawc.photocrop

import java.lang.ref.WeakReference

open class MainPresenter {

    var weakView: WeakReference<View>? = null

    val interactor = MainInteractor()

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
