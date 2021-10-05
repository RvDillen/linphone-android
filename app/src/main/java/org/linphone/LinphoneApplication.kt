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
package org.linphone

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import org.linphone.clb.AppConfigHelper
import org.linphone.clb.CallStateCLB
import org.linphone.clb.RegisterCLB
import org.linphone.core.*
import org.linphone.core.tools.Log

class LinphoneApplication : Application() {
    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var corePreferences: CorePreferences
        @SuppressLint("StaticFieldLeak")
        lateinit var coreContext: CoreContext

        fun ensureCoreExists(context: Context, pushReceived: Boolean = false) {
            if (::coreContext.isInitialized && !coreContext.stopped) {
                Log.d("[Application] Skipping Core creation (push received? $pushReceived)")
                return
            }

            Factory.instance().setLogCollectionPath(context.filesDir.absolutePath)
            Factory.instance().enableLogCollection(LogCollectionState.Enabled)

            corePreferences = CorePreferences(context)
            corePreferences.copyAssetsFromPackage()

            if (corePreferences.vfsEnabled) {
                CoreContext.activateVFS()
            }

            // Get restrictions data (AppConfigHelper)
            var acf = AppConfigHelper(context, corePreferences)

            // Init (checkAppConfig) and handle changes in LinphoneRc
            if (acf.checkAppConfig() && acf.linphoneRcHasChanges()) {
                var linphoneRc = acf.linphoneRc

                // TODO: Do your RC magic
                Log.d("test", "LinphoneRc: " + linphoneRc)

                // TODO: IF successfully applied:
                acf.storeRcHash()
            }

            val config = Factory.instance().createConfigWithFactory(corePreferences.configPath, corePreferences.factoryConfigPath)
            corePreferences.config = config

            // Parse/execute RC XML
            if (acf.linphoneRcXmlHasChanges()) {
                var linphoneRcXml = acf.linphoneRcXml

                // TODO: Do more RC XML magic
                Log.d("test", "LinphoneRcXml: " + linphoneRcXml)

                // TODO: IF settings are applied successfully:
                acf.storeRcXmlHash()
            }

            val appName = context.getString(R.string.app_name)
            Factory.instance().setLoggerDomain(appName)
            Factory.instance().enableLogcatLogs(corePreferences.logcatLogsOutput)
            if (corePreferences.debugLogs) {
                Factory.instance().loggingService.setLogLevel(LogLevel.Message)
            }

            Log.i("[Application] Core context created ${if (pushReceived) "from push" else ""}")
            coreContext = CoreContext(context, config)
            coreContext.start()

            // CLB Registration
            var registerCLB: RegisterCLB = org.linphone.clb.RegisterCLB(coreContext.context.applicationContext)
            registerCLB.RegisterReceivers()

            // CLB Forcing init of Callstate
            CallStateCLB.instance().IsCallFromCLB()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val appName = getString(R.string.app_name)
        android.util.Log.i("[$appName]", "Application is being created")
        ensureCoreExists(applicationContext)
        Log.i("[Application] Created")
    }
}
