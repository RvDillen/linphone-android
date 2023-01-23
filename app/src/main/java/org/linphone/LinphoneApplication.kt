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
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import java.util.*
import kotlin.concurrent.schedule
import org.linphone.clb.AppConfigHelper
import org.linphone.clb.CallStateCLB
import org.linphone.clb.LinphonePreferencesCLB
import org.linphone.clb.RegisterCLB
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.mediastream.Version

class LinphoneApplication : Application(), ImageLoaderFactory {
    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var corePreferences: CorePreferences
        @SuppressLint("StaticFieldLeak")
        lateinit var coreContext: CoreContext

        private fun createConfig(context: Context) {
            if (::corePreferences.isInitialized) {
                return
            }

            Factory.instance().setLogCollectionPath(context.filesDir.absolutePath)
            Factory.instance().enableLogCollection(LogCollectionState.Enabled)

            // For VFS
            Factory.instance().setCacheDir(context.cacheDir.absolutePath)

            corePreferences = CorePreferences(context)
            corePreferences.copyAssetsFromPackage()

            if (corePreferences.vfsEnabled) {
                CoreContext.activateVFS()
            }

            // if (false) {
            //    TestConfigClbParsing()
            // }

            // CLB CreateConfigCLB replaces: val config = Factory.instance().createConfigWithFactory()
            val config = CreateConfigCLB(context)
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
            Log.i("[Application] Core config & preferences created")
        }

        fun ensureCoreExists(
            context: Context,
            pushReceived: Boolean = false,
            service: CoreService? = null,
            useAutoStartDescription: Boolean = false
        ): Boolean {
            if (::coreContext.isInitialized && !coreContext.stopped) {
                Log.d("[Application] Skipping Core creation (push received? $pushReceived)")
                return false
            }

            Log.i("[Application] Core context is being created ${if (pushReceived) "from push" else ""}")
            coreContext = CoreContext(context, corePreferences.config, service, useAutoStartDescription)
	    
	    if (coreContext.core.provisioningUri == null) {
                val configUrl = "http://config.clb.nl/linphonerc.xml"
                coreContext.core.setProvisioningUri(configUrl)
                Log.i("[Application] Provisioning URL is not configured, set to default CLB URL: $configUrl")
            } else {
                val configUrl = coreContext.core.provisioningUri
                Log.i("[Application] Provisioning URL already configured: $configUrl")
            }
	    
            coreContext.start()
	    
	    // CLB Registration
            val registerCLB: RegisterCLB = org.linphone.clb.RegisterCLB(coreContext.context.applicationContext)
            registerCLB.RegisterReceivers()
	    
	    // Use
            Timer().schedule(2000) {
                try {
                    // CLB Forcing init of Callstate
                    Log.i("[Application] Creating CallStateCLB")
                    val instance = CallStateCLB.instance()
                    Log.i("[Application] Restarting CallStateCLB")
                    instance.Restart()
                } catch (e: Exception) {
                    Log.i("[Application] Can't start CallStateCLB $e")
                }
            }
            return true
        }

        fun contextExists(): Boolean {
            return ::coreContext.isInitialized
        }
	
	private fun CreateConfigCLB(context: Context): Config {

            // Get restrictions data (AppConfigHelper)
            val ach = AppConfigHelper(context, corePreferences)

            // Init
            android.util.Log.i("[CLB]", "Checking AppConfig data")
            ach.checkAppConfig()

            // Handle changes in LinphoneRc
            if (ach.linphoneRcHasChanges()) {
                android.util.Log.i("[CLB]", "Applying AppConfig linphoneRc changes")
                corePreferences.firstStart = false
                val linphonercData = ach.linphoneRc

                // CLB LinphoneRC changes? => Update
                // val linphonercData =
                //    this::class.java.classLoader.getResource("assets/clb_linphonerc_test").readText()
                if (LinphonePreferencesCLB.instance().UpdateFromLinphoneRcData(linphonercData, corePreferences.configPath)) {
                    LogConfig("Store AppConfig linphoneRc hash")
                    ach.storeRcHash()
                }
            } else {
                LogConfig("Hashes are equal, no changes... skipping config from bundle.")

                // Verify the 'old' method (i.e. linphonerc file in '/Downloads' folder)
                LinphonePreferencesCLB.instance().MoveLinphoneRcFromDownloads(context, corePreferences)
            }

            android.util.Log.i("[CLB]", "Create Linphone Config")
            LogConfig("Create Linphone Config")
            val config = Factory.instance().createConfigWithFactory(
                corePreferences.configPath,
                corePreferences.factoryConfigPath
            )

            // Parse/execute RC XML (pass 'null' to use AppConfig bundle check)
            if (ach.linphoneRcXmlHasChanges(null)) {
                LogConfig("Apply AppConfig Linphone Rc XML changes.")
                corePreferences.firstStart = false
                val linphonercXmlData = ach.linphoneRcXml

                if (LinphonePreferencesCLB.instance().UpdateFromLinphoneXmlData(linphonercXmlData, config)) {
                    LogConfig("Store AppConfig linphoneRc XML hash")
                    ach.storeRcXmlHash()
                }
            } else {
                LogConfig("Hashes are equal. Linphone Rc XML from bundle has no changes.")

                // TEST: CLB LinphoneRC XML changes? => Update
                // val linphonercXmlData =
                //    this::class.java.classLoader.getResource("assets/clb_linphonerc_xml_test")
                //        .readText()

                // Try 'old' method (i.e. parse linphonerc.xml file and apply changes)
                LinphonePreferencesCLB.instance().ParseLocalXmlFileConfig(config, corePreferences)
            }
            return config
        }

        private fun TestConfigClbParsing() {

            val testConfig1 = "[sip] contact=\"Linphone Android\" <sip:linphone.android@unknown-host> "
            var stringResult = "[sip]\r\ncontact=\"Linphone Android\" <sip:linphone.android@unknown-host>"
            var stringOutput = AppConfigHelper.parseLinphoneRc(testConfig1)
            var result = stringOutput.equals(stringResult)
            LogResult("testConfig1", result)

            val testConfig2 = "[sip] contact=\"Linphone Android\" <sip:linphone.android@unknown-host> use_info=0 keepalive_period=30000 sip_port=-1 sip_tcp_port=-1 sip_tls_port=-1 guess_hostname=1 register_only_when_network_is_up=1 auto_net_state_mon=0 auto_answer_replacing_calls=1 ping_with_options=0 verify_server_certs=1 verify_server_cn=1 ipv6_migration_done=1 media_encryption=none root_ca=/data/user/0/org.linphone/files/rootca.pem default_proxy=0 zrtp_cache_migration_done=1  "
            stringResult = "[sip]\r\ncontact=\"Linphone Android\" <sip:linphone.android@unknown-host>\r\nuse_info=0\r\nkeepalive_period=30000\r\nsip_port=-1\r\nsip_tcp_port=-1\r\nsip_tls_port=-1\r\nguess_hostname=1\r\nregister_only_when_network_is_up=1\r\nauto_net_state_mon=0\r\nauto_answer_replacing_calls=1\r\nping_with_options=0\r\nverify_server_certs=1\r\nverify_server_cn=1\r\nipv6_migration_done=1\r\nmedia_encryption=none\r\nroot_ca=/data/user/0/org.linphone/files/rootca.pem\r\ndefault_proxy=0\r\nzrtp_cache_migration_done=1"
            stringOutput = AppConfigHelper.parseLinphoneRc(testConfig2)
            result = stringOutput.equals(stringResult)
            LogResult("testConfig1", result)

            val testConfig3 = "[sip] contact=\"Linphone Android\" <sip:linphone.android@unknown-host> use_info=0 keepalive_period=30000 sip_port=-1 sip_tcp_port=-1 sip_tls_port=-1 guess_hostname=1 register_only_when_network_is_up=1 auto_net_state_mon=0 auto_answer_replacing_calls=1 ping_with_options=0 verify_server_certs=1 verify_server_cn=1 ipv6_migration_done=1 media_encryption=none root_ca=/data/user/0/org.linphone/files/rootca.pem default_proxy=0 zrtp_cache_migration_done=1  [video] size=cif device=Android1  [app] tunnel=disabled push_notification=1 auto_start=1 activation_code_length=4 first_launch=0 android.permission.READ_EXTERNAL_STORAGE=0 android.permission.READ_PHONE_STATE=0 android.permission.READ_CONTACTS=0 device_ringtone=1  "
            stringResult = "[sip]\r\ncontact=\"Linphone Android\" <sip:linphone.android@unknown-host>\r\nuse_info=0\r\nkeepalive_period=30000\r\nsip_port=-1\r\nsip_tcp_port=-1\r\nsip_tls_port=-1\r\nguess_hostname=1\r\nregister_only_when_network_is_up=1\r\nauto_net_state_mon=0\r\nauto_answer_replacing_calls=1\r\nping_with_options=0\r\nverify_server_certs=1\r\nverify_server_cn=1\r\nipv6_migration_done=1\r\nmedia_encryption=none\r\nroot_ca=/data/user/0/org.linphone/files/rootca.pem\r\ndefault_proxy=0\r\nzrtp_cache_migration_done=1 \r\n[video]\r\nsize=cif\r\ndevice=Android1 \r\n[app]\r\ntunnel=disabled\r\npush_notification=1\r\nauto_start=1\r\nactivation_code_length=4\r\nfirst_launch=0\r\nandroid.permission.READ_EXTERNAL_STORAGE=0\r\nandroid.permission.READ_PHONE_STATE=0\r\nandroid.permission.READ_CONTACTS=0\r\ndevice_ringtone=1"
            stringOutput = AppConfigHelper.parseLinphoneRc(testConfig3)
            result = stringOutput.equals(stringResult)
            LogResult("testConfig1", result)
        }

        private fun LogConfig(text: String) {
            android.util.Log.i("AppConfigHelper", text)
            Log.i(text)
        }

        private fun LogResult(item: String, result: Boolean) {
            var stringResult = "FAIL"
            if (result)
                stringResult = "OK"

            android.util.Log.d("[CLB]", "Parsing " + item + ": [" + stringResult + "]")
        }
    }

    override fun onCreate() {
        super.onCreate()
        val appName = getString(R.string.app_name)
        android.util.Log.i("[$appName]", "Application is being created")
        createConfig(applicationContext)
        Log.i("[Application] Created")
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
                add(SvgDecoder.Factory())
                if (Version.sdkAboveOrEqual(Version.API28_PIE_90)) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .build()
    }
}
