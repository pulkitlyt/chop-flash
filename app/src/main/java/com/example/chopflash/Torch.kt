package com.example.chopflash

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.util.Log

/** Thin wrapper around CameraManager's torch mode. No CAMERA permission is needed for this. */
class Torch(context: Context) {

    private val cameraManager = context.getSystemService(CameraManager::class.java)

    val cameraId: String? = findFlashCamera()

    @Volatile
    var isOn = false
        private set

    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id == cameraId) isOn = enabled
        }

        override fun onTorchModeUnavailable(id: String) {
            if (id == cameraId) isOn = false
        }
    }

    init {
        cameraManager.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
    }

    /** Returns true if the torch state was changed. */
    fun toggle(): Boolean {
        val id = cameraId ?: return false
        return try {
            cameraManager.setTorchMode(id, !isOn)
            isOn = !isOn
            true
        } catch (e: CameraAccessException) {
            // Usually means another app (e.g. the camera) is holding the camera.
            Log.w(TAG, "Torch unavailable", e)
            false
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Bad camera id", e)
            false
        }
    }

    fun turnOff() {
        val id = cameraId ?: return
        if (!isOn) return
        try {
            cameraManager.setTorchMode(id, false)
        } catch (_: Exception) {
        }
    }

    fun release() {
        cameraManager.unregisterTorchCallback(callback)
    }

    private fun findFlashCamera(): String? {
        val ids = try {
            cameraManager.cameraIdList
        } catch (e: CameraAccessException) {
            return null
        }
        val withFlash = ids.filter {
            cameraManager.getCameraCharacteristics(it)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        return withFlash.firstOrNull {
            cameraManager.getCameraCharacteristics(it)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: withFlash.firstOrNull()
    }

    private companion object {
        const val TAG = "Torch"
    }
}
