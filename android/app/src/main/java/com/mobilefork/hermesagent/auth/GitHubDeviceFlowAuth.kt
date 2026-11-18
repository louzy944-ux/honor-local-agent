package com.mobilefork.hermesagent.auth

import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * GitHub OAuth Device Flow.
 *
 * The GitHub OAuth App must have Device Flow enabled in its settings.
 * The client ID is public and must be supplied by the app configuration;
 * never embed a client secret in the Android application.
 *
 * Request a device code once, show [DeviceCode.userCode] and
 * [DeviceCode.verificationUri] to the user, then call [pollOnce] no faster
 * than the returned interval. The caller owns scheduling and cancellation.
 */
object GitHubDeviceFlowAuth {
    private const val DEVICE_CODE_URL = "https://github.com/login/device/code"
    private const val ACCESS_TOKEN_URL = "https://github.com/login/oauth/access_token"
    private const val DEFAULT_SCOPE = "repo"

    data class DeviceCode(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val expiresInSeconds: Int,
        val intervalSeconds: Int,
    )

    data class AccessToken(
        val value: String,
        val tokenType: String,
        val scope: String,
        val expiresInSeconds: Long?,
        val refreshToken: String?,
        val refreshTokenExpiresInSeconds: Long?,
    )

    sealed class PollResult {
        data class Pending(val intervalSeconds: Int) : PollResult()
        data class Authorized(val token: AccessToken) : PollResult()
        data class Failed(val error: String, val description: String) : PollResult()
    }

    /**
     * Starts Device Flow. For private repository access, the OAuth App needs
     * the "repo" scope; this is broad access and must be clearly disclosed
     * before requesting authorization.
     */
    fun requestDeviceCode(
        clientId: String,
        scope: String = DEFAULT_SCOPE,
        timeoutMs: Int = 15_000,
    ): DeviceCode {
        require(clientId.isNotBlank()) { "GitHub OAuth client ID is not configured" }
        val form = "client_id=${encode(clientId)}&scope=${encode(scope)}"
        val json = postForm(DEVICE_CODE_URL, form, timeoutMs)
        val deviceCode = json.optString("device_code").trim()
        val userCode = json.optString("user_code").trim()
        val verificationUri = json.optString("verification_uri").trim()
        val expiresIn = json.optInt("expires_in", 900)
        val interval = json.optInt("interval", 5)

        if (deviceCode.isBlank() || userCode.isBlank() || verificationUri.isBlank()) {
            throw IllegalStateException("GitHub returned an incomplete device authorization response")
        }
        return DeviceCode(
            deviceCode = deviceCode,
            userCode = userCode,
            verificationUri = verificationUri,
            expiresInSeconds = expiresIn.coerceAtLeast(1),
            intervalSeconds = interval.coerceAtLeast(1),
        )
    }

    /**
     * Performs exactly one token poll. The caller must wait at least the
     * current interval between polls and use a Pending interval if it changes.
     */
    fun pollOnce(
        clientId: String,
        deviceCode: DeviceCode,
        currentIntervalSeconds: Int = deviceCode.intervalSeconds,
        timeoutMs: Int = 15_000,
    ): PollResult {
        require(clientId.isNotBlank()) { "GitHub OAuth client ID is not configured" }
        val form = buildString {
            append("client_id=").append(encode(clientId))
            append("&device_code=").append(encode(deviceCode.deviceCode))
            append("&grant_type=").append(
                encode("urn:ietf:params:oauth:grant-type:device_code"),
            )
        }
        val json = postForm(ACCESS_TOKEN_URL, form, timeoutMs)
        val error = json.optString("error").trim()
        if (error.isNotEmpty()) {
            return when (error) {
                "authorization_pending" -> PollResult.Pending(currentIntervalSeconds.coerceAtLeast(1))
                "slow_down" -> PollResult.Pending(
                    json.optInt("interval", currentIntervalSeconds + 5)
                        .coerceAtLeast(currentIntervalSeconds + 5),
                )
                "access_denied", "expired_token", "incorrect_device_code",
                "incorrect_client_credentials", "device_flow_disabled",
                "unsupported_grant_type" -> PollResult.Failed(
                    error,
                    json.optString("error_description").ifBlank { error },
                )
                else -> PollResult.Failed(
                    error,
                    json.optString("error_description").ifBlank { "GitHub OAuth authorization failed" },
                )
            }
        }

        val accessToken = json.optString("access_token").trim()
        if (accessToken.isBlank()) {
            return PollResult.Failed(
                "invalid_response",
                "GitHub did not return an access token",
            )
        }
        return PollResult.Authorized(
            AccessToken(
                value = accessToken,
                tokenType = json.optString("token_type", "bearer"),
                scope = json.optString("scope"),
                expiresInSeconds = json.optLongOrNull("expires_in"),
                refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() },
                refreshTokenExpiresInSeconds = json.optLongOrNull("refresh_token_expires_in"),
            ),
        )
    }

    private fun postForm(url: String, form: String, timeoutMs: Int): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "HermesAgent-Android")
        }
        try {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(form) }
            val responseCode = connection.responseCode
            val body = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
            if (body.isBlank()) {
                throw IllegalStateException("GitHub OAuth returned HTTP $responseCode with an empty response")
            }
            val json = JSONObject(body)
            if (responseCode !in 200..299 && !json.has("error")) {
                throw IllegalStateException("GitHub OAuth request failed with HTTP $responseCode")
            }
            return json
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun JSONObject.optLongOrNull(name: String): Long? =
        if (has(name) && !isNull(name)) optLong(name).takeIf { it > 0 } else null
}
