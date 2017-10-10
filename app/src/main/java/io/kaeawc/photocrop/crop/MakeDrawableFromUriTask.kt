package io.kaeawc.photocrop.crop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.support.media.ExifInterface
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.provider.MediaStore
import timber.log.Timber

import java.io.FileNotFoundException
import java.io.IOException
import java.lang.ref.WeakReference

open class MakeDrawableFromUriTask(
        context: Context,
        val uri: Uri,
        val targetWidth: Int,
        val targetHeight: Int) : AsyncTask<Void, Void, Drawable>() {

    var context: WeakReference<Context>? = WeakReference(context)

    protected var rawWidth: Int = 0
        private set
    protected var rawHeight: Int = 0
        private set

    override fun doInBackground(vararg params: Void): Drawable? {
        val options = BitmapFactory.Options()
        options.inSampleSize = 1

        options.inJustDecodeBounds = true
        val context = context?.get() ?: return null

        try {
            BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri), null, options)

            rawWidth = options.outWidth
            rawHeight = options.outHeight

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

            val bitmap = getBitmap(context, uri, options) ?: return null

            val beforeRatio = rawWidth.toFloat() / rawHeight.toFloat()
            val afterRatio = bitmap.width.toFloat() / bitmap.height.toFloat()

            if (beforeRatio < 1 && afterRatio > 1 || beforeRatio > 1 && afterRatio < 1) {
                val rawWidth = this.rawWidth
                this.rawWidth = rawHeight
                rawHeight = rawWidth
            }

            return BitmapDrawable(context.resources, bitmap)
        } catch (e: FileNotFoundException) {
            return null
        }

    }

    companion object {

        @JvmStatic fun getBitmap(context: Context, uri: Uri, options: BitmapFactory.Options): Bitmap? {
            var bitmap: Bitmap? = null

            while (true) {
                try {
                    bitmap = BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri), null, options)
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
                val orientation = getRotation(uri, context)

                if (orientation != 0) {
                    val matrix = Matrix()
                    matrix.postRotate(orientation.toFloat())

                    bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)
                }
            }

            return bitmap
        }

        private fun getRotation(uri: Uri, context: Context): Int {
            if (isUriMatching(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, uri) || isUriMatching(MediaStore.Images.Media.INTERNAL_CONTENT_URI, uri)) {
                val c = context.contentResolver.query(uri, arrayOf(MediaStore.Images.Media.ORIENTATION), null, null, null)

                return if (c != null && c.count == 1) {
                    c.moveToFirst()
                    val orientation = c.getInt(0)
                    c.close()
                    orientation
                } else {
                    Timber.w("Failed to get MediaStore image orientation.")
                    c.close()
                    0
                }
            }

            try {
                val ei: ExifInterface = if (Build.VERSION.SDK_INT >= 24) {
                    ExifInterface(context.contentResolver.openInputStream(uri))
                } else {
                    ExifInterface(uri.toString())
                }

                val orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)

                return when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    ExifInterface.ORIENTATION_NORMAL -> 0
                    else -> 0
                }
            } catch (e: IOException) {
                Timber.w("Failed to get image orientation from file.", e)

                return 0
            }

        }

        @JvmStatic fun resizeBitmap(bitmap: Bitmap, newWidth: Int, newHeight: Int): Bitmap {
            val resizedBitmap = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888)

            val scaleX = newWidth / bitmap.width.toFloat()
            val scaleY = newHeight / bitmap.height.toFloat()
            val pivotX = 0f
            val pivotY = 0f

            val scaleMatrix = Matrix()
            scaleMatrix.setScale(scaleX, scaleY, pivotX, pivotY)

            val canvas = Canvas(resizedBitmap)
            canvas.matrix = scaleMatrix
            canvas.drawBitmap(bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))

            return resizedBitmap
        }

        private fun isUriMatching(path: Uri, element: Uri): Boolean =
                Uri.withAppendedPath(path, element.lastPathSegment) == element
    }
}
