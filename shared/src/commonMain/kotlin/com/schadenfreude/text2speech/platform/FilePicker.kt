package com.schadenfreude.text2speech.platform

interface FilePicker {
    suspend fun pickFile(): ByteArray?
}

expect fun getFilePicker(): FilePicker
