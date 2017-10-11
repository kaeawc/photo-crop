package io.kaeawc.photocrop.crop

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import io.kaeawc.photocrop.GlideApp
import io.kaeawc.photocrop.R
import io.kaeawc.photocrop.db.Photo
import kotlinx.android.synthetic.main.activity_crop.*

class CropActivity : AppCompatActivity(), CropPresenter.View {

    companion object {
        const val URL = "url"
        const val WIDTH = "width"
        const val HEIGHT = "height"
        const val POSITION = "position"
    }

    private val presenter = CropPresenter()
    var url = ""
    var width = -1
    var height = -1
    var position = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop)
        url = intent.getStringExtra(URL)
        width = intent.getIntExtra(WIDTH, -1)
        height = intent.getIntExtra(HEIGHT, -1)
        position = intent.getIntExtra(POSITION, -1)
        if (width < 0 || height < 0 || position < 0) return finish()
        if (url.isBlank()) return finish()
        presenter.onCreate(this, url, width, height, position)
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

    override fun showPhoto(photo: Photo) {
        if (isFinishing) return
        val cropView: CropView = photo_view ?: return
        GlideApp.with(this).load(photo.url).into(cropView)
    }

    override fun showPlaceholder() {
        if (isFinishing) return
    }
}
