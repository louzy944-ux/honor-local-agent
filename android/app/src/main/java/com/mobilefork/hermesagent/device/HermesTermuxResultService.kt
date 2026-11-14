package com.mobilefork.hermesagent.device

import android.app.IntentService
import android.content.Intent
import org.json.JSONObject

/**
 * Receives the one-shot result PendingIntent sent back by Termux RUN_COMMAND.
 * Only the latest read-only battery diagnostic is retained in private app storage.
 */
class HermesTermuxResultService : IntentService("HermesTermuxResultService") {
    override fun onHandleIntent(intent: Intent?) {
        if (intent == null) return
        val bundle = intent.getBundleExtra(EXTRA_RESULT_BUNDLE)
        val requestId = intent.getIntExtra(EXTRA_REQUEST_ID, -1)
        if (bundle == null || requestId < 0) return

        val result = JSONObject()
            .put("request_id", requestId)
            .put("stdout", bundle.getString("stdout").orEmpty().take(MAX_OUTPUT_CHARS))
            .put("stderr", bundle.getString("stderr").orEmpty().take(MAX_OUTPUT_CHARS))
            .put("exit_code", bundle.getInt("exitCode", -1))
            .put("termux_error_code", bundle.getInt("err", 0))
            .put("termux_error_message", bundle.getString("errmsg").orEmpty().take(MAX_OUTPUT_CHARS))
            .put("received_at_epoch_ms", System.currentTimeMillis())
            .put("ok", bundle.getInt("exitCode", -1) == 0 && bundle.getInt("err", 0) == -1)

        getSharedPreferences(PREFERENCES, MODE_PRIVATE)
            .edit()
            .putString(LAST_BATTERY_RESULT, result.toString())
            .apply()
    }

    companion object {
        const val EXTRA_REQUEST_ID = "hermes_termux_request_id"
        const val PREFERENCES = "hermes_external_termux_results"
        const val LAST_BATTERY_RESULT = "last_battery_status_result"
        private const val EXTRA_RESULT_BUNDLE = "result"
        private const val MAX_OUTPUT_CHARS = 16_384
    }
}
