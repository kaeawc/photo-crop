package io.kaeawc.photocrop.main

import android.support.constraint.ConstraintLayout
import android.support.constraint.ConstraintSet
import android.support.v7.widget.RecyclerView
import android.view.View
import android.widget.ImageView
import io.kaeawc.photocrop.GlideApp
import io.kaeawc.photocrop.R
import io.kaeawc.photocrop.db.Photo
import java.lang.ref.WeakReference

open class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val imageView: ImageView? = itemView.findViewById(R.id.photo)
    private var weakView: WeakReference<PhotoView>? = null

    init {
        imageView?.let { GlideApp.with(itemView).clear(it) }
    }

    open fun onBind(photo: Photo, view: PhotoView) {
        weakView = WeakReference(view)
        val photoView = imageView ?: return
        GlideApp.with(itemView).load(photo.url).into(photoView)
        val parent = photoView.parent as? ConstraintLayout ?: return
        val constraints = ConstraintSet()
        constraints.constrainWidth(R.id.photo, ConstraintLayout.LayoutParams.MATCH_CONSTRAINT)
        constraints.constrainHeight(R.id.photo, ConstraintLayout.LayoutParams.MATCH_CONSTRAINT)
        constraints.setDimensionRatio(R.id.photo, (photo.width / photo.height.toFloat()).toString())
        val topMargin = itemView.context.resources.getDimension(R.dimen.photo_list_margin).toInt()
        constraints.connect(R.id.photo, ConstraintSet.TOP, R.id.photo_constraint, ConstraintSet.TOP, topMargin)
        constraints.connect(R.id.photo, ConstraintSet.LEFT, R.id.photo_constraint, ConstraintSet.LEFT, 0)
        constraints.connect(R.id.photo, ConstraintSet.RIGHT, R.id.photo_constraint, ConstraintSet.RIGHT, 0)
        constraints.applyTo(parent)

        photoView.setOnClickListener {
            weakView?.get()?.onPhotoTapped(photo)
        }
    }

    interface PhotoView {
        fun onPhotoTapped(photo: Photo)
    }
}
