package com.schadenfreude.text2speech

import androidx.compose.ui.window.ComposeUIViewController
import com.schadenfreude.text2speech.platform.IosContext

fun MainViewController() = ComposeUIViewController { App() }.also {
    IosContext.setViewController(it)
}