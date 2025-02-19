package com.flowfoundation.wallet.page.main.presenter

import androidx.viewpager.widget.ViewPager
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.ActivityMainBinding
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.page.main.MainActivity
import com.flowfoundation.wallet.page.main.activeColor
import com.flowfoundation.wallet.page.main.adapter.MainPageAdapter
import com.flowfoundation.wallet.page.main.model.MainContentModel
import com.flowfoundation.wallet.page.main.setSvgDrawable

class MainContentPresenter(
    private val activity: MainActivity,
    private val binding: ActivityMainBinding,
) : BasePresenter<MainContentModel> {

    private val menuId by lazy {
        listOf(
            R.id.bottom_navigation_home,
            R.id.bottom_navigation_nft,
            R.id.bottom_navigation_explore,
            R.id.bottom_navigation_profile,
            R.id.bottom_navigation_activity,
        )
    }

    init {
        setupListener()
        binding.viewPager.offscreenPageLimit = 4
        binding.viewPager.adapter = MainPageAdapter(activity)
    }

    override fun bind(model: MainContentModel) {
        model.onChangeTab?.let { binding.viewPager.setCurrentItem(it.index, false) }
    }

    private fun setupListener() {
        binding.viewPager.setOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                if (isTabSwitching) return

                isTabSwitching = true
                if (binding.navigationView.selectedItemId != menuId[position]) {
                    binding.navigationView.selectedItemId = menuId[position]
                }
                isTabSwitching = false
            }
        })
        binding.navigationView.setOnNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.bottom_navigation_home -> onNavigationItemSelected(0)
                R.id.bottom_navigation_nft -> onNavigationItemSelected(1)
                R.id.bottom_navigation_explore -> onNavigationItemSelected(2)
                R.id.bottom_navigation_profile -> onNavigationItemSelected(3)
                R.id.bottom_navigation_activity -> onNavigationItemSelected(4)
            }
            true
        }

        binding.navigationView.post {
            val menu = binding.navigationView.menu
            if (WalletManager.isChildAccountSelected()) {
                menu.findItem(R.id.bottom_navigation_explore).setVisible(false)
            }
            with(menu) {
                (0 until size()).forEach { binding.navigationView.setSvgDrawable(it) }
            }
        }
    }

    private var isTabSwitching = false

    private fun onNavigationItemSelected(index: Int) {
        if (isTabSwitching) return

        val currentIndex = binding.viewPager.currentItem
        if (currentIndex == index) return

        isTabSwitching = true
        binding.viewPager.post {
            binding.viewPager.setCurrentItem(index, false)
        }
        isTabSwitching = false

        binding.navigationView.updateIndicatorColor(binding.navigationView.activeColor(index))
        binding.navigationView.setSvgDrawable(currentIndex)
        binding.navigationView.setSvgDrawable(index)
    }
}