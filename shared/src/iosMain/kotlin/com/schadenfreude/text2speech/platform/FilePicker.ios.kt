package com.schadenfreude.text2speech.platform

import com.schadenfreude.text2speech.util.logError
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerMode
import platform.UIKit.UIDocumentPickerViewController
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.coroutines.resume

class IosFilePicker : FilePicker {
    private var activeDelegate: UIDocumentPickerDelegateProtocol? = null

    override suspend fun pickFile(): ByteArray? = suspendCancellableCoroutine { continuation ->
        val viewController = IosContext.getViewController() ?: run {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        @Suppress("DEPRECATION")
        val picker = UIDocumentPickerViewController(
            documentTypes = listOf("public.audio"),
            inMode = UIDocumentPickerMode.UIDocumentPickerModeImport
        )

        activeDelegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(
                controller: UIDocumentPickerViewController,
                didPickDocumentsAtURLs: List<*>
            ) {
                val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
                if (url == null) {
                    continuation.resume(null)
                    activeDelegate = null
                    return
                }

                val data = NSData.dataWithContentsOfURL(url)
                if (data == null) {
                    logError("IosFilePicker", "Failed to read data from URL: $url")
                    continuation.resume(null)
                    activeDelegate = null
                    return
                }

                continuation.resume(data.toByteArray())
                activeDelegate = null
            }

            override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                continuation.resume(null)
                activeDelegate = null
            }
        }

        picker.delegate = activeDelegate

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
