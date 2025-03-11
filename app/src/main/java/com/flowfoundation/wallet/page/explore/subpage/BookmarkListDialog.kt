package com.flowfoundation.wallet.page.explore.subpage

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.database.AppDataBase
import com.flowfoundation.wallet.database.Bookmark
import com.flowfoundation.wallet.databinding.DialogRecentHistoryBinding
import com.flowfoundation.wallet.page.explore.ExploreViewModel
import com.flowfoundation.wallet.page.explore.adapter.ExploreBookmarkManageAdapter
import com.flowfoundation.wallet.page.explore.model.BookmarkTitleModel
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.viewModelIOScope

class BookmarkListDialog : BottomSheetDialogFragment() {

    private lateinit var binding: DialogRecentHistoryBinding

    private lateinit var viewModel: BookmarkListViewModel

    private val adapter by lazy { ExploreBookmarkManageAdapter() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogRecentHistoryBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.root.requestFocus()
        with(binding.recyclerView) {
            adapter = this@BookmarkListDialog.adapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        }

        viewModel = ViewModelProvider(this)[BookmarkListViewModel::class.java].apply {
            bookmarkLiveData.observe(this@BookmarkListDialog) { adapter.setNewDiffData(it) }
            bindFragment(this@BookmarkListDialog)
            load()
        }

        ViewModelProvider(requireActivity())[ExploreViewModel::class.java].apply {
            onDAppClickLiveData.observe(this@BookmarkListDialog) { if (isResumed) dismiss() }
        }

        binding.closeButton.setOnClickListener { dismiss() }
        binding.titleView.setText(R.string.bookmark)
    }

    companion object {

        fun show(fragmentManager: FragmentManager) {
            BookmarkListDialog().showNow(fragmentManager, "")
        }
    }
}

class BookmarkListViewModel : ViewModel() {
    val bookmarkLiveData = MutableLiveData<List<Any>>()

    @SuppressLint("StaticFieldLeak")
    private lateinit var fragment: Fragment

    fun bindFragment(fragment: Fragment) {
        this.fragment = fragment
        registerObserve()
    }

    fun load() {
        viewModelIOScope(this) {
            dispatchData(AppDataBase.database().bookmarkDao().findAll(limit = 100))
        }
    }

    private fun registerObserve() {
        AppDataBase.database().bookmarkDao().findAllLive(limit = 100).observe(fragment.viewLifecycleOwner) { load() }
    }

    private fun dispatchData(data: List<Bookmark>) {
        val started = data.filter { it.isFavourite }
        val normal = data.filter { !it.isFavourite }

        val list = mutableListOf<Any>().apply {
            if (started.isNotEmpty()) {
                add(BookmarkTitleModel(R.drawable.ic_collection_star, R.string.favourite.res2String()))
            }
            addAll(started)

            add(BookmarkTitleModel(R.drawable.ic_bookmark_list, R.string.list.res2String()))
            addAll(normal)
        }

        bookmarkLiveData.postValue(list)
    }
}