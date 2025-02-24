package com.flowfoundation.wallet.utils.image

import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity


class PhotoPicker private constructor(activity: FragmentActivity) {
    private var onPhotoSelected: ((Uri?) -> Unit)? = null
    private var pickMedia: ActivityResultLauncher<PickVisualMediaRequest>? = null

    init {
        pickMedia =
            activity.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                onPhotoSelected?.invoke(uri)
            }
    }

    fun launch(onResult: (Uri?) -> Unit) {
        onPhotoSelected = onResult
        pickMedia?.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    companion object {
        @JvmStatic
        fun with(activity: FragmentActivity): PhotoPicker {
            return PhotoPicker(activity)
        }
    }
}