package com.schadenfreude.text2speech.platform

import kotlinx.cinterop.*
import platform.Foundation.*
import platform.UIKit.*
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class IosFilePicker : FilePicker {
    override suspend fun pickFile(): ByteArray? = suspendCoroutine { continuation ->
        val viewController = IosContext.getViewController() ?: run {
            continuation.resume(null)
            return@suspendCoroutine
        }

        @Suppress("DEPRECATION")
        val picker = UIDocumentPickerViewController(
            documentTypes = listOf("public.audio"), 
            inMode = UIDocumentPickerMode.UIDocumentPickerModeImport
        )

        picker.delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
                val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
                if (url == null) {
                    continuation.resume(null)
                    return
                }

                val data = NSData.dataWithContentsOfURL(url)
                if (data == null) {
                    continuation.resume(null)
                    return
                }

                continuation.resume(data.toByteArray())
            }

            override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                continuation.resume(null)
            }
        }

        viewController.presentViewController(picker, animated = true, completion = null)
    }
}

@OptIn(ExperimentalForeignApi::class)
fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val byteArray = ByteArray(size)
    if (size > 0) {
        byteArray.usePinned { pinned ->
            memcpy(pinned.addressOf(0), bytes, length)
        }
    }
    return byteArray
}

actual fun getFilePicker(): FilePicker = IosFilePicker()
