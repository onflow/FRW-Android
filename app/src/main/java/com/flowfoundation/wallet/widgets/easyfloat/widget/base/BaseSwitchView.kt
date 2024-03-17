package com.flowfoundation.wallet.widgets.easyfloat.widget.base

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.RelativeLayout
import com.flowfoundation.wallet.widgets.easyfloat.interfaces.OnTouchRangeListener

/**
 * @author: liuzhenfeng
 * @date: 2020/10/25  11:08
 * @Package: com.gravity22.music.song.lyric.widget.easyfloat.widget.switch
 * @Description:
 */
abstract class BaseSwitchView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr) {

    abstract fun setTouchRangeListener(event: MotionEvent, listener: OnTouchRangeListener? = null)

}