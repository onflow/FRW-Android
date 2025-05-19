package com.flowfoundation.wallet.utils.debug

import android.app.AlertDialog
import android.content.SharedPreferences
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.utils.debug.fragments.debugTweaks.DebugTweaksFragment
import com.flowfoundation.wallet.utils.debug.fragments.debugViewer.DebugViewerFragment
import java.lang.ref.WeakReference

object DebugManager {

    val applicationContext by lazy {
        Env.getApp()
    }
    val isProductionBuild = false

    private lateinit var fragmentManagerRef: WeakReference<FragmentManager>
    lateinit var tweaks: List<DebugTweak<Any>>
    private val fragmentManger: FragmentManager?
        get() {
            return fragmentManagerRef.get()
        }
    private var containerViewId: Int? = null
    private var debugViewerFragment: DebugViewerFragment? = null
    private var debugTweaksFragment: DebugTweaksFragment? = null

    fun setFragmentManger(
        fragmentManger: FragmentManager,
        containerId: Int
    ) {
        fragmentManagerRef = WeakReference(fragmentManger)
        containerViewId = containerId
    }

    fun initialize(tweaks: List<DebugTweak<Any>>) {
        DebugManager.tweaks = tweaks
    }

    private fun present(
        fragment: Fragment,
        animated: Boolean = true,
        enterAnimation: Int = R.anim.enter_from_bottom
    ) {
        fragmentManger?.commit {
            if (animated) {
                this.setCustomAnimations(enterAnimation, 0, 0, 0)
            }
            setReorderingAllowed(true)
            containerViewId?.let { add(it, fragment) }
        }
    }

    private fun dismiss(
        fragment: Fragment,
        animated: Boolean = true,
        exitAnim: Int,
        shouldRemove: Boolean = true
    ) {
        fragmentManger?.commit {
            if (animated) {
                this.setCustomAnimations(0, exitAnim, 0, 0)
            }
            setReorderingAllowed(true)
            if (shouldRemove) {
                remove(fragment)
            } else {
                hide(fragment)
            }
        }
    }

    fun toggleDebugViewer() {
        debugViewerFragment?.let {
            it.dismiss(false, R.anim.exit_to_bottom)
            debugViewerFragment = null
        } ?: run {
            debugViewerFragment = DebugViewerFragment()
            present(debugViewerFragment!!, false, R.anim.enter_from_bottom)
        }
    }

    fun dismissDebugViewer() {
        debugViewerFragment?.let {
            it.dismiss(false, R.anim.exit_to_bottom)
            debugViewerFragment = null
        }
    }

    fun toggleDebugTweaks() {
        if (!isProductionBuild) {
            debugTweaksFragment?.let {
                it.dismiss(true)
                debugTweaksFragment = null
            } ?: run {
                debugTweaksFragment = DebugTweaksFragment()
                present(
                    debugTweaksFragment!!,
                    animated = true,
                    enterAnimation = R.anim.enter_from_bottom
                )
                debugViewerFragment?.let {
                    it.dismiss(false)
                    present(it, false)
                }
            }
        }
    }

    private fun Fragment.dismiss(
        animated: Boolean = true,
        animation: Int = R.anim.exit_to_bottom
    ) {
        dismiss(this, animated, animation)
    }

    fun getSharedPrefs(): SharedPreferences {
        val tweaksPrefName = "flow_wallet.preferences.debug_tweaks"
        return applicationContext.getSharedPreferences(tweaksPrefName, 0)
    }

    @Suppress("ControlFlowWithEmptyBody")
    fun terminateApp(logout: Boolean = false) {
        if (logout) {
            // TODO reset user session and logout ADD USER Session module
        }
        Runnable {
            Runtime.getRuntime().exit(0)
        }.postDelay(200)
    }
}

class TweakAction(val action: (() -> Unit))

@Suppress("UNCHECKED_CAST")
class DebugTweak<T>(
    val category: String,
    val name: String,
    private val defaultValue: T,
    private val options: List<T>? = null,
    val restartApp: Boolean = false,
    val restartAppAndLogout: Boolean = false,
    val isActive: Boolean = true,
    val postAction: (() -> Unit)? = null
) {
    private val applicationContext by lazy {
        Env.getApp()
    }
    private val isProductionBuild = false

    var value: T
        get() {
            if (isProductionBuild) {
                return defaultValue
            }
            return when (defaultValue) {
                is Boolean -> {
                    DebugManager.getSharedPrefs().getBoolean(name, defaultValue as Boolean) as T
                }
                is String -> {
                    DebugManager.getSharedPrefs().getString(name, defaultValue as String) as T
                }
                else -> {
                    defaultValue
                }
            }
        }
        set(value) {
            when (value) {
                is Boolean -> {
                    DebugManager.getSharedPrefs().edit().putBoolean(name, value).apply()
                }
                is String -> {
                    DebugManager.getSharedPrefs().edit().putString(name, value).apply()
                }
                else -> {
                    // no-ops
                }
            }
        }

    val boolValue: Boolean?
        get() {
            return value as? Boolean
        }

    private val stringValue: String?
        get() {
            if (defaultValue is TweakAction) return null
            return value.toString()
        }

    fun performAction(completion: (() -> Unit)? = null) {
        (defaultValue as? TweakAction)?.let {
            it.action.invoke()
            completion?.invoke()
            return
        }

        (options as? List<String>)?.let {
            showDialog(
                title = name,
                items = it,
                negativeButton = "Cancel"
            ) { which ->
                when (which) {
                    AlertDialog.BUTTON_NEUTRAL,
                    AlertDialog.BUTTON_NEGATIVE -> {
                    }
                    else -> {
                        value = options[which] as T
                        completion?.invoke()
                    }
                }
            }
            return
        }

        val nameEditTextField = EditText(applicationContext)
        nameEditTextField.setSingleLine()
        nameEditTextField.setText(stringValue)

        val container = FrameLayout(applicationContext)
        val params: FrameLayout.LayoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.leftMargin = 16
        params.rightMargin = 16
        nameEditTextField.layoutParams = params
        container.addView(nameEditTextField)
        showDialog(
            title = name,
            positiveButton = "Save",
            negativeButton = "Cancel"
        ) { option ->
            if (option == AlertDialog.BUTTON_POSITIVE) {
                val stringValue = nameEditTextField.text.toString()
                when (defaultValue) {
                    is String -> {
                        value = stringValue as T
                    }
                    is Float -> {
                        value = stringValue.toFloat() as T
                    }
                    is Int -> {
                        (stringValue.toInt() as T).also { value = it }
                    }
                }
                completion?.invoke()
            }
        }
    }
}
