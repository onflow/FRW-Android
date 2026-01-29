package com.flowfoundation.wallet.page.token.detail.presenter

import androidx.appcompat.app.AppCompatActivity
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.LayoutTokenDetailActivitiesBinding
import com.flowfoundation.wallet.manager.token.model.FungibleToken
import com.flowfoundation.wallet.page.token.detail.model.TokenDetailActivitiesModel
import com.flowfoundation.wallet.reactnative.ReactNativeActivity
import com.flowfoundation.wallet.reactnative.bridge.RNBridge
import com.flowfoundation.wallet.utils.extensions.setVisible


class TokenDetailActivitiesPresenter(
    private val activity: AppCompatActivity,
    private val binding: LayoutTokenDetailActivitiesBinding,
    private val token: FungibleToken,
) : BasePresenter<TokenDetailActivitiesModel> {

    init {
        // Hide the recycler view since we now use React Native for activity display
        binding.recyclerView.setVisible(false)
        // "More" button launches React Native Activity screen
        binding.activitiesMoreButton.setOnClickListener {
            ReactNativeActivity.launch(activity, RNBridge.ScreenType.ACTIVITY)
        }
    }

    override fun bind(model: TokenDetailActivitiesModel) {
        model.recordList?.let {
            // Show the section header if there are activities, but use RN for the list
            binding.root.setVisible(it.isNotEmpty())
        }
    }

}