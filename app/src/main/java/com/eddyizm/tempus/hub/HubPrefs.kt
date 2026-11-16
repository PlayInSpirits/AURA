package com.eddyizm.tempus.hub

import android.content.SharedPreferences
import androidx.core.content.edit
import com.eddyizm.tempus.App

/**
 * AURA_2: what the app remembers about the HomeMusic hub it is paired with.
 *
 * The music itself (library, streaming, playlists, the daily mix) goes through Navidrome and is stored
 * by Tempus as before. This object only adds the hub address and the hub session cookie (`hm_session`),
 * which the "Hub" tab hands to its WebView so the user does not have to sign in twice.
 */
object HubPrefs {
    private const val KEY_URL = "aura_hub_url"
    private const val KEY_COOKIE = "aura_hub_cookie"
    private const val KEY_USER = "aura_hub_user"

    private val prefs: SharedPreferences
        get() = App.getInstance().preferences

    /** Base address of the hub without a trailing slash, e.g. `http://192.168.1.129:8080`. */
    var hubUrl: String?
        get() = prefs.getString(KEY_URL, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit { putString(KEY_URL, value) }

    /** Raw `name=value` of the hub session cookie, e.g. `hm_session=abc123`. */
    var cookie: String?
        get() = prefs.getString(KEY_COOKIE, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit { putString(KEY_COOKIE, value) }

    /** Navidrome name of the signed-in account (the hub may have been signed into by e-mail). */
    var userName: String?
        get() = prefs.getString(KEY_USER, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit { putString(KEY_USER, value) }

    fun isPaired(): Boolean = hubUrl != null

    fun clear() {
        prefs.edit {
            remove(KEY_URL)
            remove(KEY_COOKIE)
            remove(KEY_USER)
        }
    }
}
