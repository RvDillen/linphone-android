import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension
import com.google.gms.googleservices.GoogleServicesPlugin
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kapt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.navigation)
    alias(libs.plugins.crashlytics)
}

val packageName = "org.linphone"

static def getPackageName() {
    return "nl.clb.linphone"
}

static def getPackageNameOrg() {
    return "org.linphone"
}

static def getPackageNameTypeM() {
    return "nl.clb.typem.linphone"
}

static def getPackageNameConfig() {
    return "nl.clb.linphoneconfig"
}

val useDifferentPackageNameForDebugBuild = false
val versionMajor = 6
val versionMinor = 0
val versionRelease = 1
val jenkinsBuildNumber = System.getenv("BUILD_NUMBER")?.toIntOrNull() ?: 0
val appVersionName = "$versionMajor.$versionMinor.$versionRelease.$jenkinsBuildNumber"
val appVersionCode =
    (versionMajor * 1000000) +
        (versionMinor * 100000) +
        (versionRelease * 10000) +
        jenkinsBuildNumber

val sdkPath = providers.gradleProperty("LinphoneSdkBuildDir").get()
val googleServices = File(projectDir.absolutePath + "/google-services.json")
val linphoneLibs = File("$sdkPath/libs/")
val linphoneDebugLibs = File("$sdkPath/libs-debug/")
val firebaseCloudMessagingAvailable = googleServices.exists()
val crashlyticsAvailable = googleServices.exists() && linphoneLibs.exists() && linphoneDebugLibs.exists()

if (firebaseCloudMessagingAvailable) {
    println("google-services.json found, enabling CloudMessaging feature")
    apply<GoogleServicesPlugin>()
} else {
    println("google-services.json not found, disabling CloudMessaging feature")
}

var gitBranch = ByteArrayOutputStream()
var gitVersion = "6.0.23"

task("getGitVersion") {
    def versionBuild = jenkinsBuildNumber
    project.version = String.format("%d.%d.%d.%d", versionMajor, versionMinor, versionRelease, versionBuild)
    /*
    val gitVersionStream = ByteArrayOutputStream()
    val gitCommitsCount = ByteArrayOutputStream()
    val gitCommitHash = ByteArrayOutputStream()

    try {
        exec {
            commandLine("git", "describe", "--abbrev=0")
            standardOutput = gitVersionStream
        }
        exec {
            commandLine(
                "git",
                "rev-list",
                gitVersionStream.toString().trim() + "..HEAD",
                "--count",
            )
            standardOutput = gitCommitsCount
        }
        exec {
            commandLine("git", "rev-parse", "--short", "HEAD")
            standardOutput = gitCommitHash
        }
        exec {
            commandLine("git", "name-rev", "--name-only", "HEAD")
            standardOutput = gitBranch
        }

        gitVersion =
            if (gitCommitsCount.toString().trim().toInt() == 0) {
                gitVersionStream.toString().trim()
            } else {
                gitVersionStream.toString().trim() + "." +
                    gitCommitsCount.toString()
                        .trim() + "+" + gitCommitHash.toString().trim()
            }
        println("Git version: $gitVersion")
    } catch (e: Exception) {
        println("Git not found [$e], using $gitVersion")
    }

    project.version = gitVersion
    */
}
project.tasks.preBuild.dependsOn("getGitVersion")

configurations {
    implementation { isCanBeResolved = true }
}
task("linphoneSdkSource") {
    doLast {
        configurations.implementation.get().incoming.resolutionResult.allComponents.forEach {
            if (it.id.displayName.contains("linphone-sdk-android")) {
                println("Linphone SDK used is ${it.moduleVersion?.version}")
            }
        }
    }
}
project.tasks.preBuild.dependsOn("linphoneSdkSource")

android {
    namespace = "org.linphone"
    compileSdk = 35

    defaultConfig {
        applicationId = getPackageName()
        minSdk = 28
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        setProperty("archivesBaseName", "$applicationId-$versionName")

        manifestPlaceholders["appAuthRedirectScheme"] = packageName

        ndk {
            //noinspection ChromeOsAbiSupport
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    productFlavors {
        clb {
            dimension "all"
            applicationId getPackageName()
        }
        clbTypeM {
            dimension "all"
            applicationId getPackageNameTypeM()
        }
        clbConfig {
            dimension "all"
            applicationId getPackageNameConfig()
        }
        linphone {
            dimension "all"
            applicationId getPackageNameOrg()
        }
    }

    applicationVariants.all { variant ->
        variant.outputs.all {
            outputFileName = "linphone-android-${variant.getFlavorName()}-${variant.buildType.name}_${variant.versionName}.apk"
        }

        var enableFirebaseService = "false"
        if (firebaseAvailable) {
            enableFirebaseService = "true"
        }
/*
    // LINPHONE 5.2 build variant/flavour script
        // See https://developer.android.com/studio/releases/gradle-plugin#3-6-0-behavior for why extractNativeLibs is set to true in debug flavor
	    // If this throws errors: ExtractNativeLibs might have to be removed!
        if (variant.buildType.name == "release" || variant.buildType.name == "releaseWithCrashlytics") {
            if (variant.getFlavorName() == "clbTypeM") {

                // Special appLabel for typeM build
                // Verify/Test on API34 device: Is it a 'problem' that the root-package-name is used instead of the actual package name for address_mime_type and file_provider?
                variant.getMergedFlavor().manifestPlaceholders = [linphone_address_mime_type: "vnd.android.cursor.item/vnd." + getPackageName() + ".provider.sip_address",
                                                                  linphone_file_provider    : getPackageName() + ".fileprovider",
                                                                  appLabel                  : "@string/app_name_typem",
                                                                  firebaseServiceEnabled    : enableFirebaseService,
                                                                  extractNativeLibs         : "false"]
            }
            else {
                variant.getMergedFlavor().manifestPlaceholders = [linphone_address_mime_type: "vnd.android.cursor.item/vnd." + getPackageName() + ".provider.sip_address",
                                                                  linphone_file_provider    : getPackageName() + ".fileprovider",
                                                                  appLabel                  : "@string/app_name",
                                                                  firebaseServiceEnabled    : enableFirebaseService,
                                                                  extractNativeLibs         : "false"]
            }
        } else {
            variant.getMergedFlavor().manifestPlaceholders = [linphone_address_mime_type: "vnd.android.cursor.item/vnd." + getPackageName() + ".provider.sip_address",
                                                              linphone_file_provider    : getPackageName() + ".debug.fileprovider",
                                                              appLabel                  : "@string/app_name_debug",
                                                              firebaseServiceEnabled    : enableFirebaseService,
                                                              extractNativeLibs         : "true" ]
        }
    }
*/

// ORIGINAL 6.0 aplication.variants method
    applicationVariants.all {
        val variant = this
        val flavorOutputName = variant.flavorName.takeIf { it.isNotBlank() } ?: "linphone"
        val variantNameCap = variant.name.replaceFirstChar { it.uppercaseChar() }

        val defaultApkOutputDir =
            project.layout.buildDirectory.dir("outputs/apk/${variant.buildType.name}")
        val flavorApkOutputDir =
            project.layout.buildDirectory.dir("outputs/apk/$flavorOutputName/${variant.buildType.name}")

        variant.outputs
            .map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
            .forEach { output ->
                output.outputFileName =
                    "linphone-android-$flavorOutputName-${variant.buildType.name}_${variant.versionName}.apk"
            }

        val copyApkOutputsTask = tasks.register<Copy>("copy${variantNameCap}ApkOutputsToFlavorDir") {
            from(defaultApkOutputDir)
            into(flavorApkOutputDir)
        }
        variant.assembleProvider.configure {
            finalizedBy(copyApkOutputsTask)
        }
    }

    val keystoreProperties = Properties()
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))
    }

    fun signingProperty(name: String): String? =
        providers.gradleProperty(name).orNull ?: keystoreProperties.getProperty(name)

    signingConfigs {
        create("release") {
            val keyStorePath = signingProperty("storeFile")
            val storePasswordValue = signingProperty("storePassword")
            val keyAliasValue = signingProperty("keyAlias")
            val keyPasswordValue = signingProperty("keyPassword")

            if (
                keyStorePath != null &&
                storePasswordValue != null &&
                keyAliasValue != null &&
                keyPasswordValue != null
            ) {
                val keyStore = project.file(keyStorePath)
                if (keyStore.exists()) {
                    storeFile = keyStore
                    storePassword = storePasswordValue
                    keyAlias = keyAliasValue
                    keyPassword = keyPasswordValue
                    println("Signing config release is using keystore [$storeFile]")
                } else {
                    println("Keystore [$keyStore] doesn't exist!")
                }
            } else {
                println("Release signing config properties are missing, APKs will be unsigned")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (useDifferentPackageNameForDebugBuild) {
                applicationIdSuffix = ".debug"
            }
            isDebuggable = true
            isJniDebuggable = true

            if (useDifferentPackageNameForDebugBuild) {
                resValue("string", "file_provider", getPackageName() + ".debug.fileprovider")
            } else {
                resValue("string", "file_provider", getPackageName() + ".fileprovider")
            }
            resValue("string", "linphone_app_version", gitVersion.trim())
            resValue("string", "linphone_app_branch", gitBranch.toString().trim())
            resValue("string", "linphone_openid_callback_scheme", getPackageName())

            if (crashlyticsAvailable) {
                val path = File("$sdkPath/libs-debug/").toString()
                configure<CrashlyticsExtension> {
                    nativeSymbolUploadEnabled = true
                    unstrippedNativeLibsDir = path
                }
            }
            buildConfigField("Boolean", "CRASHLYTICS_ENABLED", crashlyticsAvailable.toString())
        }

        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")

            resValue("string", "file_provider", getPackageName() + ".fileprovider")
            resValue("string", "linphone_app_version", gitVersion.trim())
            resValue("string", "linphone_app_branch", gitBranch.toString().trim())
            resValue("string", "linphone_openid_callback_scheme", getPackageName())

            if (crashlyticsAvailable) {
                val path = File("$sdkPath/libs-debug/").toString()
                configure<CrashlyticsExtension> {
                    nativeSymbolUploadEnabled = true
                    unstrippedNativeLibsDir = path
                }
            }
            buildConfigField("Boolean", "CRASHLYTICS_ENABLED", crashlyticsAvailable.toString())
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        dataBinding = true
        buildConfig = true
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation(libs.androidx.annotations)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraint.layout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.telecom)
    implementation(libs.androidx.media)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.slidingpanelayout)
    implementation(libs.androidx.window)
    implementation(libs.androidx.gridlayout)
    implementation(libs.androidx.security.crypto.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.emoji2)
    implementation(libs.androidx.car)

    // https://github.com/google/flexbox-layout/blob/main/LICENSE Apache v2.0
    implementation(libs.google.flexbox)
    // https://github.com/material-components/material-components-android/blob/master/LICENSE Apache v2.0
    implementation(libs.google.material)
    // To be able to parse native crash tombstone and print them with SDK logs the next time the app will start
    implementation(libs.google.protobuf)

    implementation(platform(libs.google.firebase.bom))
    implementation(libs.google.firebase.messaging)
    implementation(libs.google.firebase.crashlytics)

    // https://github.com/coil-kt/coil/blob/main/LICENSE.txt Apache v2.0
    implementation(libs.coil)
    implementation(libs.coil.gif)
    implementation(libs.coil.svg)
    implementation(libs.coil.video)
    // https://github.com/tommybuonomo/dotsindicator/blob/master/LICENSE Apache v2.0
    implementation(libs.dots.indicator)
    // https://github.com/Baseflow/PhotoView/blob/master/LICENSE Apache v2.0
    implementation(libs.photoview)
    // https://github.com/openid/AppAuth-Android/blob/master/LICENSE Apache v2.0
    implementation(libs.openid.appauth)

    implementation(libs.linphone)
}

configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
    android.set(true)
    ignoreFailures.set(true)
    additionalEditorconfig.set(
        mapOf(
            "max_line_length" to "120",
            "ktlint_standard_max-line-length" to "disabled",
            "ktlint_standard_function-signature" to "disabled",
            "ktlint_standard_no-blank-line-before-rbrace" to "disabled",
            "ktlint_standard_no-empty-class-body" to "disabled",
            "ktlint_standard_annotation-spacing" to "disabled",
            "ktlint_standard_class-signature" to "disabled",
            "ktlint_standard_function-expression-body" to "disabled",
            "ktlint_standard_function-type-modifier-spacing" to "disabled",
            "ktlint_standard_if-else-wrapping" to "disabled",
            "ktlint_standard_argument-list-wrapping" to "disabled",
            "ktlint_standard_trailing-comma-on-call-site" to "disabled",
            "ktlint_standard_trailing-comma-on-declaration-site" to "disabled",
            "ktlint_standard_no-empty-first-line-in-class-body" to "disabled",
            "ktlint_standard_no-empty-first-line-in-method-block" to "disabled",
            "ktlint_standard_no-trailing-spaces" to "disabled",
            "ktlint_standard_no-blank-line-in-list" to "disabled",
            "ktlint_standard_no-multi-spaces" to "disabled",
            "ktlint_standard_try-catch-finally-spacing" to "disabled",
            "ktlint_standard_block-comment-initial-star-alignment" to "disabled",
            "ktlint_standard_spacing-between-declarations-with-comments" to "disabled",
            "ktlint_standard_no-consecutive-comments" to "disabled",
            "ktlint_standard_multiline-expression-wrapping" to "disabled",
            "ktlint_standard_parameter-list-wrapping" to "disabled",
            "ktlint_standard_comment-wrapping" to "disabled",
            "ktlint_standard_discouraged-comment-location" to "disabled",
            "ktlint_standard_string-template-indent" to "disabled",
            "ktlint_standard_parameter-list-spacing" to "disabled",
            "ktlint_standard_statement-wrapping" to "disabled",
            "ktlint_standard_import-ordering" to "disabled",
            "ktlint_standard_paren-spacing" to "disabled",
            "ktlint_standard_curly-spacing" to "disabled",
            "ktlint_standard_indent" to "disabled",
        )
    )
}
project.tasks.preBuild.dependsOn("ktlintFormat")

afterEvaluate {
    listOf("Release").forEach { buildTypeName ->
        val assembleTask = tasks.findByName("assemble$buildTypeName")
        val bundleTask = tasks.findByName("bundle$buildTypeName")
        if (assembleTask != null && bundleTask != null) {
            assembleTask.finalizedBy(bundleTask)
        }
    }
}

if (crashlyticsAvailable) {
    afterEvaluate {
        tasks.getByName("assembleDebug").finalizedBy(
            tasks.getByName("uploadCrashlyticsSymbolFileDebug"),
        )
        tasks.getByName("packageDebug").finalizedBy(
            tasks.getByName("uploadCrashlyticsSymbolFileDebug"),
        )
        tasks.getByName("assembleRelease").finalizedBy(
            tasks.getByName("uploadCrashlyticsSymbolFileRelease"),
        )
        tasks.getByName("packageRelease").finalizedBy(
            tasks.getByName("uploadCrashlyticsSymbolFileRelease"),
        )
    }
}
