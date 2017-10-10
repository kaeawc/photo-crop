package io.kaeawc.photocrop.main

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import io.kaeawc.photocrop.R
import io.kaeawc.photocrop.db.Photo

open class PhotoAdapter(open val data: List<Photo>) : RecyclerView.Adapter<PhotoViewHolder>() {
    override fun getItemCount(): Int = 50

    override fun onBindViewHolder(holder: PhotoViewHolder?, position: Int) {
        if (holder !is PhotoViewHolder) return
        holder.onBind(data[position % data.size])
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val inflator = LayoutInflater.from(parent.context)
        val view = inflator.inflate(R.layout.photo_item, parent, false)
        return PhotoViewHolder(view)
    }
}