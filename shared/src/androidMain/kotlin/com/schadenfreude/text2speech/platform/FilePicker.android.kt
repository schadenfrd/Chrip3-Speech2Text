package com.schadenfreude.text2speech.platform

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import com.schadenfreude.text2speech.util.logError
import java.util.UUID
import kotlin.coroutines.resume

class AndroidFilePicker : FilePicker {
    override suspend fun pickFile(): ByteArray? = withContext(Dispatchers.Main) {
        val activity = AndroidContext.getActivity() ?: return@withContext null

        suspendCancellableCoroutine { continuation ->
            val key = UUID.randomUUID().toString()
            val launcher = activity.activityResultRegistry.register(
                key,
                object : ActivityResultContracts.GetContent() {
                    override fun createIntent(context: Context, input: String): Intent {
                        return super.createIntent(context, input)
                            .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/wav", "audio/flac", "audio/mpeg"))
                    }
                }
            ) { uri ->
                if (uri == null) {
                    continuation.resume(null)
                    return@register
                }

                try {
                    val inputStream = activity.contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes()
                    inputStream?.close()
                    continuation.resume(bytes)
                } catch (e: Exception) {
                    logError("AndroidFilePicker", "Failed to read file from URI: $uri", e)
                    continuation.resume(null)
                }
            }

            launcher.launch("audio/*")
        }
    }
}

actual fun getFilePicker(): FilePicker = AndroidFilePicker()
