package com.flowfoundation.wallet.page.landing.utils

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.viewpager2.widget.ViewPager2
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit


class AutoScrollViewPager(
    private val viewPager: ViewPager2,
    private val interval: Long,
    lifecycle: Lifecycle
) : DefaultLifecycleObserver {

    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var scheduledFuture: ScheduledFuture<*>? = null

    init {
        lifecycle.addObserver(this)
    }

    override fun onResume(owner: LifecycleOwner) {
        startAutoScroll()
    }

    override fun onPause(owner: LifecycleOwner) {
        stopAutoScroll()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        scheduledFuture?.cancel(true)
        scheduler.shutdownNow()
    }

    private fun startAutoScroll() {
        stopAutoScroll()
        scheduledFuture = scheduler.scheduleWithFixedDelay({
            viewPager.post {
                val nextItem = viewPager.currentItem + 1
                if (nextItem < (viewPager.adapter?.itemCount ?: 0)) {
                    viewPager.setCurrentItem(nextItem, true)
                }
            }
        }, interval, interval, TimeUnit.MILLISECONDS)
    }

    fun stopAutoScroll() {
        scheduledFuture?.cancel(true)
    }
}