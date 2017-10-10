package io.kaeawc.photocrop.main

import io.kaeawc.photocrop.db.Photo

open class MainInteractor {

    open fun getPhotos(): List<Photo> = emptyList()

}
