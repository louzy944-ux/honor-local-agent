package com.mobilefork.hermesagent.device

import android.app.IntentService
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import org.json.JSONObject
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Minimal, bounded bridge to the real Termux app.
 *
 * The first integration deliberately exposes only a pwd probe. General command
 * execution must be added behind the agent's explicit user-approval policy.
 */
object HermesTermuxRunCommandBridge {
    private const val TERMUX_PACKAGE = "com.termux"
    private const val TERMUX_SERVICE = "com.termux.app.RunCommandService"
    private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
    private const val EXTRA_PATH = "com.termux.RUN_COMMAND_PATH"
    private const val EXTRA_ARGS = "com.termux.RUN_COMMAND_ARGUMENTS"
    private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    private const val EXTRA_LABEL = "com.termux.RUN_COMMAND_COMMAND_LABEL"
    private const val EXTRA_DESCRIPTION = "com.termux.RUN_COMMAND_COMMAND_DESCRIPTION"
    private const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"
    private const val RESULT_BUNDLE = "result"

    private val nextId = AtomicInteger(10_000)
    private val pending = java.util.concurrent.ConcurrentHashMap<Int, CompletableFuture<Bundle>>()

    fun runPwdProbe(context: Context, timeoutSeconds: Long = 15): JSONObject {
        val app = context.applicationContext
        val id = nextId.incrementAndGet()
        val future = CompletableFuture<Bundle>()
        pending[id] = future
        try {
            val callback = Intent(app, HermesTermuxResultService::class.java)
                .putExtra(HermesTermuxResultService.EXTRA_EXECUTION_ID, id)
            val callbackPendingIntent = PendingIntent.getService(
                app,
                id,
                callback,
                PendingIntent.FLAG_ONE_SHOT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0),
            )

            val command = Intent(ACTION_RUN_COMMAND).apply {
                setClassName(TERMUX_PACKAGE, TERMUX_SERVICE)
                putExtra(EXTRA_PATH, "/data/data/com.termux/files/usr/bin/pwd")
                putExtra(EXTRA_ARGS, emptyArray<String>())
                putExtra(EXTRA_WORKDIR, "/data/data/com.termux/files/home")
                putExtra(EXTRA_BACKGROUND, true)
                putExtra(EXTRA_PENDING_INTENT, callbackPendingIntent)
            }
            app.startService(command)

            val result = future.get(timeoutSeconds.coerceIn(5, 30), TimeUnit.SECONDS)
            val stdout = result.getString("stdout").orEmpty()
            val stderr = result.getString("stderr").orEmpty()
            val exitCode = result.getInt("exitCode", 125)
            return JSONObject()
                .put("ok", exitCode == 0)
                .put("exit_code", exitCode)
                .put("output", stdout.take(8_000))
                .put("error", stderr.take(4_000))
                .put("execution_mode", "real_termux_run_command")
                .put("command", "pwd")
                .put("execution_id", id)
        } catch (error: Exception) {
            return JSONObject()
                .put("ok", false)
                .put("exit_code", 125)
                .put("output", "")
                .put("error", error.message ?: error.javaClass.simpleName)
                .put("execution_mode", "real_termux_run_command")
                .put("command", "pwd")
                .put("execution_id", id)
        } finally {
            pending.remove(id)
        }
    }

    internal fun deliverResult(id: Int, result: Bundle) {
        pending[id]?.complete(result)
    }

    internal const val EXTRA_RESULT_BUNDLE = RESULT_BUNDLE
}

/** Receives Termux's PendingIntent result and completes the waiting probe. */
class HermesTermuxResultService : IntentService("HermesTermuxResultService") {
    override fun onHandleIntent(intent: Intent?) {
        if (intent == null) return
        val id = intent.getIntExtra(EXTRA_EXECUTION_ID, 0)
        val result = intent.getBundleExtra(HermesTermuxRunCommandBridge.EXTRA_RESULT_BUNDLE)
        if (id != 0 && result != null) {
            val normalized = Bundle().apply {
                putString("stdout", result.getString("stdout").orEmpty())
                putString("stderr", result.getString("stderr").orEmpty())
                putInt("exit_code", result.getInt("exit_code", 125))
            }
            HermesTermuxRunCommandBridge.deliverResult(id, normalized)
        }
    }

    companion object {
        const val EXTRA_EXECUTION_ID = "hermes_termux_execution_id"
    }
}
