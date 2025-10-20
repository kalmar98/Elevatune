package com.example.elevatune

import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionUtils {
    const val REQUEST_RECORD_AUDIO = 1001
    fun hasAudioPermission(activity: Activity) =
        ContextCompat.checkSelfPermission(activity, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun requestAudioPermission(activity: Activity) {
        ActivityCompat.requestPermissions(activity,
            arrayOf(android.Manifest.permission.RECORD_AUDIO, android.Manifest.permission.READ_EXTERNAL_STORAGE),
            REQUEST_RECORD_AUDIO)
    }
}