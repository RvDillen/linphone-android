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
import org.linphone.clb.LinphonePreferencesCLB
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

            // CLB CreateConfigCLB replaces: val config = Factory.instance().createConfigWithFactory(
            val config = CreateConfigCLB()
            corePreferences.config = config

            val appName = context.getString(R.string.app_name)
            Factory.instance().setLoggerDomain(appName)
            Factory.instance().enableLogcatLogs(corePreferences.logcatLogsOutput)
            if (corePreferences.debugLogs) {
                Factory.instance().loggingService.setLogLevel(LogLevel.Message)
            }

            // CLB Config changed ? => write to log (log is available now)
            if (LinphonePreferencesCLB.instance().HasLogInfo())
                LinphonePreferencesCLB.instance().WriteLogLines()

            Log.i("[Application] Core context created ${if (pushReceived) "from push" else ""}")
            coreContext = CoreContext(context, config)
            coreContext.start()

            // CLB Registration
            var registerCLB: RegisterCLB = org.linphone.clb.RegisterCLB(coreContext.context.applicationContext)
            registerCLB.RegisterReceivers()

            // CLB Forcing init of Callstate
            CallStateCLB.instance().IsCallFromCLB()
        }

        private fun CreateConfigCLB(): Config {

            // Get restrictions data (AppConfigHelper)
            var acf = AppConfigHelper(coreContext.context, corePreferences)

            // Init
            acf.checkAppConfig()

            // Handle changes in LinphoneRc
            if (acf.linphoneRcHasChanges()) {
                val linphonercData = acf.linphoneRc

        	    // CLB LinphoneRC changes? => Update
	            //val linphonercData =
        	    //    this::class.java.classLoader.getResource("assets/clb_linphonerc_test").readText()
	            if (LinphonePreferencesCLB.instance().UpdateFromLinphoneRcData(linphonercData, corePreferences.configPath)) {
           	    	acf.storeRcHash()
            	}
            }
	    
            val config = Factory.instance().createConfigWithFactory(
                corePreferences.configPath,
                corePreferences.factoryConfigPath
            )

            // Parse/execute RC XML
            if (acf.linphoneRcXmlHasChanges()) {
                val linphonercXmlData = acf.linphoneRcXml

	            // CLB LinphoneRC XML changes? => Update
	            //val linphonercXmlData =
	            //    this::class.java.classLoader.getResource("assets/clb_linphonerc_xml_test")
	            //        .readText()
	            if (LinphonePreferencesCLB.instance().UpdateFromLinphoneXmlData(linphonercXmlData, config)) {
	                acf.storeRcXmlHash()
	            }
            }
            return config
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
