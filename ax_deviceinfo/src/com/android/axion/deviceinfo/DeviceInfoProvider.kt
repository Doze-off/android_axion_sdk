/*
 * Copyright (C) 2025 AxionOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.axion.deviceinfo

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.hardware.fingerprint.FingerprintManager
import android.os.BatteryManager
import android.os.Build
import android.os.SystemProperties
import android.os.storage.StorageManager
import android.provider.Settings
import android.view.Display
import com.android.internal.os.PowerProfile
import com.android.internal.util.MemInfoReader
import com.android.settingslib.deviceinfo.PrivateStorageInfo
import com.android.settingslib.deviceinfo.StorageManagerVolumeProvider
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

data class DeviceInfo(
    val deviceName: String,
    val model: String,
    val axionVersion: String,
    val axionBuildType: String,
    val androidVersion: String,
    val securityPatch: String,
    val buildDate: String,
    val buildNumber: String,
    val maintainer: String,
    val processor: String,
    val totalRam: String,
    val storageUsed: String,
    val storageTotal: String,
    val storagePercentage: Float,
    val batteryCapacity: String,
    val screenSize: String,
    val screenResolution: String,
    val frontCamera: String,
    val rearCamera: String,
    val kernelVersion: String
)

object DeviceInfoProvider {

    fun getDeviceInfo(context: Context): DeviceInfo {
        val storageInfo = getStorageInfo(context)

        return DeviceInfo(
            deviceName = getDeviceName(context),
            model = Build.MODEL,
            axionVersion = getAxionVersion(),
            axionBuildType = getAxionBuildType(),
            androidVersion = Build.VERSION.RELEASE,
            securityPatch = Build.VERSION.SECURITY_PATCH,
            buildDate = getBuildDate(),
            buildNumber = Build.DISPLAY,
            maintainer = getMaintainerName(),
            processor = getProcessor(),
            totalRam = getTotalRam(),
            storageUsed = storageInfo.first,
            storageTotal = storageInfo.second,
            storagePercentage = storageInfo.third,
            batteryCapacity = getBatteryCapacity(context),
            screenSize = getScreenSize(),
            screenResolution = getScreenResolution(context),
            frontCamera = getFrontCameraMegapixels(context),
            rearCamera = getRearCameraMegapixels(context),
            kernelVersion = getKernelVersion()
        )
    }

    fun getDeviceName(context: Context): String {
        return Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            ?: Build.MODEL
    }

    fun setDeviceName(context: Context, name: String) {
        Settings.Global.putString(context.contentResolver, Settings.Global.DEVICE_NAME, name)
    }

    @JvmStatic
    fun isFingerprintBiometricSupported(context: Context): Boolean {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)) {
            return false
        }

        return try {
            context.getSystemService(FingerprintManager::class.java)
                ?.sensorPropertiesInternal
                ?.isNotEmpty() == true
        } catch (_: Exception) {
            false
        }
    }

    @JvmStatic
    fun isUdfpsSupported(context: Context): Boolean {
        return try {
            if (context.resources.run {
                    getIdentifier("config_is_powerbutton_fps", "bool", "android")
                        .takeIf { it != 0 }
                        ?.let { getBoolean(it) }
                } == true) {
                return false
            }

            context.resources.run {
                getIdentifier("config_udfps_sensor_props", "array", "android")
                    .takeIf { it != 0 }
                    ?.let { getIntArray(it) }
                    ?.isNotEmpty()
            } == true || context.getSystemService(FingerprintManager::class.java)
                ?.sensorPropertiesInternal
                ?.any { it.isAnyUdfpsType } == true
        } catch (_: Exception) {
            false
        }
    }

    fun getAxionVersion(): String {
        return SystemProperties.get("ro.axion.build.version", "1.0")
    }

    fun getAxionBuildType(): String {
        return SystemProperties.get("ro.axion.releasetype", "Stable")
            .replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }

    fun getBuildDate(): String {
        return SystemProperties.get("ro.build.date", "Unknown")
    }

    fun getMaintainerName(): String {
        return SystemProperties.get("persist.sys.axion_maintainer", "Unknown")
            .replace("_", " ")
    }

    fun getProcessor(): String {
        val processorInfo = SystemProperties.get("persist.sys.axion_processor_info", "")
        if (processorInfo.isNotEmpty()) {
            return processorInfo.replace("_", " ")
        }
        return Build.HARDWARE.replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }

    fun getTotalRam(): String {
        val memInfoReader = MemInfoReader()
        memInfoReader.readMemInfo()
        val totalMemoryBytes = memInfoReader.totalSize
        val totalMemoryGB = totalMemoryBytes / (1000.0 * 1000.0 * 1000.0)
        val rounded = totalMemoryGB.roundToInt().coerceAtLeast(1)
        return "${rounded}GB"
    }

    private fun getStorageInfo(context: Context): Triple<String, String, Float> {
        val storageManager = context.getSystemService(StorageManager::class.java)
        val volumeProvider = StorageManagerVolumeProvider(storageManager)
        val info = PrivateStorageInfo.getPrivateStorageInfo(volumeProvider)

        val totalBytes = info.totalBytes
        val usedBytes = totalBytes - info.freeBytes

        val usedGB = usedBytes / (1024.0 * 1024.0 * 1024.0)
        val totalStorageGB = totalBytes / (1024.0 * 1024.0 * 1024.0)
        val roundedStorageGB = roundToNearestKnownStorageSize(totalStorageGB)

        val usedString = if (usedGB.compareTo(1024.0) >= 0) {
            String.format(Locale.US, "%.1f TB", usedGB / 1024.0)
        } else {
            String.format(Locale.US, "%.1f GB", usedGB)
        }

        val totalString = if (roundedStorageGB >= 1024) {
            "${roundedStorageGB / 1024}TB"
        } else {
            "${roundedStorageGB}GB"
        }

        val percentage = if (totalBytes > 0) {
            (usedBytes.toFloat() / totalBytes.toFloat())
        } else 0f

        return Triple(usedString, totalString, percentage)
    }

    private fun roundToNearestKnownStorageSize(storageGB: Double): Int {
        val knownSizes = arrayOf(16, 32, 64, 128, 256, 512, 1024)
        if (storageGB <= 8) return ceil(storageGB).toInt()
        for (size in knownSizes) {
            if (storageGB <= size) return size
        }
        return ceil(storageGB).toInt()
    }

    fun getBatteryCapacity(context: Context): String {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val designCapacityUah = batteryIntent?.getIntExtra(BatteryManager.EXTRA_DESIGN_CAPACITY, -1) ?: -1
        val capacityMah = if (designCapacityUah > 0) {
            designCapacityUah / 1000
        } else {
            PowerProfile(context).getAveragePower(PowerProfile.POWER_BATTERY_CAPACITY).roundToInt()
        }
        return "$capacityMah mAh"
    }

    fun getScreenSize(): String {
        return SystemProperties.get("persist.sys.axion_screen_size", "")
    }

    fun getScreenResolution(context: Context): String {
        val dm = context.getSystemService(DisplayManager::class.java)
        val display = dm?.getDisplay(Display.DEFAULT_DISPLAY)
        val height = display?.mode?.physicalHeight
        val width = display?.mode?.physicalWidth
        return "${width} x ${height}"
    }

    fun getFrontCameraMegapixels(context: Context): String {                                             
      val frontCameraInfo = SystemProperties.get("persist.sys.device_camera_info_front", "")
      if (frontCameraInfo.isNotEmpty()) {
          return frontCameraInfo.split(",").joinToString(" + ") { "${it}MP" }
      }
                                                                                                    
      val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
      val frontList = mutableListOf<String>()                                                          
      for (cameraId in cameraManager.cameraIdList) {                                                      
          val characteristics = cameraManager.getCameraCharacteristics(cameraId)                          
          val facing = characteristics.get(CameraCharacteristics.LENS_FACING)                             
          if (facing == CameraCharacteristics.LENS_FACING_FRONT) {                                             
              frontList.add(formatMegapixels(getCameraMegapixels(characteristics)))
          }
      }
      return if (frontList.isNotEmpty()) frontList.joinToString(" + ") else "N/A"
    }


    fun getRearCameraMegapixels(context: Context): String {
        val rearCameraInfo = SystemProperties.get("persist.sys.device_camera_info_rear", "")
        if (rearCameraInfo.isNotEmpty()) {
            return rearCameraInfo.split(",").joinToString(" + ") { "${it}MP" }
        }

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val rearList = mutableListOf<String>()
        for (cameraId in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                rearList.add(formatMegapixels(getCameraMegapixels(characteristics)))
            }
        }
        return if (rearList.isNotEmpty()) rearList.joinToString(" + ") else "N/A"
    }

    private fun getCameraMegapixels(characteristics: CameraCharacteristics): Double {
        val sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
        if (sensorSize != null) {
            val totalPixels = sensorSize.width.toLong() * sensorSize.height.toLong()
            return totalPixels.toDouble() / 1_000_000.0
        }
        return 0.0
    }

    private fun formatMegapixels(megapixels: Double): String {
        return if (megapixels % 1.0 == 0.0) {
            "${megapixels.toInt()}MP"
        } else {
            "%.1fMP".format(megapixels)
        }
    }

    fun getKernelVersion(): String {
        return System.getProperty("os.version") ?: "Unknown"
    }
}
