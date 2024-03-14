package com.flowfoundation.wallet.page.demo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivityDemoBinding

class DemoActivity : BaseActivity() {

    private lateinit var binding: ActivityDemoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDemoBinding.inflate(layoutInflater)
        setContentView(binding.root)
//        LilicoWebView(binding.root, "https://outblock.github.io/harness/")
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, DemoActivity::class.java))
        }
    }
}