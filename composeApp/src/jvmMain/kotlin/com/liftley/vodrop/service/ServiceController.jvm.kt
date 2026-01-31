package com.liftley.vodrop.service

/**
 * JVM stub for ServiceController (no foreground service on desktop).
 */
class JvmServiceController : ServiceController {
    override fun startForeground() {
        // No-op on JVM - no foreground service needed
    }
    
    override fun stopForeground() {
        // No-op on JVM
    }
}

actual fun createServiceController(): ServiceController = JvmServiceController()
