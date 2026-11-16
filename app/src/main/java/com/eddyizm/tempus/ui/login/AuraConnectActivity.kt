package com.eddyizm.tempus.ui.login

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.UnstableApi
import com.eddyizm.tempus.App
import com.eddyizm.tempus.R
import com.eddyizm.tempus.helper.ThemeHelper
import com.eddyizm.tempus.hub.HubClient
import com.eddyizm.tempus.hub.HubPrefs
import com.eddyizm.tempus.hub.PairResult
import com.eddyizm.tempus.interfaces.SystemCallback
import com.eddyizm.tempus.model.Server
import com.eddyizm.tempus.repository.PlaylistRepository
import com.eddyizm.tempus.repository.ServerRepository
import com.eddyizm.tempus.repository.SystemRepository
import com.eddyizm.tempus.ui.activity.MainActivity
import com.eddyizm.tempus.util.Preferences
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * AURA_2 sign-in screen: server address, username and password, or an invite code to register.
 *
 * "Pairing" means two things at once: the account is signed in on the HomeMusic hub (so the Hub tab
 * works) and on Navidrome (so the player streams the library). The hub is found from the address
 * the user typed, which may be either the Navidrome address or the hub's own.
 */
@OptIn(UnstableApi::class)
class AuraConnectActivity : AppCompatActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())

    private var registerMode = false
    private var busy = false
    private var serverStep = true

    private lateinit var subtitle: TextView
    private lateinit var stepServer: View
    private lateinit var stepAccount: View
    private lateinit var serverLayout: TextInputLayout
    private lateinit var serverField: TextInputEditText
    private lateinit var serverShown: TextView
    private lateinit var codeLayout: TextInputLayout
    private lateinit var codeField: TextInputEditText
    private lateinit var userField: TextInputEditText
    private lateinit var passwordField: TextInputEditText
    private lateinit var statusView: TextView
    private lateinit var inviteNote: TextView
    private lateinit var progress: ProgressBar
    private lateinit var submitButton: MaterialButton
    private lateinit var toggleButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.enableThemeSwitch(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_aura_connect)

        subtitle = findViewById(R.id.aura_subtitle)
        stepServer = findViewById(R.id.aura_step_server)
        stepAccount = findViewById(R.id.aura_step_account)
        serverLayout = findViewById(R.id.aura_server_layout)
        serverField = findViewById(R.id.aura_server)
        serverShown = findViewById(R.id.aura_server_shown)
        codeLayout = findViewById(R.id.aura_code_layout)
        codeField = findViewById(R.id.aura_code)
        userField = findViewById(R.id.aura_user)
        passwordField = findViewById(R.id.aura_password)
        statusView = findViewById(R.id.aura_status)
        inviteNote = findViewById(R.id.aura_invite_note)
        progress = findViewById(R.id.aura_progress)
        submitButton = findViewById(R.id.aura_submit)
        toggleButton = findViewById(R.id.aura_toggle)

        // The home server is filled in, so it does not have to be typed every time.
        val previous = Preferences.getServer()
        val localHub = App.getInstance().preferences.getString(LOCAL_HUB_KEY, null)
        serverField.setText(
            when {
                !previous.isNullOrBlank() -> previous
                !localHub.isNullOrBlank() -> localHub
                else -> DEFAULT_SERVER
            }
        )
        val previousUser = Preferences.getUser()
        if (!previousUser.isNullOrBlank()) userField.setText(previousUser)

        findViewById<View>(R.id.aura_next).setOnClickListener { goToAccount() }
        serverField.setOnEditorActionListener { _, _, _ -> goToAccount(); true }
        findViewById<View>(R.id.aura_change_server).setOnClickListener { if (!busy) showServerStep() }
        toggleButton.setOnClickListener {
            if (!busy) setRegisterMode(!registerMode)
        }
        submitButton.setOnClickListener { submit() }
        passwordField.setOnEditorActionListener { _, _, _ -> submit(); true }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!serverStep && !busy) {
                    showServerStep()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        setRegisterMode(false)
        showServerStep()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun showServerStep() {
        serverStep = true
        swap(stepAccount, stepServer)
        subtitle.setText(R.string.aura_subtitle_server)
        showStatus(null)
    }

    private fun goToAccount() {
        val typed = serverField.text?.toString().orEmpty().trim()
        if (HubClient.parseBase(typed) == null) {
            serverLayout.error = getString(R.string.aura_err_server)
            return
        }
        serverLayout.error = null
        serverShown.text = typed.trimEnd('/')
        serverStep = false
        swap(stepServer, stepAccount)
        subtitle.setText(if (registerMode) R.string.aura_subtitle_signup else R.string.aura_subtitle_signin)
        (if (registerMode) codeField else if (userField.text.isNullOrEmpty()) userField else passwordField).requestFocus()
    }

    /** Fades from one step to the other. */
    private fun swap(from: View, to: View) {
        from.visibility = View.GONE
        to.alpha = 0f
        to.translationY = 24f
        to.visibility = View.VISIBLE
        to.animate().alpha(1f).translationY(0f).setDuration(220).start()
    }

    private fun setRegisterMode(register: Boolean) {
        registerMode = register
        codeLayout.visibility = if (register) View.VISIBLE else View.GONE
        inviteNote.visibility = if (register) View.GONE else View.VISIBLE
        if (!serverStep) {
            subtitle.setText(if (register) R.string.aura_subtitle_signup else R.string.aura_subtitle_signin)
        }
        submitButton.setText(if (register) R.string.aura_button_register else R.string.aura_button_login)
        toggleButton.setText(if (register) R.string.aura_toggle_to_login else R.string.aura_register_link)
        showStatus(null)
    }

    private fun showStatus(message: String?) {
        if (message.isNullOrBlank()) {
            statusView.visibility = View.GONE
            statusView.text = ""
        } else {
            statusView.text = message
            statusView.visibility = View.VISIBLE
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        progress.visibility = if (value) View.VISIBLE else View.INVISIBLE
        submitButton.isEnabled = !value
        toggleButton.isEnabled = !value
    }

    private fun submit() {
        if (busy) return

        val typed = serverField.text?.toString().orEmpty().trim()
        val user = userField.text?.toString().orEmpty().trim()
        val password = passwordField.text?.toString().orEmpty()
        val code = if (registerMode) codeField.text?.toString().orEmpty().trim() else null

        if (typed.isEmpty() || user.isEmpty() || password.isEmpty() || (registerMode && code.isNullOrEmpty())) {
            showStatus(getString(R.string.aura_err_fill))
            return
        }

        showStatus(null)
        setBusy(true)

        // Network work stays off the main thread; the answer comes back through the handler.
        Thread {
            val result = HubClient.pair(typed, user, password, code)
            mainHandler.post { onPaired(result, password) }
        }.start()
    }

    private fun onPaired(result: PairResult, password: String) {
        if (isFinishing || isDestroyed) return

        if (!result.ok) {
            setBusy(false)
            showStatus(result.error)
            return
        }
        signInToPlayer(result, password)
    }

    /** The same steps as Tempus' own server login: remember the server, then check the credentials. */
    private fun signInToPlayer(result: PairResult, password: String) {
        val navUrl = result.navUrl.orEmpty()
        val navUser = result.navUser.orEmpty()

        val server = Server(
            serverId = System.currentTimeMillis().toString(),
            serverName = "AURA",
            username = navUser,
            password = password,
            address = navUrl,
            localAddress = "",
            timestamp = System.currentTimeMillis(),
            isLowSecurity = false,
            clientCert = "",
            customHeaders = null,
        )

        Preferences.setServerId(server.serverId)
        Preferences.setServer(navUrl)
        Preferences.setLocalAddress("")
        Preferences.setInUseServerAddress(navUrl)
        Preferences.setUser(navUser)
        Preferences.setPassword(password)
        Preferences.setLowSecurity(false)
        Preferences.setClientCert("")
        Preferences.setCustomHeaders(null)
        App.getSubsonicClientInstance(true)

        SystemRepository().checkUserCredential(object : SystemCallback {
            override fun onError(exception: Exception) {
                runOnUiThread { onPlayerRejected(exception) }
            }

            override fun onSuccess(password: String?, token: String?, salt: String?) {
                runOnUiThread { onPlayerAccepted(server, result) }
            }
        })
    }

    private fun onPlayerRejected(exception: Exception) {
        resetServerPreference()
        if (isFinishing || isDestroyed) return
        setBusy(false)
        showStatus(getString(R.string.aura_err_player, exception.message ?: ""))
    }

    private fun onPlayerAccepted(server: Server, result: PairResult) {
        ServerRepository().insert(server)

        // Forget any hub session of a previous account before storing the new one.
        CookieManager.getInstance().removeAllCookies(null)
        com.eddyizm.tempus.hub.HubWebHost.destroy()
        com.eddyizm.tempus.hub.HubWave.stop()
        if (result.hubBase != null) {
            HubPrefs.hubUrl = result.hubBase
            HubPrefs.cookie = result.cookie
            HubPrefs.userName = result.navUser
        } else {
            HubPrefs.clear()
        }

        PlaylistRepository().refreshAllPlaylists()

        // MainActivity decides at start-up from the saved password, so a fresh start takes it to Home.
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }

    private companion object {
        /** The HomeMusic server at home: offered on the first step. */
        const val DEFAULT_SERVER = "http://192.168.1.129:4533/"

        /** Settings -> AURA -> local hub address. */
        const val LOCAL_HUB_KEY = "aura_local_hub"
    }

    private fun resetServerPreference() {
        Preferences.setServerId(null)
        Preferences.setServer(null)
        Preferences.setUser(null)
        Preferences.setPassword(null)
        Preferences.setToken(null)
        Preferences.setSalt(null)
        Preferences.setLowSecurity(false)
        Preferences.setClientCert(null)
        Preferences.setCustomHeaders(null)
        App.getSubsonicClientInstance(true)
    }
}
