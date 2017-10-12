package io.kaeawc.photocrop.crop

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

object BitmapExtensions {

    fun Bitmap.toDrawable(context: Context): Drawable = BitmapDrawable(context.resources, this)

    fun Bitmap.cropAndResize(
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
            width: Int,
            height: Int): Bitmap? {

        val options = BitmapFactory.Options()
        options.inSampleSize = 1

        val rawArea = (right - left) * (bottom - top)
        val targetArea = width * height * 4

        var resultArea = rawArea

        while (resultArea > targetArea) {
            options.inSampleSize *= 2
            resultArea = rawArea / (options.inSampleSize * options.inSampleSize)
        }

        if (options.inSampleSize > 1) {
            options.inSampleSize /= 2
        }

        val sampledLeft = left / options.inSampleSize
        val sampledTop = top / options.inSampleSize
        val sampledRight = right / options.inSampleSize
        val sampledBottom = bottom / options.inSampleSize
        val croppedWidth = sampledRight - sampledLeft
        val croppedHeight = sampledBottom - sampledTop

        val croppedBitmap = Bitmap.createBitmap(this, sampledLeft, sampledTop, croppedWidth, croppedHeight)
        if (croppedWidth <= width && croppedHeight <= height) {
            return croppedBitmap
        }

        val resizedBitmap = this.resize(width, height)
        croppedBitmap?.recycle()

        return resizedBitmap
    }

    private fun Bitmap.resize(targetWidth: Int, targetHeight: Int): Bitmap? {

        val resizedBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)

        val scaleX = targetWidth / width.toFloat()
        val scaleY = targetHeight / height.toFloat()
        val pivotX = 0f
        val pivotY = 0f

        val scaleMatrix = Matrix()
        scaleMatrix.setScale(scaleX, scaleY, pivotX, pivotY)

        val canvas = Canvas(resizedBitmap)
        canvas.matrix = scaleMatrix
        canvas.drawBitmap(this, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))

        this.recycle()

        return resizedBitmap
    }
}
