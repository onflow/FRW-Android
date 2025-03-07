package com.flowfoundation.wallet.page.explore.subpage

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseAdapter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.DialogDappListBinding
import com.flowfoundation.wallet.databinding.ItemDappCategoryBinding
import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.page.explore.ExploreViewModel
import com.flowfoundation.wallet.page.explore.adapter.ExploreDAppAdapter
import com.flowfoundation.wallet.page.explore.model.DAppModel
import com.flowfoundation.wallet.page.explore.model.DAppTagModel
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.widgets.itemdecoration.ColorDividerItemDecoration

class DAppListDialog : BottomSheetDialogFragment() {

    private lateinit var binding: DialogDappListBinding

    private lateinit var viewModel: DAppListViewModel

    private val tagAdapter by lazy { ExploreDAppTagsAdapter() }
    private val adapter by lazy { ExploreDAppAdapter() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogDappListBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.root.requestFocus()

        viewModel = ViewModelProvider(this)[DAppListViewModel::class.java].apply {
            dAppsLiveData.observe(viewLifecycleOwner) { adapter.setNewDiffData(it) }
            dAppTagsLiveData.observe(viewLifecycleOwner) { tagAdapter.setNewDiffData(it) }
        }

        with(binding.dappTabs) {
            adapter = tagAdapter
            layoutManager = FlexboxLayoutManager(requireContext()).apply {
                alignItems = AlignItems.STRETCH
                flexDirection = FlexDirection.ROW
                flexWrap = FlexWrap.WRAP
            }
        }
        with(binding.recyclerView) {
            adapter = this@DAppListDialog.adapter
            addItemDecoration(ColorDividerItemDecoration(Color.TRANSPARENT, 12.dp2px().toInt(), LinearLayoutManager.VERTICAL))
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        }

        tagAdapter.setViewModel(viewModel)

        ViewModelProvider(requireActivity())[ExploreViewModel::class.java].apply {
            onDAppClickLiveData.observe(this@DAppListDialog) { if (isResumed) dismiss() }
        }

        binding.closeButton.setOnClickListener { dismiss() }
        binding.titleView.setText(R.string.dapps)
        viewModel.load()
    }

    companion object {

        fun show(fragmentManager: FragmentManager) {
            DAppListDialog().showNow(fragmentManager, "")
        }
    }
}

private class ExploreDAppTagsAdapter : BaseAdapter<DAppTagModel>(diffCallback) {
    private lateinit var viewModel: DAppListViewModel

    fun setViewModel(viewModel: DAppListViewModel) {
        this.viewModel = viewModel
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return ExploreDAppTagItemPresenter(parent.inflate(R.layout.item_dapp_category))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as ExploreDAppTagItemPresenter).bind(getData()[position])
    }

    private inner class ExploreDAppTagItemPresenter(
        private val view: View,
    ) : BaseViewHolder(view), BasePresenter<DAppTagModel> {
        private val binding by lazy { ItemDappCategoryBinding.bind(view) }

        override fun bind(model: DAppTagModel) {
            with(binding) {
                textView.text = model.category
                textView.setTextColor(if (model.isSelected) R.color.text.res2color() else R.color.text_sub.res2color())

                root.strokeColor = if (model.isSelected) R.color.violet1.res2color() else R.color.bg_icon.res2color()
                view.setOnClickListener {
                    viewModel.selectDappTag(model.category)
                }
            }
        }
    }
}

private val diffCallback = object : DiffUtil.ItemCallback<DAppTagModel>() {
    override fun areItemsTheSame(oldItem: DAppTagModel, newItem: DAppTagModel): Boolean {
        return oldItem.category == newItem.category
    }

    override fun areContentsTheSame(oldItem: DAppTagModel, newItem: DAppTagModel): Boolean {
        return oldItem == newItem
    }
}

class DAppListViewModel : ViewModel() {

    val dAppTagsLiveData = MutableLiveData<List<DAppTagModel>>()
    val dAppsLiveData = MutableLiveData<List<DAppModel>>()

    private var dappTag: String? = null

    fun selectDappTag(tag: String) {
        if (dappTag == tag) return
        dappTag = tag
        load()
    }

    fun load() {
        val json = Firebase.remoteConfig.getString("dapp")
        val dApps = Gson().fromJson<List<DAppModel>>(json, object : TypeToken<List<DAppModel>>() {}.type).filter {
            if (isTestnet()) !it.testnetUrl.isNullOrBlank() else !it.url.isNullOrBlank()
        }
        val tags = dApps.map { it.category }.distinct()
            .map { DAppTagModel(it, dappTag == it) }.toMutableList().apply {
                add(0, DAppTagModel(R.string.all.res2String(), isShowAllDapps()))
            }
        dAppTagsLiveData.postValue(tags)
        dAppsLiveData.postValue(if (isShowAllDapps()) dApps else dApps.filter { it.category == dappTag })
    }

    private fun isShowAllDapps() = dappTag == null || dappTag == R.string.all.res2String()
}