package com.flowfoundation.wallet.utils

import android.content.Context
import android.os.Build
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.flowfoundation.wallet.page.main.widget.NetworkPopupListView
import com.lxj.xpopup.core.AttachPopupView
import com.lxj.xpopup.interfaces.OnSelectListener
import com.flowfoundation.wallet.widgets.popup.PopupListView
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.assertj.core.api.Assertions.assertThat

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P])
class PopupMenuUtilsTest {

    private lateinit var mockView: View
    private lateinit var mockContext: Context
    private lateinit var mockSelectListener: OnSelectListener

    @Before
    fun setup() {
        mockContext = ApplicationProvider.getApplicationContext()
        mockView = mock()
        mockSelectListener = mock()
        whenever(mockView.context).thenReturn(mockContext)
    }

    @Test
    fun `test popupMenu creates AttachPopupView with default parameters`() {
        val items = listOf(PopupListView.ItemData("Test Item"))
        val popup = popupMenu(mockView, items = items, selectListener = mockSelectListener)
        
        assertThat(popup).isInstanceOf(AttachPopupView::class.java)
    }

    @Test
    fun `test popupMenu with custom context`() {
        val customContext = mock<Context>()
        val items = listOf(PopupListView.ItemData("Test Item"))
        
        val popup = popupMenu(
            mockView,
            context = customContext,
            items = items,
            selectListener = mockSelectListener
        )
        
        assertThat(popup).isInstanceOf(AttachPopupView::class.java)
    }

    @Test
    fun `test popupMenu with custom offsets`() {
        val items = listOf(PopupListView.ItemData("Test Item"))
        val offsetX = 10
        val offsetY = 20
        
        val popup = popupMenu(
            mockView,
            items = items,
            selectListener = mockSelectListener,
            offsetX = offsetX,
            offsetY = offsetY
        )
        
        assertThat(popup).isInstanceOf(AttachPopupView::class.java)
    }

    @Test
    fun `test popupMenu with dialog mode`() {
        val items = listOf(PopupListView.ItemData("Test Item"))
        
        val popup = popupMenu(
            mockView,
            items = items,
            selectListener = mockSelectListener,
            isDialogMode = true
        )
        
        assertThat(popup).isInstanceOf(AttachPopupView::class.java)
    }

    @Test
    fun `test networkPopupMenu creates AttachPopupView with default parameters`() {
        val items = listOf(NetworkPopupListView.ItemData("Test Network"))
        val popup = networkPopupMenu(mockView, items = items, selectListener = mockSelectListener)
        
        assertThat(popup).isInstanceOf(AttachPopupView::class.java)
    }

    @Test
    fun `test networkPopupMenu with custom context`() {
        val customContext = mock<Context>()
        val items = listOf(NetworkPopupListView.ItemData("Test Network"))
        
        val popup = networkPopupMenu(
            mockView,
            context = customContext,
            items = items,
            selectListener = mockSelectListener
        )
        
        assertThat(popup).isInstanceOf(AttachPopupView::class.java)
    }

    @Test
    fun `test networkPopupMenu with custom offsets`() {
        val items = listOf(NetworkPopupListView.ItemData("Test Network"))
        val offsetX = 10
        val offsetY = 20
        
        val popup = networkPopupMenu(
            mockView,
            items = items,
            selectListener = mockSelectListener,
            offsetX = offsetX,
            offsetY = offsetY
        )
        
        assertThat(popup).isInstanceOf(AttachPopupView::class.java)
    }

    @Test
    fun `test networkPopupMenu with dialog mode`() {
        val items = listOf(NetworkPopupListView.ItemData("Test Network"))
        
        val popup = networkPopupMenu(
            mockView,
            items = items,
            selectListener = mockSelectListener,
            isDialogMode = true
        )
        
        assertThat(popup).isInstanceOf(AttachPopupView::class.java)
    }
} 