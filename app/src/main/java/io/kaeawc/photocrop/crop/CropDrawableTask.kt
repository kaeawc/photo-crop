package io.kaeawc.photocrop.crop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

class CropDrawableTask(
        context: Context,
        drawable: Drawable,
        mMinimumRatio: Float,
        mMaximumRatio: Float,
        actualRight: Int,
        actualLeft: Int,
        actualBottom: Int,
        actualTop: Int,
        widthSpec: Int,
        heightSpec: Int,
        callback: CropView.BitmapCallback) : CropBitmapTask<Drawable>(
        context,
        drawable,
        mMinimumRatio,
        mMaximumRatio,
        actualRight,
        actualLeft,
        actualBottom,
        actualTop,
        widthSpec,
        heightSpec,
        callback) {

    override fun getBitmap(context: Context, resource: Drawable, targetWidth: Int, targetHeight: Int): Bitmap? =
            (resource as? BitmapDrawable)?.bitmap
}
