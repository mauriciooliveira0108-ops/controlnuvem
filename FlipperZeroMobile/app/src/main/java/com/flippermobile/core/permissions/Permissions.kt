package com.flippermobile.core.permissions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.flippermobile.domain.permissions.Feature
import com.flippermobile.domain.permissions.PermissionPlanner
import com.flippermobile.domain.permissions.PermissionRequirement

object Permissions {
    fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun missing(context: Context, feature: Feature): List<PermissionRequirement> =
        PermissionPlanner.missing(feature, Build.VERSION.SDK_INT) { isGranted(context, it) }

    fun allGranted(context: Context, feature: Feature): Boolean = missing(context, feature).isEmpty()

    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun openSettings(context: Context, action: String) {
        runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }
}
