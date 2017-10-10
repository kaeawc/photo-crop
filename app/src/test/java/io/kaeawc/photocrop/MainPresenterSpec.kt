package io.kaeawc.photocrop

import com.nhaarman.mockito_kotlin.*
import org.junit.Test

import org.junit.Assert.*
import org.junit.Before
import org.mockito.Mock
import org.mockito.MockitoAnnotations

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class MainPresenterSpec {

    @Mock lateinit var view: MainPresenter.View

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
    }

    @Test
    fun `set view weak ref on create`() {
        val presenter = MainPresenter()
        assertNull(presenter.weakView)
        presenter.onCreate(view)
        assertNotNull(presenter.weakView)
    }

    @Test
    fun `show title on resume`() {
        val presenter = MainPresenter()
        presenter.onCreate(view)
        presenter.onResume()
        verify(view, times(1)).showPhotos(any())
    }

    @Test
    fun `clear view reference on pause`() {
        val presenter = MainPresenter()
        presenter.onCreate(view)
        presenter.onResume()
        presenter.onPause()
        assertNotNull(presenter.weakView)
        assertNull(presenter.weakView?.get())
    }

    @Test
    fun `unset view reference on pause`() {
        val presenter = MainPresenter()
        presenter.onCreate(view)
        presenter.onStop()
        assertNull(presenter.weakView)
        assertNull(presenter.weakView?.get())
    }
}