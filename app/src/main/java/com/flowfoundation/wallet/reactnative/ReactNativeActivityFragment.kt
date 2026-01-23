package com.flowfoundation.wallet.reactnative

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.facebook.react.ReactApplication
import com.facebook.react.ReactRootView
import com.flowfoundation.wallet.base.fragment.BaseFragment
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.wallet.toAddress
import com.flowfoundation.wallet.utils.logd

/**
 * Fragment that hosts the React Native Activity screen within the ViewPager.
 * This allows the Activity screen to be displayed as a tab while keeping
 * the bottom navigation visible.
 */
class ReactNativeActivityFragment : BaseFragment() {

    private var reactRootView: ReactRootView? = null

    companion object {
        private const val TAG = "RNActivityFragment"
        private const val COMPONENT_NAME = "FRWRN"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        logd(TAG, "onCreateView - Creating ReactRootView")

        reactRootView = ReactRootView(requireContext())

        val application = requireActivity().application as? ReactApplication
        if (application == null) {
            logd(TAG, "Application is not a ReactApplication")
            return reactRootView!!
        }

        val reactNativeHost = application.reactNativeHost
        val reactInstanceManager = reactNativeHost.reactInstanceManager

        // Build launch options
        val launchOptions = Bundle().apply {
            val address = WalletManager.selectedWalletAddress().toAddress()
            val network = chainNetWorkString()

            putString("address", address)
            putString("network", network)

            // Create initialProps with screen type
            val initialPropsBundle = Bundle().apply {
                putString("screen", "activity")
            }
            putBundle("initialProps", initialPropsBundle)

            // Set initial route for Activity screen
            putString("initialRoute", "Activity")
        }

        logd(TAG, "Starting React application with Activity screen")
        reactRootView?.startReactApplication(
            reactInstanceManager,
            COMPONENT_NAME,
            launchOptions
        )

        return reactRootView!!
    }

    override fun onResume() {
        super.onResume()
        logd(TAG, "onResume")
    }

    override fun onPause() {
        super.onPause()
        logd(TAG, "onPause")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        logd(TAG, "onDestroyView - Unmounting React application")
        reactRootView?.unmountReactApplication()
        reactRootView = null
    }
}
