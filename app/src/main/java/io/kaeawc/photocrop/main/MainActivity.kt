package io.kaeawc.photocrop.main

import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import io.kaeawc.photocrop.R
import io.kaeawc.photocrop.crop.CropActivity
import io.kaeawc.photocrop.db.Photo
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(), MainPresenter.View, PhotoViewHolder.PhotoView {

    private val presenter = MainPresenter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        presenter.onCreate(this)
    }

    override fun onResume() {
        super.onResume()
        presenter.onResume()
    }

    override fun onPause() {
        super.onPause()
        presenter.onPause()
    }

    override fun onStop() {
        super.onStop()
        presenter.onStop()
    }

    override fun showPhotos(photos: List<Photo>) {
        if (isFinishing) return
        photo_list.layoutManager = LinearLayoutManager(baseContext, LinearLayoutManager.VERTICAL, false)
        photo_list.adapter = PhotoAdapter(photos, this)
    }

    override fun onPhotoTapped(photo: Photo) {
        val intent = Intent(baseContext, CropActivity::class.java)
        intent.putExtra(CropActivity.URL, photo.url)
        intent.putExtra(CropActivity.WIDTH, photo.width)
        intent.putExtra(CropActivity.HEIGHT, photo.height)
        intent.putExtra(CropActivity.POSITION, photo.position)
        startActivity(intent)
    }
}
