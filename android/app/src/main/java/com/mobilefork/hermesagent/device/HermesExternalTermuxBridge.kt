package com.mobilefork.hermesagent.device

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.os.Build
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONObject

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
    private const val RUN_COMMAND_ACTION = "com.termux.RUN_COMMAND"
    private const val RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
    private const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
    private const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    private const val EXTRA_PENDING_INTENT = "pendingIntent"
    private const val TERMUX_PREFIX = "/data/data/com.termux/files/usr/bin"
    private const val TERMUX_HOME = "/data/data/com.termux/files/home"
    private val requestIds = AtomicInteger(4100)

    enum class DiagnosticCommand(val executable: String) {
        BATTERY_STATUS("$TERMUX_PREFIX/termux-battery-status"),
    }

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
     * Run only a fixed, read-only Termux:API diagnostic. No model-provided
     * command, executable path, or arguments are accepted.
     *
     * Results are delivered to HermesTermuxResultService and persisted locally.
     * Termux >= 0.109 is required for the PendingIntent result channel.
     */
    fun requestBatteryStatus(context: Context): JSONObject {
        val app = context.applicationContext
        val status = status(app)
        if (!status.readyForExternalCommands) {
            return JSONObject()
                .put("ok", false)
                .put("accepted", false)
                .put("error", status.detail)
                .put("execution_mode", "termux_external_not_ready")
        }

        val requestId = requestIds.incrementAndGet()
        val resultIntent = Intent(app, HermesTermuxResultService::class.java)
            .putExtra(HermesTermuxResultService.EXTRA_REQUEST_ID, requestId)
        val pendingFlags = PendingIntent.FLAG_ONE_SHOT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val resultPendingIntent = PendingIntent.getService(
            app,
            requestId,
            resultIntent,
            pendingFlags,
        )

        val command = DiagnosticCommand.BATTERY_STATUS
        val intent = Intent(RUN_COMMAND_ACTION).apply {
            setClassName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE)
            putExtra(EXTRA_COMMAND_PATH, command.executable)
            putExtra(EXTRA_ARGUMENTS, emptyArray<String>())
            putExtra(EXTRA_WORKDIR, TERMUX_HOME)
            putExtra(EXTRA_BACKGROUND, true)
            putExtra(EXTRA_PENDING_INTENT, resultPendingIntent)
            putExtra("com.termux.RUN_COMMAND_LABEL", "Hermes battery diagnostic")
            putExtra("com.termux.RUN_COMMAND_DESCRIPTION", "Read battery status using Termux:API")
        }

        return runCatching {
            app.startService(intent)
            JSONObject()
                .put("ok", true)
                .put("accepted", true)
                .put("request_id", requestId)
                .put("command", "termux-battery-status")
                .put("execution_mode", "termux_external_pending_result")
                .put("message", "Battery diagnostic was sent to Termux. Read the result with lastBatteryStatusResult().")
        }.getOrElse { error ->
            JSONObject()
                .put("ok", false)
                .put("accepted", false)
                .put("error", error.message ?: error.javaClass.simpleName)
                .put("execution_mode", "termux_external_start_failed")
        }
    }

    fun lastBatteryStatusResult(context: Context): JSONObject? {
        val raw = context.applicationContext
            .getSharedPreferences(HermesTermuxResultService.PREFERENCES, Context.MODE_PRIVATE)
            .getString(HermesTermuxResultService.LAST_BATTERY_RESULT, null)
            ?: return null
        return runCatching { JSONObject(raw) }.getOrNull()
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
