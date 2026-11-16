package com.eddyizm.tempus.hub

import com.eddyizm.tempus.util.Preferences
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * AURA: signed-in calls to the hub's HTTP API (hub/API.md of HomeMusic) for the app's own screens.
 *
 * Every call blocks: run it off the main thread. The session is the `hm_session` cookie from the
 * sign-in; when the hub says the session is gone (401) the app signs in again once, with the saved
 * name and password, and repeats the call.
 */
object HubApi {
    class HubException(message: String, val code: Int = 0) : Exception(message)

    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    fun isPaired(): Boolean = HubPrefs.hubUrl != null

    fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    /** A hub picture address made absolute (the hub answers `/api/...` for its own covers). */
    fun absolute(url: String?): String? {
        if (url.isNullOrBlank() || url == "null") return null
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        val hub = HubPrefs.hubUrl ?: return null
        return if (url.startsWith("/")) hub + url else "$hub/$url"
    }

    fun get(path: String): String = call("GET", path, null)

    fun post(path: String, body: JSONObject?): String = call("POST", path, body)

    fun delete(path: String): String = call("DELETE", path, null)

    fun getJson(path: String): JSONObject = JSONObject(get(path).ifBlank { "{}" })

    fun postJson(path: String, body: JSONObject?): JSONObject = JSONObject(post(path, body).ifBlank { "{}" })

    private fun call(method: String, path: String, body: JSONObject?): String {
        val hub = HubPrefs.hubUrl ?: throw HubException("Хаб не подключён: войдите в аккаунт заново")
        var code = 0
        var text = ""
        for (attempt in 0..1) {
            val builder = Request.Builder().url(hub + path)
            when (method) {
                "POST" -> builder.post((body ?: JSONObject()).toString().toRequestBody(JSON_TYPE))
                "DELETE" -> builder.delete()
                else -> builder.get()
            }
            HubPrefs.cookie?.let { builder.header("Cookie", it) }
            try {
                client.newCall(builder.build()).execute().use { response ->
                    code = response.code
                    text = response.body?.string().orEmpty()
                }
            } catch (e: java.io.IOException) {
                throw HubException("Нет связи с хабом: ${e.message ?: e.javaClass.simpleName}")
            }
            if (code != 401 || attempt == 1 || !relogin(hub)) break
        }
        if (code !in 200..299) {
            val detail = runCatching { JSONObject(text).optString("detail") }.getOrNull()
            throw HubException(if (detail.isNullOrBlank()) "Ошибка хаба ($code)" else detail, code)
        }
        return text
    }

    private fun relogin(hub: String): Boolean {
        val user = HubPrefs.userName ?: Preferences.getUser() ?: return false
        val password = Preferences.getPassword() ?: return false
        val auth = HubClient.login(hub, user, password)
        if (!auth.ok || auth.cookie == null) return false
        HubPrefs.cookie = auth.cookie
        return true
    }
}
