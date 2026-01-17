package com.liftley.vodrop.data.firestore

import android.content.Context
import java.util.UUID

/**
 * Manages device identification for single-device restriction
 */
class DeviceManager(private val context: Context) {

    private val prefs by lazy {
        context.getSharedPreferences("vodrop_device", Context.MODE_PRIVATE)
    }

    companion object {
        private const val KEY_DEVICE_ID = "device_id"
    }

    /**
     * Get or create a unique device ID
     * This ID persists even if app is reinstalled (until app data is cleared)
     */
    fun getDeviceId(): String {
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)

        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }

        return deviceId
    }

    /**
     * Clear device ID (for testing only)
     */
    fun clearDeviceId() {
        prefs.edit().remove(KEY_DEVICE_ID).apply()
    }
}