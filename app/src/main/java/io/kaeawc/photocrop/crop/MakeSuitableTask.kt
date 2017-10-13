package io.kaeawc.photocrop.crop

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.AsyncTask
import java.io.FileNotFoundException
import java.lang.ref.WeakReference
import io.kaeawc.photocrop.crop.UriExtensions.getBitmap

open class MakeSuitableTask(
        context: Context,
        val uri: Uri,
        val targetWidth: Int,
        val targetHeight: Int,
        val listener: SuitableDrawableListener) : AsyncTask<Void, Void, Drawable>() {

    var context: WeakReference<Context>? = WeakReference(context)

    private var rawWidth: Int = 0
    private var rawHeight: Int = 0
    private var error: Exception? = null

    override fun doInBackground(vararg params: Void): Drawable? {
        val options = BitmapFactory.Options()
        options.inSampleSize = 1

        options.inJustDecodeBounds = true
        val context = context?.get() ?: return null

        try {
            BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri), null, options)
            rawWidth = options.outWidth
            rawHeight = options.outHeight
            val bitmap = uri.getBitmap(context, targetWidth, targetHeight) ?: return null
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

    override fun onPostExecute(drawable: Drawable) {
        when (error) {
            null -> listener.onSuccess(drawable, rawWidth, rawHeight)
            else -> listener.onError(error)
        }
    }

    interface SuitableDrawableListener {
        fun onSuccess(drawable: Drawable, rawWidth: Int, rawHeight: Int)
        fun onError(ex: Exception?)
    }
}
