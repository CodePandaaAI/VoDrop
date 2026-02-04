package com.liftley.vodrop.service

/**
 * iOS stub for ServiceController (no foreground service on iOS).
 */
class IosServiceController : ServiceController {
    override fun startForeground() {
        // No-op on iOS - handled differently
    }
    
    override fun stopForeground() {
        // No-op on iOS
    }
}

actual fun createServiceController(): ServiceController = IosServiceController()
