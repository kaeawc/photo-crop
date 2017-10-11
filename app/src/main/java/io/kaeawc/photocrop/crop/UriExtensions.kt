package io.kaeawc.photocrop.crop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.support.media.ExifInterface
import timber.log.Timber
import java.io.IOException

object UriExtensions {

    fun Uri.getBitmap(context: Context, targetWidth: Int, targetHeight: Int): Bitmap? {

        val options = BitmapFactory.Options()
        options.inSampleSize = 1

        options.inJustDecodeBounds = true

        return try {
            BitmapFactory.decodeStream(context.contentResolver.openInputStream(this), null, options)

            val rawWidth = options.outWidth
            val rawHeight = options.outHeight

            var resultWidth = rawWidth
            var resultHeight = rawHeight

            Runtime.getRuntime().gc()

            val totalMemory = Runtime.getRuntime().maxMemory()
            val allowedMemoryToUse = totalMemory / 8
            val maximumAreaPossibleAccordingToAvailableMemory = (allowedMemoryToUse / 4).toInt()

            val targetArea = Math.min(targetWidth * targetHeight * 4, maximumAreaPossibleAccordingToAvailableMemory)

            var resultArea = resultWidth * resultHeight

            while (resultArea > targetArea) {
                options.inSampleSize *= 2

                resultWidth = rawWidth / options.inSampleSize
                resultHeight = rawHeight / options.inSampleSize

                resultArea = resultWidth * resultHeight
            }

            options.inJustDecodeBounds = false

            getBitmapWithOptions(context, options)
        } catch (ex: Exception) {
            null
        }
    }

    fun Uri.getBitmapWithOptions(context: Context, options: BitmapFactory.Options): Bitmap? {

        var bitmap: Bitmap? = null

        while (true) {
            try {
                bitmap = BitmapFactory.decodeStream(context.contentResolver.openInputStream(this), null, options)
                break
            } catch (t: Throwable) {
                options.inSampleSize *= 2

                if (options.inSampleSize >= 1024) {
                    Timber.d("Failed to optimize RAM to receive Bitmap.")

                    break
                }
            }
        }

        if (bitmap != null) {
            val orientation = getRotation(context)

            if (orientation != 0) {
                val matrix = Matrix()
                matrix.postRotate(orientation.toFloat())

                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)
            }
        }

        return bitmap
    }

    fun Uri.matchesThisUri(other: Uri): Boolean = Uri.withAppendedPath(other, lastPathSegment) == this

    fun Uri.getRotation(context: Context): Int {
        if (matchesThisUri(MediaStore.Images.Media.EXTERNAL_CONTENT_URI) || matchesThisUri(MediaStore.Images.Media.INTERNAL_CONTENT_URI)) {
            val cursor = context.contentResolver.query(this, arrayOf(MediaStore.Images.Media.ORIENTATION), null, null, null)

            return if (cursor != null && cursor.count == 1) {
                cursor.moveToFirst()
                val orientation = cursor.getInt(0)
                cursor.close()
                orientation
            } else {
                Timber.w("Failed to get MediaStore image orientation.")
                cursor.close()
                0
            }
        }

        try {
            val ei: ExifInterface = if (Build.VERSION.SDK_INT >= 24) {
                ExifInterface(context.contentResolver.openInputStream(this))
            } else {
                ExifInterface(this.toString())
            }

            val orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)

            return when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                ExifInterface.ORIENTATION_NORMAL -> 0
                else -> 0
            }
        } catch (ex: IOException) {
            Timber.e(ex, "Failed to get image orientation from file.")
            return 0
        }
    }
}
