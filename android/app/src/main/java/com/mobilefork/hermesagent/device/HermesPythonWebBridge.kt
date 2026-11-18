package com.mobilefork.hermesagent.device

import com.chaquo.python.Python
import org.json.JSONArray
import org.json.JSONObject

/**
 * Narrow bridge to Hermes' existing configured Python web-search implementation.
 * Search-result text is data only; this class never interprets it as an instruction.
 */
object HermesPythonWebBridge {
    private const val MAX_RESULTS = 10
    private const val MAX_TITLE_CHARS = 240
    private const val MAX_DESCRIPTION_CHARS = 1_200
    private const val MAX_URL_CHARS = 2_048

    fun search(query: String, requestedLimit: Int = 5): JSONObject {
        val cleanQuery = query.trim()
        require(cleanQuery.isNotEmpty()) { "Search query cannot be empty" }
        require(cleanQuery.length <= 1_000) { "Search query is too long" }
        check(!Thread.currentThread().isInterrupted) { "Search cancelled" }
        check(Python.isStarted()) {
            "Embedded Python is not initialized; start HermesRuntimeManager first"
        }

        val limit = requestedLimit.coerceIn(1, MAX_RESULTS)
        val raw = Python.getInstance()
            .getModule("tools.web_tools")
            .callAttr("web_search_tool", cleanQuery, limit)
            .toString()

        check(!Thread.currentThread().isInterrupted) { "Search cancelled" }
        val response = JSONObject(raw)
        if (!response.optBoolean("success", false)) {
            return JSONObject()
                .put("success", false)
                .put("error", response.optString("error", "Web search failed").take(1_000))
        }

        val sourceResults = response.optJSONObject("data")?.optJSONArray("web") ?: JSONArray()
        val boundedResults = JSONArray()
        for (index in 0 until minOf(sourceResults.length(), limit)) {
            val item = sourceResults.optJSONObject(index) ?: continue
            val url = item.optString("url").trim()
            if (!url.startsWith("https://", ignoreCase = true) &&
                !url.startsWith("http://", ignoreCase = true)
            ) continue

            boundedResults.put(
                JSONObject()
                    .put("title", item.optString("title").take(MAX_TITLE_CHARS))
                    .put("url", url.take(MAX_URL_CHARS))
                    .put("description", item.optString("description").take(MAX_DESCRIPTION_CHARS))
                    .put("position", item.optInt("position", index + 1)),
            )
        }

        return JSONObject()
            .put("success", true)
            .put("data", JSONObject().put("web", boundedResults))
            .put("untrusted_content", true)
    }
}
