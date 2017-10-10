package io.kaeawc.photocrop.crop

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import io.kaeawc.photocrop.R
import io.kaeawc.photocrop.db.Photo

class CropActivity : AppCompatActivity(), CropPresenter.View {

    companion object {
        const val POSITION = "position"
    }

    private val presenter = CropPresenter()
    var position = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop)
        position = intent.getIntExtra(POSITION, -1)
        presenter.onCreate(this)
    }

    override fun onResume() {
        super.onResume()
        when {
            position >= 0 -> presenter.onResume(position)
            else -> finish()
        }
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
    }

    override fun showPlaceholder() {
        if (isFinishing) return
    }
}
