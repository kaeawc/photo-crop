package io.kaeawc.photocrop

import java.lang.ref.WeakReference

class MainPresenter {

    var weakView: WeakReference<View>? = null

    fun onCreate(view: View) {
        weakView = WeakReference(view)
    }

    fun onResume() {

    }

    fun onPause() {
        weakView?.clear()
    }

    fun onStop() {
        weakView = null
    }

    interface View {

        fun showPhotos(photos: List<Photo>)
    }
}
