package com.mobilefork.hermesagent.device

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.os.Build

/**
 * Security boundary for the separately installed Termux + Termux:API apps.
 *
 * This class intentionally does not expose arbitrary shell execution. The
 * RUN_COMMAND permission and Termux's allow-external-apps setting are both
 * required before a later, narrowly allowlisted command adapter can be used.
 */
object HermesExternalTermuxBridge {
    private const val TERMUX_PACKAGE = "com.termux"
    private const val TERMUX_API_PACKAGE = "com.termux.api"
    private const val RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND"

    data class Status(
        val termuxInstalled: Boolean,
        val termuxApiInstalled: Boolean,
        val runCommandPermissionGranted: Boolean,
        val readyForExternalCommands: Boolean,
        val detail: String,
    )

    fun status(context: Context): Status {
        val app = context.applicationContext
        val termuxInstalled = isPackageInstalled(app, TERMUX_PACKAGE)
        val termuxApiInstalled = isPackageInstalled(app, TERMUX_API_PACKAGE)
        val permissionGranted = app.checkSelfPermission(RUN_COMMAND_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED
        val ready = termuxInstalled && termuxApiInstalled && permissionGranted
        val detail = when {
            !termuxInstalled -> "Install the official Termux app first."
            !termuxApiInstalled -> "Install the matching Termux:API app."
            !permissionGranted -> "Grant 'Run commands in Termux environment' to this app in Android App permissions."
            else -> "Android permission is granted. Termux must also have allow-external-apps=true before external commands can run."
        }
        return Status(
            termuxInstalled = termuxInstalled,
            termuxApiInstalled = termuxApiInstalled,
            runCommandPermissionGranted = permissionGranted,
            readyForExternalCommands = ready,
            detail = detail,
        )
    }

    /**
     * Opens Android's app-info page so the user can grant the special
     * RUN_COMMAND permission explicitly. No permission is silently enabled.
     */
    fun openAgentAppPermissionSettings(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    /**
     * Opens Termux app settings. The user must manually enable
     * allow-external-apps in ~/.termux/termux.properties and restart Termux.
     */
    fun openTermuxAppSettings(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$TERMUX_PACKAGE")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
        }.isSuccess
    }
}
