package com.schadenfreude.text2speech.platform

import androidx.activity.ComponentActivity
import java.lang.ref.WeakReference

object AndroidContext {
    private var activityRef: WeakReference<ComponentActivity>? = null

    fun setActivity(activity: ComponentActivity) {
        activityRef = WeakReference(activity)
    }

    fun getActivity(): ComponentActivity? = activityRef?.get()
}
