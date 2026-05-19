# Linphone Android — Agent Instructions

Linphone is a Kotlin-based Android VoIP softphone (SIP) by Belledonne Communications. It is dual-licensed (GPLv3 / commercial). The app has been fully rewritten in Kotlin using MVVM, Data Binding, and Jetpack Navigation.

## Build Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK (requires signing config)
./gradlew installDebug           # Build + install debug APK on connected device
./gradlew bundleRelease          # Build release AAB (requires ANDROID_NDK_HOME for symbols)
./gradlew ktlintFormat           # Format Kotlin code (also runs automatically on preBuild)
./gradlew ktlintCheck            # Check formatting without fixing
./gradlew linphoneSdkSource      # Print which Linphone SDK version/repo is being used
```

APK output: `app/build/outputs/apk/<flavor>/<buildType>/`

**JVM heap**: `org.gradle.jvmargs=-Xmx8192m` — builds are memory-intensive.

### Local SDK (optional)
To use a locally built linphone-sdk instead of Maven, set `LinphoneSdkBuildDir` in `~/.gradle/gradle.properties` (see [README.md](README.md#building-a-local-sdk)).

## Project Structure

```
app/src/main/java/org/linphone/
├── activities/
│   ├── assistant/      # Account setup / onboarding (fragments, viewmodels, adapters)
│   ├── main/           # Main UI: chat, contacts, dialer, history, settings, conference
│   ├── voip/           # Active call/video UI (CallActivity)
│   ├── launcher/       # Splash/startup
│   ├── chat_bubble/    # Notification bubbles
│   ├── GenericActivity.kt   # Base Activity
│   └── GenericFragment.kt   # Base Fragment
├── core/
│   ├── CoreContext.kt       # Central singleton: SDK lifecycle, call routing, event dispatch
│   ├── CoreService.kt       # Foreground service keeping the core alive
│   └── CorePreferences.kt   # Shared preferences wrapper
├── contact/            # ContactsManager, NativeContactEditor, avatar generation
├── notifications/      # NotificationsManager (calls, messages, missed events)
├── telecom/            # Android Telecom API integration
├── compatibility/      # API-level branching (Compat classes per SDK version)
├── utils/              # Stateless helpers (AudioRouteUtils, FileUtils, PermissionHelper…)
├── views/              # Custom views
├── clb/                # CLB-flavour extensions
└── LinphoneApplication.kt   # Application entry point
```

## Architecture

**MVVM** with Android Data Binding.

- **Fragment** (View) ← Data Binding → **ViewModel** ← **CoreContext** (Linphone SDK)
- Fragments extend `GenericFragment`. ViewModels extend `ViewModel` or `MessageNotifierViewModel`.
- Navigation via Jetpack Navigation component; nav graph in `res/navigation/`.
- `CoreContext` is the central singleton for all SDK interaction — do not bypass it to call the Linphone SDK directly.

## Product Flavors

| Flavor | Application ID | Notes |
|--------|---------------|-------|
| `linphone` | `org.linphone` | Official upstream Linphone app |
| `clb` | `nl.clb.linphone` | CLB branded variant |
| `clbTypeM` | `nl.clb.typem.linphone` | CLB Type-M variant |
| `clbConfig` | `nl.clb.linphoneconfig` | CLB configurator app |

Build targets combine flavor + build type, e.g. `assembleClbDebug`, `bundleLinphoneRelease`.

## SDK Versions

- Compile / Target: **API 34** (Android 14)
- Min: **API 24** (Android 7.0)
- JVM toolchain: **Java 21**
- Linphone SDK: **5.3.47** (fetched from Maven; see `settings.gradle` for repos)

## Key Libraries

| Library | Purpose |
|---------|---------|
| `org.linphone:linphone-sdk-android` | SIP/VoIP engine (audio, video, IM, presence) |
| Jetpack Navigation | Fragment navigation |
| AndroidX Data Binding (kapt) | View–ViewModel binding |
| Coil 2 (+ gif/svg/video) | Image loading |
| Firebase (optional) | Push notifications (FCM), Crashlytics NDK |
| Material Components | UI |
| `androidx.security:security-crypto-ktx` | Encrypted SharedPreferences |

Firebase is **optional**: if `app/google-services.json` is absent the build skips FCM and Crashlytics.

## Code Conventions

- **100% Kotlin**; no Java sources.
- KTLint (official Kotlin style) runs automatically on every `preBuild` — always commit formatted code.
- Naming: `*Activity`, `*Fragment`, `*ViewModel`, `*Adapter`. Base classes prefixed `Generic` or `Abstract`.
- Compatibility shims go in `compatibility/` — add a new `Compat` class per API level rather than inline `Build.VERSION.SDK_INT` checks.
- No automated test suite exists; testing is manual on devices/emulators.

## Common Pitfalls

- **`libc++_shared.so` crash on startup** → Clean project (`Build > Clean Project`) and rebuild.
- **Release build requires signing**: `keystore.properties` must be populated (see `app/build.gradle` signingConfigs). For local debug builds the debug keystore is used automatically.
- **NDK required for `bundleRelease`**: set `ANDROID_NDK_HOME` env var before building an AAB.
- **Native debugging**: switch to the debug AAR in `app/build.gradle` (commented section) and configure LLDB in Android Studio.
- **Linphone SDK API**: all interactions with the SDK must go through `CoreContext`; it is not thread-safe — use `coreThread` dispatcher where required.
