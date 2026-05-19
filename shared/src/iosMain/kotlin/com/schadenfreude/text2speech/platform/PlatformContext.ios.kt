package com.schadenfreude.text2speech.platform

import platform.UIKit.UIViewController
import kotlin.native.ref.WeakReference

@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
object IosContext {
    private var viewControllerRef: WeakReference<UIViewController>? = null

    fun setViewController(viewController: UIViewController) {
        viewControllerRef = WeakReference(viewController)
    }

    fun getViewController(): UIViewController? = viewControllerRef?.get()
}
