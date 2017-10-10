package io.kaeawc.photocrop.crop

import io.kaeawc.photocrop.db.Photo
import io.kaeawc.photocrop.db.PhotoRepository
import java.lang.ref.WeakReference

open class CropPresenter {

    var weakView: WeakReference<View>? = null

    open val interactor = PhotoRepository()

    open var currentPhoto: Photo? = null

    open fun onCreate(view: View, url: String, width: Int, height: Int, position: Int) {
        weakView = WeakReference(view)
        currentPhoto = Photo(url, width, height, position)
    }

    open fun onResume() {
        val view = weakView?.get() ?: return
        val photo = currentPhoto
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
