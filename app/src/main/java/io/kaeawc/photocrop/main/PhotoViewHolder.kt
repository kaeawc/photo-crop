package io.kaeawc.photocrop.main

import android.support.v7.widget.RecyclerView
import android.view.View
import android.widget.ImageView
import io.kaeawc.photocrop.GlideApp
import io.kaeawc.photocrop.R
import io.kaeawc.photocrop.db.Photo

open class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val imageView: ImageView? = itemView.findViewById(R.id.photo)

    init {
        imageView?.let { GlideApp.with(itemView).clear(it) }
    }

    open fun onBind(photo: Photo) {
        imageView?.let { GlideApp.with(itemView).load(photo.url).into(it) }
    }
}
