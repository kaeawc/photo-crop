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
        val mMinimumRatio: Float,
        val mMaximumRatio: Float,
        val actualRight: Int,
        val actualLeft: Int,
        val actualBottom: Int,
        val actualTop: Int,
        val widthSpec: Int,
        val heightSpec: Int,
        val callback: CropView.BitmapCallback) : AsyncTask<Void, Void, Bitmap>() {

    var context: WeakReference<Context>? = WeakReference(context)

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

                    if (specRatio == actualRatio) {
                        targetWidth = widthSize
                        targetHeight = heightSize
                    } else if (specRatio > actualRatio) {
                        targetHeight = heightSize
                        targetWidth = (targetHeight * actualRatio).toInt()
                    } else {
                        targetWidth = widthSize
                        targetHeight = (targetWidth / actualRatio).toInt()
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

        val rawBitmap = getBitmap(context, resource, targetWidth, targetHeight) ?: return null
        return rawBitmap.cropAndResize(actualLeft, actualTop, actualRight, actualBottom, targetWidth, targetHeight) ?: return null
    }

    override fun onPostExecute(bitmap: Bitmap) {
        callback.onBitmapReady(bitmap)
    }

    abstract fun getBitmap(context: Context, resource: T, targetWidth: Int, targetHeight: Int): Bitmap?
}
