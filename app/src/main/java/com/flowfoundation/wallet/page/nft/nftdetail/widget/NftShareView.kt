package com.flowfoundation.wallet.page.nft.nftdetail.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.palette.graphics.Palette
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.WidgetShareNftBinding
import com.flowfoundation.wallet.manager.config.NftCollectionConfig
import com.flowfoundation.wallet.network.model.Nft
import com.flowfoundation.wallet.network.model.UserInfoData
import com.flowfoundation.wallet.page.nft.nftlist.getNFTCover
import com.flowfoundation.wallet.page.nft.nftlist.name
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.loadAvatar
import com.flowfoundation.wallet.utils.toQRBitmap
import com.flowfoundation.wallet.utils.uiScope
import jp.wasabeef.glide.transformations.BlurTransformation

@SuppressLint("ViewConstructor")
class NftShareView(
    context: Context,
    private val userInfo: UserInfoData,
    private val nft: Nft,
) : FrameLayout(context) {
    lateinit var onReady: (view: NftShareView) -> Unit

    val binding = WidgetShareNftBinding.inflate(LayoutInflater.from(context))

    init {
        addView(binding.root)
    }

    fun setup(onReady: (view: NftShareView) -> Unit) {
        this.onReady = onReady
        setupUserInfo()
        setupNft()
    }

    private fun setupNft() {
        val config = NftCollectionConfig.get(nft.collectionAddress, nft.contractName()) ?: return
        with(binding) {
            nftNameView.text = nft.name()
            Glide.with(nftCollectionIconView).load(config.logo()).transform(RoundedCorners(20
                .dp2px().toInt())).into(nftCollectionIconView)
            nftCollectionNameView.text = config.name

            Glide.with(backgroundImage).load(nft.getNFTCover())
                .transform(BlurTransformation(15, 30))
                .into(backgroundImage)

            qrcodeView.setImageBitmap("https://lilico.app/".toQRBitmap(300, 300))

            loadNftCover()
        }
    }

    private fun loadNftCover() {
        Glide.with(binding.nftCoverView)
            .asBitmap()
            .load(nft.getNFTCover())
            .transform(RoundedCorners(12.dp2px().toInt()))
            .into(object : SimpleTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    ioScope {
                        val coverRatio = "${resource.width}:${resource.height}"
                        val color = Palette.from(resource).generate().getDominantColor(R.color.text_sub.res2color())
                        uiScope {
                            updatePageColor(color, coverRatio)
                            binding.nftCoverView.setImageBitmap(resource)
                            requestLayout()
                            post { onReady.invoke(this@NftShareView) }
                        }
                    }
                }
            })
    }

    private fun updatePageColor(color: Int, coverRatio: String) {
        with(binding) {
            with(nftCoverView.layoutParams as ConstraintLayout.LayoutParams) {
                dimensionRatio = coverRatio
                nftCoverView.layoutParams = this
            }

            cardDividerView.backgroundTintList = ColorStateList.valueOf(color)

            userInfoBackground.backgroundTintList = ColorStateList.valueOf(color)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupUserInfo() {
        with(binding) {
            nicknameView.text = userInfo.nickname
            usernameView.text = "@${userInfo.username}"
            avatarView.loadAvatar(userInfo.avatar, transformation = RoundedCorners(24.dp2px().toInt()))
        }
    }
}