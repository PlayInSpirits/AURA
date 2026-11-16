package com.eddyizm.tempus.hub

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** What `GET /api/auth/options` told us about a hub. */
data class HubInfo(
    val baseUrl: String,
    val musicUrl: String?,
    val invites: Boolean,
    val emailSignup: Boolean,
)

/** Answer of the hub's login or register call. */
data class HubAuth(
    val ok: Boolean,
    val username: String? = null,
    val cookie: String? = null,
    val error: String? = null,
    val status: Int = 0,
)

/** Result of pairing the app with a server: everything the UI needs to finish signing in. */
data class PairResult(
    val ok: Boolean,
    val error: String? = null,
    /** Navidrome address the player (Subsonic API) should use. */
    val navUrl: String? = null,
    /** Navidrome account name (the hub may translate an e-mail into it). */
    val navUser: String? = null,
    /** Hub base address, null when no hub answered and only the player gets connected. */
    val hubBase: String? = null,
    val cookie: String? = null,
    val note: String? = null,
)

/**
 * Client of the HomeMusic hub HTTP API (hub/API.md of HomeMusic).
 *
 * Everything here blocks, call it from a background thread. Only the documented auth endpoints are used:
 *  - GET  /api/auth/options   (no auth) tells that this really is a hub
 *  - POST /api/auth/login     {username, password}
 *  - POST /api/auth/register  {code, username, password}  (invite code)
 * The session is the `hm_session` cookie from `Set-Cookie`.
 */
object HubClient {
    private const val COOKIE_NAME = "hm_session"
    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    private val probeClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    private val authClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Parses what the user typed. Adds `http://` when there is no scheme. */
    fun parseBase(input: String): HttpUrl? {
        var text = input.trim()
        if (text.isEmpty()) return null
        if (!text.contains("://")) text = "http://$text"
        return text.toHttpUrlOrNull()
    }

    /** `scheme://host:port` without a trailing slash. */
    fun baseOf(url: HttpUrl): String {
        return url.newBuilder()
            .encodedPath("/")
            .query(null)
            .fragment(null)
            .build()
            .toString()
            .trimEnd('/')
    }

    private fun withPort(url: HttpUrl, port: Int): String {
        return baseOf(url.newBuilder().port(port).build())
    }

    /**
     * Addresses where the hub may be, given what the user typed. The home setup of HomeMusic keeps
     * Navidrome on 4533 and the hub on 8080 (and 8444 / 8445 behind the VPS), so a Navidrome address
     * leads to the hub next to it. The typed address itself goes first: it may already be the hub.
     */
    fun hubCandidates(typed: HttpUrl): List<String> {
        val out = LinkedHashSet<String>()
        out.add(baseOf(typed))
        when (typed.port) {
            4533 -> out.add(withPort(typed, 8080))
            8444 -> out.add(withPort(typed, 8445))
        }
        if (typed.scheme == "http" && typed.port != 8080) out.add(withPort(typed, 8080))
        return out.toList()
    }

    /** Navidrome address that sits next to a hub address, when the layout is the standard one. */
    private fun musicBaseNextToHub(hubBase: String): String? {
        val hub = hubBase.toHttpUrlOrNull() ?: return null
        return when (hub.port) {
            8080 -> withPort(hub, 4533)
            8445 -> withPort(hub, 8444)
            else -> null
        }
    }

    /** Null when nothing there answers like a hub. */
    fun probeHub(base: String): HubInfo? {
        return try {
            val request = Request.Builder().url("$base/api/auth/options").get().build()
            probeClient.newCall(request).execute().use { response -> readOptions(base, response) }
        } catch (e: Exception) {
            null
        }
    }

    private fun readOptions(base: String, response: Response): HubInfo? {
        if (!response.isSuccessful) return null
        val body = response.body?.string() ?: return null
        val json = JSONObject(body)
        if (!json.has("invites") && !json.has("music_url")) return null
        val music: String? = if (json.isNull("music_url")) null else json.optString("music_url")
        return HubInfo(
            baseUrl = base,
            musicUrl = if (music.isNullOrBlank()) null else music,
            invites = json.optBoolean("invites", true),
            emailSignup = json.optBoolean("email_signup", false),
        )
    }

    fun login(hubBase: String, username: String, password: String): HubAuth {
        val payload = JSONObject().put("username", username).put("password", password)
        return postAuth(hubBase, "/api/auth/login", payload)
    }

    fun register(hubBase: String, code: String, username: String, password: String): HubAuth {
        val payload = JSONObject()
            .put("code", code)
            .put("username", username)
            .put("password", password)
        return postAuth(hubBase, "/api/auth/register", payload)
    }

    /**
     * Ends the hub session (`POST /api/auth/logout`), the same call the hub's «Ещё → Выйти» makes.
     * Best effort: signing out of the app goes on even when the hub cannot be reached.
     */
    fun logout(hubBase: String, cookie: String?): Boolean {
        return try {
            val request = Request.Builder()
                .url("$hubBase/api/auth/logout")
                .post("{}".toRequestBody(JSON_TYPE))
                .apply { if (cookie != null) header("Cookie", cookie) }
                .build()
            probeClient.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    private fun postAuth(hubBase: String, path: String, payload: JSONObject): HubAuth {
        return try {
            val request = Request.Builder()
                .url(hubBase + path)
                .post(payload.toString().toRequestBody(JSON_TYPE))
                .build()
            authClient.newCall(request).execute().use { response -> readAuth(response) }
        } catch (e: Exception) {
            HubAuth(ok = false, error = "Нет связи с хабом: ${e.message}")
        }
    }

    private fun readAuth(response: Response): HubAuth {
        val text = response.body?.string().orEmpty()
        if (response.isSuccessful) {
            // The hub answers with the account as JSON; an empty or non-JSON body (a proxy page)
            // must not be reported as "no connection".
            val me = try {
                JSONObject(text)
            } catch (e: Exception) {
                return HubAuth(ok = false, error = "Хаб ответил не в формате JSON (код ${response.code})", status = response.code)
            }
            val cookie: String? = response.headers("Set-Cookie")
                .firstOrNull { it.startsWith("$COOKIE_NAME=") }
                ?.substringBefore(';')
            val name = me.optString("username")
            return HubAuth(
                ok = true,
                username = if (name.isBlank()) null else name,
                cookie = cookie,
                status = response.code,
            )
        }
        var message = "Ошибка ${response.code}"
        try {
            val detail = JSONObject(text).optString("detail")
            if (detail.isNotBlank()) message = detail
        } catch (ignored: Exception) {
            // not JSON: keep the status text
        }
        if (response.code == 429) {
            val wait = response.header("Retry-After")
            if (wait != null) message += " (повторите через $wait с)"
        }
        return HubAuth(ok = false, error = message, status = response.code)
    }

    /**
     * Pairs the app with a server.
     *
     * [typed] is whatever the user entered: the Navidrome address (`http://192.168.1.129:4533/`) or the
     * hub's. The hub is looked for there and next to it. With a hub found the account is signed in (or
     * registered, when [inviteCode] is given) on the hub first, then the Navidrome address for the
     * player is settled. Without a hub only the player can be connected, except for registration,
     * which exists only on the hub.
     */
    fun pair(typed: String, username: String, password: String, inviteCode: String?): PairResult {
        val url = parseBase(typed) ?: return PairResult(false, error = "Неверный адрес сервера")
        val registering = !inviteCode.isNullOrBlank()

        var hub: HubInfo? = null
        for (candidate in hubCandidates(url)) {
            hub = probeHub(candidate)
            if (hub != null) break
        }

        if (hub == null) {
            if (registering) {
                return PairResult(
                    false,
                    error = "Хаб не найден по этому адресу. Регистрация по коду есть только на хабе " +
                        "(обычно порт 8080 рядом с Navidrome).",
                )
            }
            return PairResult(
                ok = true,
                navUrl = baseOf(url),
                navUser = username,
                note = "Хаб не найден: подключён только плеер",
            )
        }

        val auth = if (registering) {
            register(hub.baseUrl, inviteCode!!.trim().uppercase(), username, password)
        } else {
            login(hub.baseUrl, username, password)
        }
        if (!auth.ok) return PairResult(false, error = auth.error)

        val typedIsHub = hub.baseUrl == baseOf(url)
        val navUrl: String? = if (typedIsHub) {
            musicBaseNextToHub(hub.baseUrl) ?: hub.musicUrl?.trimEnd('/')
        } else {
            baseOf(url)
        }
        if (navUrl == null) {
            return PairResult(
                false,
                error = "Хаб ответил, но адрес Navidrome определить не удалось. Введите адрес Navidrome вручную.",
            )
        }

        return PairResult(
            ok = true,
            navUrl = navUrl,
            navUser = auth.username ?: username,
            hubBase = hub.baseUrl,
            cookie = auth.cookie,
        )
    }
}
