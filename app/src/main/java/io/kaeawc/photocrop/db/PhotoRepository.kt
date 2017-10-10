package io.kaeawc.photocrop.db

open class PhotoRepository {

    open fun getPhotos(): List<Photo> = (0..9).map {
        step ->
        val position = step * 3
        val smallSize = 300 + 100 * step
        val largeSize = smallSize + 100

        return listOf(Photo(
                url = "http://via.placeholder.com/${smallSize}x$smallSize",
                width = smallSize,
                height = smallSize,
                position = position
        ), Photo(
                url = "http://via.placeholder.com/${largeSize}x$smallSize",
                width = largeSize,
                height = smallSize,
                position = position + 1
        ), Photo(
                url = "http://via.placeholder.com/${smallSize}x$largeSize",
                width = smallSize,
                height = largeSize,
                position = position + 2
        ))
    }.flatten()

    open fun getPhoto(position: Int): Photo? {
        val step = position / 3
        val smallSize = 300 + 100 * step
        val largeSize = smallSize + 100
        val offset = position % 3
        val (width, height) = when (offset) {
            0 -> smallSize to smallSize
            1 -> largeSize to smallSize
            else -> smallSize to largeSize
        }

        return Photo(
                url = "http://via.placeholder.com/${width}x$height",
                width = width,
                height = height,
                position = position + offset
        )
    }
}
