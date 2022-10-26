/*
 * Copyright (c) 2010-2020 Belledonne Communications SARL.
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
package org.linphone.activities.launcher

import android.content.Intent
import android.os.Bundle
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.GenericActivity
import org.linphone.activities.main.MainActivity
import org.linphone.clb.LinphonePreferencesCLB
import org.linphone.clb.LockHelperExt
import org.linphone.clb.PermissionHelperCLB
import org.linphone.core.tools.Log

class LauncherActivity : GenericActivity() {

    var lockHelper = LockHelperExt(this)
    var clbCall: Boolean = false
    var endCall: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Call from CLB Messenger? => show clb waiting screen
        if (intent?.getStringExtra("CLB") == "OnOutgoingStarted") {
            Log.i("[Launcher] Starting CLB call")
            clbCall = true
        } else if (intent?.getStringExtra("CLB") == "OnOutgoingEnded") {
            Log.i("[Launcher] Stopped CLB call")
            endCall = true
        } else {
            Log.i("[Launcher] Starting...")
        }

        if (clbCall)
            setContentView(R.layout.clb_launcher_activity)
        else
            setContentView(R.layout.launcher_activity)

        lockHelper.lockScreen()
    }

    override fun onStart() {
        super.onStart()
        lockHelper.setWakeLocks()

        // CLB Preferences
        PermissionHelperCLB.instance().CheckPermissions(this)

        coreContext.handler.postDelayed({ onReady() }, 500)
    }

    override fun onDestroy() {
        lockHelper.unlockScreen()
        super.onDestroy()
    }

    private fun onReady() {
        Log.i("[Launcher] Core is ready")

        if (corePreferences.preventInterfaceFromShowingUp) {
            Log.w("[Context] We were asked to not show the user interface")
            finish()
            return
        }
        if (endCall) {
            finish()
            return
        }

        // CLB : OnOutgoingStarted => just display launch screen so microphone keeps enabled (on A11)
        if (clbCall)
            return

        LinphonePreferencesCLB.instance().CheckPermissions(this)

        val intent = Intent()
        intent.setClass(this, MainActivity::class.java)

        // Propagate current intent action, type and data
        if (getIntent() != null) {
            val extras = getIntent().extras
            if (extras != null) intent.putExtras(extras)
        }
        intent.action = getIntent().action
        intent.type = getIntent().type
        intent.data = getIntent().data

        startActivity(intent)
        if (corePreferences.enableAnimations) {
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }
}
