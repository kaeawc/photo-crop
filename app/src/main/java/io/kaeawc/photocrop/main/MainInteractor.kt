package io.kaeawc.photocrop.main

import io.kaeawc.photocrop.db.Photo

open class MainInteractor {

    open fun getPhotos(): List<Photo> = listOf(Photo(
            url = "http://via.placeholder.com/400x300",
            width = 400,
            height = 300,
            position = 0))

}
