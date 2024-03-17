package com.flowfoundation.wallet.page.explore

import android.annotation.SuppressLint
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.database.AppDataBase
import com.flowfoundation.wallet.database.Bookmark
import com.flowfoundation.wallet.database.WebviewRecord
import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.page.explore.model.DAppModel
import com.flowfoundation.wallet.page.explore.model.DAppTagModel
import com.flowfoundation.wallet.utils.cpuScope
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.viewModelIOScope

class ExploreViewModel : ViewModel() {
    val recentLiveData = MutableLiveData<List<WebviewRecord>>()
    val bookmarkLiveData = MutableLiveData<List<Bookmark>>()
    val dAppTagsLiveData = MutableLiveData<List<DAppTagModel>>()
    val dAppsLiveData = MutableLiveData<List<DAppModel>>()

    val onDAppClickLiveData = MutableLiveData<String>()

    private var dappTag: String? = null

    @SuppressLint("StaticFieldLeak")
    private lateinit var activity: FragmentActivity

    fun bindActivity(activity: FragmentActivity) {
        this.activity = activity
        registerObserve()
    }

    fun load() {
        viewModelIOScope(this) {
            recentLiveData.postValue(AppDataBase.database().webviewRecordDao().findAll(limit = 4))
            refreshBookmark()
            refreshDApps()
        }
    }

    fun onDAppClick(url: String) {
        onDAppClickLiveData.postValue(url)
    }

    fun selectDappTag(tag: String) {
        if (dappTag == tag) return
        dappTag = tag
        refreshDApps()
    }

    private fun registerObserve() {
        AppDataBase.database().webviewRecordDao().findAllLive(limit = 4).observe(activity) { recentLiveData.postValue(it) }
        AppDataBase.database().bookmarkDao().findAllLive(limit = 10).observe(activity) { refreshBookmark() }
    }

    private fun refreshBookmark() {
        cpuScope {
            val data = AppDataBase.database().bookmarkDao().findAll(limit = 10)
            bookmarkLiveData.postValue(data)
        }
    }


    private fun refreshDApps() {
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