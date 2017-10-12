package io.kaeawc.photocrop.crop

import android.content.Context
import android.graphics.Bitmap
import android.os.AsyncTask
import android.view.View
import java.lang.ref.WeakReference
import io.kaeawc.photocrop.crop.BitmapExtensions.cropAndResize

abstract class CropBitmapTask<T>(
        context: Context,
        val resource: T,
        private val mMinimumRatio: Float,
        private val mMaximumRatio: Float,
        private val actualRight: Int,
        private val actualLeft: Int,
        private val actualBottom: Int,
        private val actualTop: Int,
        private val widthSpec: Int,
        private val heightSpec: Int,
        private val callback: CropView.CropBitmapListener) : AsyncTask<Void, Void, Bitmap?>() {

    var context: WeakReference<Context>? = WeakReference(context)
    private var error: Exception? = null

    override fun doInBackground(vararg params: Void): Bitmap? {
        val context = context?.get() ?: return null
        val actualWidth = actualRight - actualLeft
        val actualHeight = actualBottom - actualTop
        var actualRatio = actualWidth.toFloat() / actualHeight.toFloat()

        if (actualRatio < mMinimumRatio) {
            actualRatio = mMinimumRatio
        }

        if (actualRatio > mMaximumRatio) {
            actualRatio = mMaximumRatio
        }

        val widthMode = View.MeasureSpec.getMode(widthSpec)
        val widthSize = View.MeasureSpec.getSize(widthSpec)
        val heightMode = View.MeasureSpec.getMode(heightSpec)
        val heightSize = View.MeasureSpec.getSize(heightSpec)

        var targetWidth = actualWidth
        var targetHeight = actualHeight

        when (widthMode) {
            View.MeasureSpec.EXACTLY -> {
                targetWidth = widthSize

                when (heightMode) {
                    View.MeasureSpec.EXACTLY -> targetHeight = heightSize
                    View.MeasureSpec.AT_MOST -> targetHeight = Math.min(heightSize, (targetWidth / actualRatio).toInt())
                    View.MeasureSpec.UNSPECIFIED -> targetHeight = (targetWidth / actualRatio).toInt()
                }
            }
            View.MeasureSpec.AT_MOST -> when (heightMode) {
                View.MeasureSpec.EXACTLY -> {
                    targetHeight = heightSize
                    targetWidth = Math.min(widthSize, (targetHeight * actualRatio).toInt())
                }
                View.MeasureSpec.AT_MOST -> if (actualWidth <= widthSize && actualHeight <= heightSize) {
                    targetWidth = actualWidth
                    targetHeight = actualHeight
                } else {
                    val specRatio = widthSize.toFloat() / heightSize.toFloat()

                    when {
                        specRatio == actualRatio -> {
                            targetWidth = widthSize
                            targetHeight = heightSize
                        }
                        specRatio > actualRatio -> {
                            targetHeight = heightSize
                            targetWidth = (targetHeight * actualRatio).toInt()
                        }
                        else -> {
                            targetWidth = widthSize
                            targetHeight = (targetWidth / actualRatio).toInt()
                        }
                    }
                }
                View.MeasureSpec.UNSPECIFIED -> if (actualWidth <= widthSize) {
                    targetWidth = actualWidth
                    targetHeight = actualHeight
                } else {
                    targetWidth = widthSize
                    targetHeight = (targetWidth / actualRatio).toInt()
                }
            }
            View.MeasureSpec.UNSPECIFIED -> when (heightMode) {
                View.MeasureSpec.EXACTLY -> {
                    targetHeight = heightSize
                    targetWidth = (targetHeight * actualRatio).toInt()
                }
                View.MeasureSpec.AT_MOST -> if (actualHeight <= heightSize) {
                    targetHeight = actualHeight
                    targetWidth = actualWidth
                } else {
                    targetHeight = heightSize
                    targetWidth = (targetHeight * actualRatio).toInt()
                }
                View.MeasureSpec.UNSPECIFIED -> {
                    targetWidth = actualWidth
                    targetHeight = actualHeight
                }
            }
        }

        return try {
            val rawBitmap = getBitmap(context, resource, actualWidth, actualHeight) ?: return null
            rawBitmap.cropAndResize(actualLeft, actualTop, actualRight, actualBottom, targetWidth, targetHeight)
        } catch (ex: Exception) {
            error = ex
            null
        }
    }

    override fun onPostExecute(bitmap: Bitmap?) {
        when (bitmap) {
            null -> callback.onError(error ?: Exception("Unexpected processing error"))
            else -> callback.onBitmapReady(bitmap)
        }
    }

    abstract fun getBitmap(context: Context, resource: T, targetWidth: Int, targetHeight: Int): Bitmap?
}
