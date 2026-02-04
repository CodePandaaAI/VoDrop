package com.liftley.vodrop.service

/**
 * Platform abstraction for foreground service control.
 * SessionManager uses this to start/stop the foreground service.
 */
interface ServiceController {
    fun startForeground()
    fun stopForeground()
}

/**
 * Factory function to create platform-specific ServiceController.
 */
expect fun createServiceController(): ServiceController
