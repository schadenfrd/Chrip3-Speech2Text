package com.schadenfreude.text2speech

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform