package com.liftley.vodrop

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform