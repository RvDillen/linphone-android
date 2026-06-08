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
package org.linphone

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.os.PowerManager
import androidx.annotation.MainThread
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.imageLoader
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import coil3.video.VideoFrameDecoder
import com.google.android.material.color.DynamicColors
import java.util.*
import kotlin.concurrent.schedule
import org.linphone.clb.AppConfigHelper
import org.linphone.clb.CallStateCLB
import org.linphone.clb.LinphonePreferencesCLB
import org.linphone.clb.RegisterCLB
import org.linphone.compatibility.Compatibility
import org.linphone.core.Config
import org.linphone.core.CoreContext
import org.linphone.core.CorePreferences
import org.linphone.core.Factory
import org.linphone.core.LogCollectionState
import org.linphone.core.LogLevel
import org.linphone.core.VFS
import org.linphone.core.tools.Log

@MainThread
class LinphoneApplication : Application(), SingletonImageLoader.Factory {
    companion object {
        private const val TAG = "[Linphone Application]"

        @SuppressLint("StaticFieldLeak")
        lateinit var corePreferences: CorePreferences

        @SuppressLint("StaticFieldLeak")
        lateinit var coreContext: CoreContext
    }

    override fun onCreate() {
        super.onCreate()
        val context = applicationContext

        val powerManager = context.getSystemService(POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Linphone:AppCreation"
        )
        wakeLock.acquire(20000L) // 20 seconds

        Factory.instance().setLogCollectionPath(context.filesDir.absolutePath)
        Factory.instance().enableLogCollection(LogCollectionState.Enabled)
        // For VFS
        Factory.instance().setCacheDir(context.cacheDir.absolutePath)

        corePreferences = CorePreferences(context)
        corePreferences.copyAssetsFromPackage()

        if (VFS.isEnabled(context)) {
            VFS.setup(context)
        }

        // CLB: Create config with CLB customizations
        val config = CreateConfigCLB(context)
        corePreferences.config = config

        val appName = context.getString(R.string.app_name)
        Factory.instance().setLoggerDomain(appName)
        Factory.instance().loggingService.setLogLevel(LogLevel.Message)
        Factory.instance().enableLogcatLogs(corePreferences.printLogsInLogcat)

        Log.i("$TAG Report Core preferences initialized")
        Compatibility.setupAppStartupListener(context)

        coreContext = CoreContext(context)
        coreContext.start()

        // CLB: Always force background-mode to be enabled
        Log.i("$TAG Force 'background-mode' to 'enabled'.")
        corePreferences.keepServiceAlive = true

        // CLB: Provisioning uses core; run it only once core initialization is complete.
        runClbProvisioningWhenCoreReady(context)

        // CLB: Register CLB account receivers
        val registerCLB: RegisterCLB = RegisterCLB(context.applicationContext)
        registerCLB.RegisterReceivers()

        // CLB: Initialize CallStateCLB with delay to ensure core is ready
        Timer().schedule(2000) {
            try {
                Log.i("$TAG Creating CallStateCLB")
                val instance = CallStateCLB.instance()
                Log.i("$TAG Restarting CallStateCLB")
                instance.Restart()
            } catch (e: Exception) {
                Log.i("$TAG Can't start CallStateCLB $e")
            }
        }

        DynamicColors.applyToActivitiesIfAvailable(this)
        wakeLock.release()
    }

    override fun onTrimMemory(level: Int) {
        Log.w("$TAG onTrimMemory called with level [${trimLevelToString(level)}]($level) !")
        when (level) {
            TRIM_MEMORY_RUNNING_LOW,
            TRIM_MEMORY_RUNNING_CRITICAL,
            TRIM_MEMORY_MODERATE,
            TRIM_MEMORY_COMPLETE -> {
                Log.i("$TAG Memory trim required, clearing imageLoader memory cache")
                imageLoader.memoryCache?.clear()
            }
            else -> {}
        }
        super.onTrimMemory(level)
    }

    override fun newImageLoader(context: Context): ImageLoader {
        // When VFS is enabled, prevent Coil from keeping plain version of files on disk
        val diskCachePolicy = if (VFS.isEnabled(applicationContext)) {
            CachePolicy.DISABLED
        } else {
            CachePolicy.ENABLED
        }

        return ImageLoader.Builder(this)
            .crossfade(false)
            .components {
                add(VideoFrameDecoder.Factory())
                // add(GifDecoder.Factory) // Do not add it, GIFs are properly rendered without it and adding it breaks resizing...
                add(SvgDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                val cache = cacheDir.resolve("image_cache")
                DiskCache.Builder()
                    .directory(cache)
                    .maxSizePercent(0.02)
                    .build()
            }
            .networkCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(diskCachePolicy)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    private fun trimLevelToString(level: Int): String {
        return when (level) {
            TRIM_MEMORY_UI_HIDDEN -> "Hidden UI"
            TRIM_MEMORY_RUNNING_MODERATE -> "Moderate (Running)"
            TRIM_MEMORY_RUNNING_LOW -> "Low"
            TRIM_MEMORY_RUNNING_CRITICAL -> "Critical"
            TRIM_MEMORY_BACKGROUND -> "Background"
            TRIM_MEMORY_MODERATE -> "Moderate"
            TRIM_MEMORY_COMPLETE -> "Complete"
            else -> level.toString()
        }
    }

    // CLB: Create Linphone config with CLB customizations (MDM, app section, etc)
    private fun CreateConfigCLB(context: Context): Config {
        // Get restrictions data from MDM (AppConfigHelper)
        val ach = AppConfigHelper(context, corePreferences)

        // AppConfigHelper reads CLB values through CorePreferences before coreContext exists.
        // Bootstrap the config now so CorePreferences doesn't fall back to coreContext.core.config.
        val config = Factory.instance().createConfigWithFactory(
            corePreferences.configPath,
            corePreferences.factoryConfigPath
        )
        corePreferences.config = config

        // Check for MDM restrictions and app config changes
        android.util.Log.i("[CLB]", "Checking AppConfig data")
        ach.checkAppConfig()

        // Handle changes in LinphoneRc from MDM
        var configShouldBeUpdated = false
        if (ach.linphoneRcHasChanges()) {
            android.util.Log.i("[CLB]", "Applying AppConfig linphoneRc changes")

            val linphonercData = ach.linphoneRc

            if (LinphonePreferencesCLB.instance().UpdateFromLinphoneRcData(
                    linphonercData,
                    corePreferences.configPath
                )
            ) {
                LogConfig("Store AppConfig linphoneRc hash")
                ach.storeRcHash()
                configShouldBeUpdated = true
            }
        } else {
            LogConfig("Hashes are equal, no changes... skipping config from bundle.")

            // Verify the 'old' method (i.e. linphonerc file in '/Downloads' folder)
            LinphonePreferencesCLB.instance().MoveLinphoneRcFromDownloads(
                context,
                corePreferences
            )
        }

        // Reuse the bootstrapped base Linphone config.
        android.util.Log.i("[CLB]", "Create Linphone Config")
        LogConfig("Create Linphone Config")

        // Parse/execute RC XML (app-specific section)
        if (ach.linphoneRcXmlHasChanges(null)) {
            LogConfig("Apply AppConfig Linphone Rc XML changes.")

            val linphonercXmlData = ach.linphoneRcXml

            if (LinphonePreferencesCLB.instance().UpdateFromLinphoneXmlData(
                    linphonercXmlData,
                    config
                )
            ) {
                LogConfig("Store AppConfig linphoneRc XML hash")
                ach.storeRcXmlHash()
                configShouldBeUpdated = true
            }
        } else {
            LogConfig("Hashes are equal. Linphone Rc XML from bundle has no changes.")

            // Try 'old' method (i.e. parse linphonerc.xml file and apply changes)
            if (LinphonePreferencesCLB.instance().ParseLocalXmlFileConfig(config, corePreferences)) {
                // If there were any config changes, also update the 'show_settings' value
                ach.updateShowSettingsToCorePreferences(config)
            }
        }

        if (configShouldBeUpdated) {
            ach.updateShowSettingsToCorePreferences(config)
        }

        return config
    }

    private fun LogConfig(text: String) {
        android.util.Log.i("[AppConfigHelper]", text)
        Log.i(text)
    }

    private fun runClbProvisioningWhenCoreReady(context: Context, attempt: Int = 0) {
        if (!coreContext.isReady()) {
            if (attempt >= 40) {
                Log.e("$TAG Core is still not ready after ${attempt + 1} attempts, skipping CLB provisioning check at startup")
                return
            }

            Timer().schedule(250) {
                coreContext.postOnMainThread {
                    runClbProvisioningWhenCoreReady(context, attempt + 1)
                }
            }
            return
        }

        if (coreContext.core.provisioningUri == null) {
            val configUrl = "http://config.clb.nl/linphonerc.xml"
            coreContext.core.setProvisioningUri(configUrl)
            Log.i("$TAG Provisioning URL is not configured, set to default CLB URL: $configUrl")
        } else {
            val configUrl = coreContext.core.provisioningUri
            Log.i("$TAG Provisioning URL already configured: $configUrl")
        }

        if (coreContext.core.provisioningUri != null) {
            val ach = AppConfigHelper(context, corePreferences)
            val provisioningPath = coreContext.core.provisioningUri
            val contents = ach.checkRemoteProvisioning(false, coreContext, provisioningPath)

            if (contents != null && contents.length > 0) {
                Log.i("$TAG Applying [app] section provisioning config...")
                val remoteConfig = Factory.instance().createConfigWithFactory(
                    corePreferences.configPath,
                    corePreferences.factoryConfigPath
                )

                if (LinphonePreferencesCLB.instance().UpdateFromLinphoneXmlData(contents, remoteConfig)) {
                    ach.updateShowSettingsToCorePreferences(remoteConfig)
                }
            }
        }
    }
}
