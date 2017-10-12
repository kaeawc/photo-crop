package io.kaeawc.photocrop.crop

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import io.kaeawc.photocrop.crop.UriExtensions.getBitmap

class CropImageUriTask(
        context: Context,
        uri: Uri,
        mMinimumRatio: Float,
        mMaximumRatio: Float,
        actualRight: Int,
        actualLeft: Int,
        actualBottom: Int,
        actualTop: Int,
        widthSpec: Int,
        heightSpec: Int,
        callback: CropView.CropBitmapListener) : CropBitmapTask<Uri>(
        context,
        uri,
        mMinimumRatio,
        mMaximumRatio,
        actualRight,
        actualLeft,
        actualBottom,
        actualTop,
        widthSpec,
        heightSpec,
        callback) {

    override fun getBitmap(context: Context, resource: Uri, targetWidth: Int, targetHeight: Int): Bitmap? =
            resource.getBitmap(context, targetWidth, targetHeight)
}
