package com.flowfoundation.wallet.page.profile.subpage.avatar.edit

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.children
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseAdapter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.network.model.Nft
import com.flowfoundation.wallet.page.nft.nftlist.cover
import com.flowfoundation.wallet.utils.findActivity
import com.flowfoundation.wallet.utils.loadAvatar

class AvatarListAdapter : BaseAdapter<Any>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return AvatarViewHolder(parent.inflate(R.layout.item_edit_avatar))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as AvatarViewHolder).bind(getItem(position))
    }
}

private class AvatarViewHolder(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<Any> {
    private val viewModel by lazy { ViewModelProvider(findActivity(view) as FragmentActivity)[EditAvatarViewModel::class.java] }

    private val imageView by lazy { view.findViewById<ImageView>(R.id.image_view) }

    private var model: Any? = null

    init {
        view.setOnClickListener {
            model?.let { viewModel.selectAvatar(it) }
            (view.parent as ViewGroup).children.forEach { child ->
                child.setBackgroundResource(if (child == view) R.drawable.bg_avatar_edit_item else R.color.transparent)
            }
        }
    }

    override fun bind(model: Any) {
        this.model = model
        if (model is Nft) {
//            Glide.with(imageView).load(model.cover()).placeholder(R.drawable.placeholder).into(imageView)
            imageView.loadAvatar(model.cover().orEmpty())
        } else if (model is String) {
            imageView.loadAvatar(model)
        }

        if (model == viewModel.selectedAvatarLiveData.value) {
            view.setBackgroundResource(R.drawable.bg_avatar_edit_item)
        } else {
            view.setBackgroundResource(R.color.transparent)
        }
    }
}