package com.flowfoundation.wallet.firebase.storage

import android.graphics.Bitmap
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.flowfoundation.wallet.manager.account.username
import com.flowfoundation.wallet.utils.extensions.removeUrlParams
import com.flowfoundation.wallet.utils.loge
import java.io.ByteArrayOutputStream


fun uploadAvatarToFirebase(image: Bitmap, callback: (url: String?) -> Unit) {
    // Firebase Storage (lilico-334404.appspot.com) is gone; avatar upload is unavailable until it
    // moves to S3. Returning null shows the existing failure path instead of crashing.
    if (!com.flowfoundation.wallet.firebase.FirebaseFlags.enabled) {
        callback.invoke(null)
        return
    }
    val baos = ByteArrayOutputStream()
    image.compress(Bitmap.CompressFormat.JPEG, 100, baos)
    val data = baos.toByteArray()

    val ref = Firebase.storage.reference.child("avatar/${username()}-${System.currentTimeMillis()}.jpg")
    val uploadTask = ref.putBytes(data)
    uploadTask.continueWithTask { _ -> ref.downloadUrl }
        .addOnCompleteListener { task ->
            // task.result throws on failure; that used to crash the app.
            if (task.isSuccessful) {
                callback.invoke(task.result.toString().removeUrlParams().firebaseImage())
            } else {
                loge(task.exception)
                callback.invoke(null)
            }
        }
}

fun String.firebaseImage(): String {
    if (!this.startsWith("https://firebasestorage.googleapis.com")) {
        return this
    }

    if (this.contains("alt=media")) {
        return this
    }
    return "$this?alt=media"
}