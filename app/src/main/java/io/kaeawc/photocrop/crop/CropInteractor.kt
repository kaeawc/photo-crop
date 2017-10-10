package io.kaeawc.photocrop.crop

import io.kaeawc.photocrop.db.Photo

open class CropInteractor {

    open fun getPhoto(): Photo? = Photo(
            url = "http://via.placeholder.com/400x300",
            width = 400,
            height = 300,
            position = 0)

}
