package io.kaeawc.photocrop.crop

import com.nhaarman.mockito_kotlin.*
import io.kaeawc.photocrop.db.Photo
import io.kaeawc.photocrop.db.PhotoRepository
import org.junit.Test

import org.junit.Assert.*
import org.junit.Before
import org.mockito.Mock
import org.mockito.MockitoAnnotations

class CropPresenterSpec {

    @Mock lateinit var view: CropPresenter.View
    @Mock lateinit var interactor: PhotoRepository
    @Mock lateinit var presenter: CropPresenter
    @Mock lateinit var photo: Photo

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        whenever(presenter.interactor).thenReturn(interactor)
        whenever(presenter.onCreate(any())).thenCallRealMethod()
        whenever(presenter.onResume(any())).thenCallRealMethod()
        whenever(presenter.onPause()).thenCallRealMethod()
        whenever(presenter.onStop()).thenCallRealMethod()
    }

    @Test
    fun `set view weak ref on create`() {
        assertNull(presenter.weakView)
        presenter.onCreate(view)
        assertNotNull(presenter.weakView)
    }

    @Test
    fun `show photo on resume`() {
        whenever(interactor.getPhoto(eq(0))).thenReturn(photo)
        presenter.onCreate(view)
        presenter.onResume(0)
        verify(view, times(1)).showPhoto(eq(photo))
        verify(view, never()).showPlaceholder()
    }

    @Test
    fun `show placeholder on resume`() {
        whenever(interactor.getPhoto(eq(0))).thenReturn(null)
        presenter.onCreate(view)
        presenter.onResume(0)
        verify(view, times(1)).showPlaceholder()
        verify(view, never()).showPhoto(any())
    }

    @Test
    fun `clear view reference on pause`() {
        presenter.onCreate(view)
        presenter.onResume(0)
        presenter.onPause()
        assertNotNull(presenter.weakView)
        assertNull(presenter.weakView?.get())
    }

    @Test
    fun `unset view reference on pause`() {
        presenter.onCreate(view)
        presenter.onStop()
        assertNull(presenter.weakView)
        assertNull(presenter.weakView?.get())
    }
}
