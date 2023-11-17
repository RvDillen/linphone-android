/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.annotation.DrawableRes
import androidx.annotation.UiThread
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.children
import androidx.core.view.doOnAttach
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.findNavController
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.MainActivityBinding
import org.linphone.ui.assistant.AssistantActivity
import org.linphone.ui.main.viewmodel.MainViewModel
import org.linphone.ui.main.viewmodel.SharedMainViewModel
import org.linphone.ui.welcome.WelcomeActivity
import org.linphone.utils.AppUtils
import org.linphone.utils.FileUtils
import org.linphone.utils.LinphoneUtils
import org.linphone.utils.slideInToastFromTop
import org.linphone.utils.slideInToastFromTopForDuration

@UiThread
class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "[Main Activity]"

        private const val CONTACTS_FRAGMENT_ID = 1
        private const val HISTORY_FRAGMENT_ID = 2
        private const val CHAT_FRAGMENT_ID = 3
        private const val MEETINGS_FRAGMENT_ID = 4
    }

    private lateinit var binding: MainActivityBinding

    private lateinit var viewModel: MainViewModel

    private lateinit var sharedViewModel: SharedMainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be done before the setContentView
        installSplashScreen()

        WindowCompat.setDecorFitsSystemWindows(window, true)
        super.onCreate(savedInstanceState)

        while (!coreContext.isReady()) {
            Thread.sleep(20)
        }

        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            loadContacts()
        }

        binding = DataBindingUtil.setContentView(this, R.layout.main_activity)
        binding.lifecycleOwner = this

        viewModel = run {
            ViewModelProvider(this)[MainViewModel::class.java]
        }
        binding.viewModel = viewModel

        sharedViewModel = run {
            ViewModelProvider(this)[SharedMainViewModel::class.java]
        }

        viewModel.changeSystemTopBarColorEvent.observe(this) {
            it.consume { mode ->
                window.statusBarColor = when (mode) {
                    MainViewModel.SINGLE_CALL, MainViewModel.MULTIPLE_CALLS -> {
                        AppUtils.getColor(R.color.green_success_500)
                    }
                    MainViewModel.NETWORK_NOT_REACHABLE, MainViewModel.NON_DEFAULT_ACCOUNT_NOT_CONNECTED -> {
                        AppUtils.getColor(R.color.red_danger_500)
                    }
                    MainViewModel.NON_DEFAULT_ACCOUNT_NOTIFICATIONS -> {
                        AppUtils.getColor(R.color.gray_main2_500)
                    }
                    else -> AppUtils.getColor(R.color.orange_main_500)
                }
            }
        }

        viewModel.goBackToCallEvent.observe(this) {
            it.consume {
                coreContext.showCallActivity()
            }
        }

        viewModel.openDrawerEvent.observe(this) {
            it.consume {
                openDrawerMenu()
            }
        }

        viewModel.defaultAccountRegistrationErrorEvent.observe(this) {
            it.consume { error ->
                val tag = "DEFAULT_ACCOUNT_REGISTRATION_ERROR"
                if (error) {
                    // First remove any already existing connection error toat
                    removePersistentRedToast(tag)

                    val message = getString(R.string.toast_default_account_connection_state_error)
                    showPersistentRedToast(message, R.drawable.warning_circle, tag)
                } else {
                    removePersistentRedToast(tag)
                }
            }
        }

        binding.root.doOnAttach {
            Log.i("$TAG Report UI has been fully drawn (TTFD)")
            try {
                reportFullyDrawn()
            } catch (se: SecurityException) {
                Log.e("$TAG Security exception when doing reportFullyDrawn(): $se")
            }
        }

        // Wait for fragment to be displayed before hiding the splashscreen
        binding.root.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    return if (sharedViewModel.isFirstFragmentReady) {
                        binding.root.viewTreeObserver.removeOnPreDrawListener(this)
                        true
                    } else {
                        false
                    }
                }
            }
        )
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        coreContext.postOnCoreThread { core ->
            if (corePreferences.firstLaunch) {
                Log.i("$TAG First time Linphone 6.0 has been started, showing Welcome activity")
                corePreferences.firstLaunch = false
                coreContext.postOnMainThread {
                    startActivity(Intent(this, WelcomeActivity::class.java))
                }
            } else if (core.accountList.isEmpty()) {
                Log.w("$TAG No account found, showing Assistant activity")
                coreContext.postOnMainThread {
                    startActivity(Intent(this, AssistantActivity::class.java))
                }
            }
        }

        coreContext.greenToastToShowEvent.observe(this) {
            it.consume { pair ->
                val message = pair.first
                val icon = pair.second
                showGreenToast(message, icon)
            }
        }

        coreContext.postOnCoreThread {
            val startDestination = when (corePreferences.defaultFragment) {
                CONTACTS_FRAGMENT_ID -> {
                    Log.i("$TAG Latest visited page is contacts, setting it as start destination")
                    R.id.contactsListFragment
                }
                HISTORY_FRAGMENT_ID -> {
                    Log.i(
                        "$TAG Latest visited page is call history, setting it as start destination"
                    )
                    R.id.historyListFragment
                }
                CHAT_FRAGMENT_ID -> {
                    Log.i(
                        "$TAG Latest visited page is conversations, setting it as start destination"
                    )
                    R.id.conversationsListFragment
                }
                MEETINGS_FRAGMENT_ID -> {
                    Log.i("$TAG Latest visited page is meetings, setting it as start destination")
                    R.id.meetingsListFragment
                }
                else -> { // Default
                    Log.i("$TAG No latest visited page stored, using default one (call history)")
                    R.id.historyListFragment
                }
            }
            coreContext.postOnMainThread {
                if (intent != null) {
                    handleIntent(intent, startDestination, false)
                } else {
                    // This should never happen!
                    Log.e("$TAG onPostCreate called without intent !")
                }
            }
        }
    }

    override fun onPause() {
        val defaultFragmentId = when (sharedViewModel.currentlyDisplayedFragment.value) {
            R.id.contactsListFragment -> {
                CONTACTS_FRAGMENT_ID
            }
            R.id.historyListFragment -> {
                HISTORY_FRAGMENT_ID
            }
            R.id.conversationsListFragment -> {
                CHAT_FRAGMENT_ID
            }
            R.id.meetingsListFragment -> {
                MEETINGS_FRAGMENT_ID
            }
            else -> { // Default
                HISTORY_FRAGMENT_ID
            }
        }
        coreContext.postOnCoreThread {
            Log.i("$TAG Storing default page [$defaultFragmentId]")
            corePreferences.defaultFragment = defaultFragmentId
            corePreferences.config.sync()
        }

        super.onPause()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            handleIntent(intent, -1, true)
        }
    }

    @SuppressLint("RtlHardcoded")
    fun toggleDrawerMenu() {
        if (binding.drawerMenu.isDrawerOpen(Gravity.LEFT)) {
            closeDrawerMenu()
        } else {
            openDrawerMenu()
        }
    }

    fun closeDrawerMenu() {
        binding.drawerMenu.closeDrawer(binding.drawerMenuContent, true)
    }

    private fun openDrawerMenu() {
        binding.drawerMenu.openDrawer(binding.drawerMenuContent, true)
    }

    fun findNavController(): NavController {
        return findNavController(R.id.main_nav_host_fragment)
    }

    fun showGreenToast(message: String, @DrawableRes icon: Int) {
        val greenToast = AppUtils.getGreenToast(this, binding.toastsArea, message, icon)
        binding.toastsArea.addView(greenToast.root)

        greenToast.root.slideInToastFromTopForDuration(
            binding.toastsArea as ViewGroup,
            lifecycleScope
        )
    }

    fun showRedToast(message: String, @DrawableRes icon: Int) {
        val redToast = AppUtils.getRedToast(this, binding.toastsArea, message, icon)
        binding.toastsArea.addView(redToast.root)

        redToast.root.slideInToastFromTopForDuration(
            binding.toastsArea as ViewGroup,
            lifecycleScope
        )
    }

    private fun showPersistentRedToast(
        message: String,
        @DrawableRes icon: Int,
        tag: String,
        doNotTint: Boolean = false
    ) {
        val redToast = AppUtils.getRedToast(this, binding.toastsArea, message, icon, doNotTint)
        redToast.root.tag = tag
        binding.toastsArea.addView(redToast.root)

        redToast.root.slideInToastFromTop(
            binding.toastsArea as ViewGroup,
            true
        )
    }

    private fun removePersistentRedToast(tag: String) {
        for (child in binding.toastsArea.children) {
            if (child.tag == tag) {
                binding.toastsArea.removeView(child)
            }
        }
    }

    private fun handleIntent(intent: Intent, defaultDestination: Int, isNewIntent: Boolean) {
        Log.i(
            "$TAG Handling intent action [${intent.action}], type [${intent.type}] and data [${intent.data}]"
        )

        when (intent.action) {
            Intent.ACTION_MAIN -> {
                handleMainIntent(intent, defaultDestination, isNewIntent)
            }
            Intent.ACTION_SEND -> {
                handleSendIntent(intent, false)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                handleSendIntent(intent, true)
            }
        }
    }

    private fun handleMainIntent(intent: Intent, defaultDestination: Int, isNewIntent: Boolean) {
        val navGraph = findNavController().navInflater.inflate(R.navigation.main_nav_graph)
        if (intent.hasExtra("Chat")) {
            Log.i("$TAG New intent with [Chat] extra")
            if (isNewIntent) {
                Log.i("$TAG Going to Conversations fragment")
                findNavController().navigate(
                    R.id.action_global_conversationsListFragment,
                    intent.extras
                )
            } else {
                Log.i("$TAG Going to Conversations fragment instead of default destination")
                navGraph.setStartDestination(R.id.conversationsListFragment)
                findNavController().setGraph(navGraph, intent.extras)
            }
        } else {
            if (!isNewIntent && defaultDestination > 0) {
                Log.i("$TAG Setting nav graph with expected default destination")
                navGraph.setStartDestination(defaultDestination)
                findNavController().setGraph(navGraph, null)
            }
        }
    }

    private fun handleSendIntent(intent: Intent, multiple: Boolean) {
        val parcelablesUri = arrayListOf<Uri>()

        if (intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { extraText ->
                sharedViewModel.textToShareFromIntent.value = extraText
            }
        } else {
            if (multiple) {
                val parcelables =
                    intent.getParcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)
                for (parcelable in parcelables.orEmpty()) {
                    val uri = parcelable as? Uri
                    if (uri != null) {
                        Log.i("$TAG Found URI [$uri] in parcelable extra list")
                        parcelablesUri.add(uri)
                    }
                }
            } else {
                val uri = intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri
                if (uri != null) {
                    Log.i("$TAG Found URI [$uri] in parcelable extra")
                    parcelablesUri.add(uri)
                }
            }
        }

        val list = arrayListOf<String>()
        lifecycleScope.launch() {
            val deferred = arrayListOf<Deferred<String?>>()
            for (uri in parcelablesUri) {
                deferred.add(async { FileUtils.getFilePath(this@MainActivity, uri, false) })
            }

            val shortcutId = intent.getStringExtra("android.intent.extra.shortcut.ID") // Intent.EXTRA_SHORTCUT_ID
            if (shortcutId != null) {
                Log.i("$TAG Found shortcut ID [$shortcutId]")
                val pair = LinphoneUtils.getLocalAndPeerSipUrisFromChatRoomId(shortcutId)
                if (pair != null) {
                    val localSipUri = pair.first
                    val remoteSipUri = pair.second
                    Log.i(
                        "$TAG Navigating to conversation with local [$localSipUri] and peer [$remoteSipUri] addresses, computed from shortcut ID"
                    )
                    intent.putExtra("LocalSipUri", localSipUri)
                    intent.putExtra("RemoteSipUri", remoteSipUri)
                } else {
                    Log.e("$TAG Failed to parse shortcut ID, going to conversations list")
                }
            } else {
                Log.i("$TAG Going into conversations list as no shortcut ID as found")
            }

            val navGraph = findNavController().navInflater.inflate(R.navigation.main_nav_graph)
            navGraph.setStartDestination(R.id.conversationsListFragment)

            val paths = deferred.awaitAll()
            for (path in paths) {
                Log.i("$TAG Found file to share [$path] in intent")
                if (path != null) list.add(path)
            }

            if (list.isNotEmpty()) {
                sharedViewModel.filesToShareFromIntent.value = list
            } else {
                if (sharedViewModel.textToShareFromIntent.value.orEmpty().isNotEmpty()) {
                    Log.i("$TAG Found plain text to share")
                } else {
                    Log.w("$TAG Failed to find at least one file or text to share!")
                }
            }

            findNavController().setGraph(navGraph, intent.extras)
        }
    }

    private fun loadContacts() {
        coreContext.contactsManager.loadContacts(this)

        /* TODO: Uncomment later, only fixes a small UI display issue for contacts with emoji in the name
        val emojiCompat = coreContext.emojiCompat
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                // Wait for emoji compat library to have been loaded
                Log.i("[Main Activity] Waiting for emoji compat library to have been loaded")
                while (emojiCompat.loadState == EmojiCompat.LOAD_STATE_DEFAULT || emojiCompat.loadState == EmojiCompat.LOAD_STATE_LOADING) {
                    delay(100)
                }

                Log.i(
                    "[Main Activity] Emoji compat library loading status is ${emojiCompat.loadState}, re-loading contacts"
                )
                coreContext.postOnMainThread {
                    // Contacts loading must be started from UI thread
                    coreContext.contactsManager.loadContacts(this@MainActivity)
                }
            }
        }*/
    }
}
