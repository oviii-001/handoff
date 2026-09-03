package com.ovi.handoff

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform