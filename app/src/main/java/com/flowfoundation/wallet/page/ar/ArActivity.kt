package com.flowfoundation.wallet.page.ar

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.util.Log
import com.google.ar.sceneform.ux.ArFragment
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.page.ar.model.ArContentModel
import com.flowfoundation.wallet.page.ar.presenter.ArContentPresenter
import com.flowfoundation.wallet.utils.logw

private val TAG = ArActivity::class.java.simpleName

class ArActivity : BaseActivity() {

    private val image by lazy { intent.getStringExtra(EXTRA_IMAGE) }
    private val video by lazy { intent.getStringExtra(EXTRA_VIDEO) }

    lateinit var fragment: ArFragment

    private lateinit var presenter: ArContentPresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!checkIsSupportedDeviceOrFinish(this)) {
            return
        }
        setContentView(R.layout.activity_ar)
        if (image.isNullOrBlank() && video.isNullOrBlank()) {
            logw(TAG, "image and video is all empty!")
            finish()
            return
        }
//        val video = "https://jsoncompare.org/LearningContainer/SampleFiles/Video/MP4/sample-mp4-file.mp4"
        presenter = ArContentPresenter(supportFragmentManager.findFragmentById(R.id.fragment) as ArFragment, image, video)
    }

    override fun onPause() {
        super.onPause()
        presenter.bind(ArContentModel(onPause = true))
    }

    override fun onRestart() {
        super.onRestart()
        presenter.bind(ArContentModel(onRestart = true))
    }

    override fun onResume() {
        super.onResume()
        presenter.bind(ArContentModel(onResume = true))
    }

    override fun onDestroy() {
        super.onDestroy()
        presenter.bind(ArContentModel(onDestroy = true))
    }

    private fun checkIsSupportedDeviceOrFinish(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < VERSION_CODES.N) {
            Log.e(TAG, "Sceneform requires Android N or later")
            activity.finish()
            return false
        }
        val openGlVersionString = (activity.getSystemService(ACTIVITY_SERVICE) as ActivityManager)
            .deviceConfigurationInfo
            .glEsVersion
        if (openGlVersionString.toDouble() < MIN_OPENGL_VERSION) {
            Log.e(TAG, "Sceneform requires OpenGL ES 3.0 later")
            activity.finish()
            return false
        }
        return true
    }

    companion object {
        private const val EXTRA_IMAGE = "extra_image"
        private const val EXTRA_VIDEO = "extra_video"
        private const val MIN_OPENGL_VERSION = 3.0

        fun launch(context: Context, image: String? = null, video: String? = null) {
            context.startActivity(Intent(context, ArActivity::class.java).apply {
                putExtra(EXTRA_IMAGE, image)
                putExtra(EXTRA_VIDEO, video)
            })
        }
    }
}