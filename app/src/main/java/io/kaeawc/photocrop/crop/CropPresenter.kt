package io.kaeawc.photocrop.crop

import io.kaeawc.photocrop.db.Photo
import io.kaeawc.photocrop.db.PhotoRepository
import java.lang.ref.WeakReference

open class CropPresenter {

    var weakView: WeakReference<View>? = null

    open val interactor = PhotoRepository()

    open fun onCreate(view: View) {
        weakView = WeakReference(view)
    }

    open fun onResume(position: Int) {
        val view = weakView?.get() ?: return
        val photo = interactor.getPhoto(position)
        when (photo) {
            null -> view.showPlaceholder()
            else -> view.showPhoto(photo)
        }
    }

    open fun onPause() {
        weakView?.clear()
    }

    open fun onStop() {
        weakView = null
    }

    interface View {
        fun showPhoto(photo: Photo)
        fun showPlaceholder()
    }
}
