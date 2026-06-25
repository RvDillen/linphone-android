# CLB Overview: Linphone 6.0 Architecture and Proprietary Integration

This document consolidates the CLB analysis and migration notes into a single overview of how Linphone works in this app and where the CLB-specific code changes that behavior.

It is based on these source documents:
- CLB_FUNCTIONAL_ANALYSIS.md
- CLB_INTEGRATION_PORTING_PLAN.md
- CLB_INTEGRATION_STRATEGY.md

It is also cross-checked against the current branch implementation so it reflects the live integration points, not only the original plan.

## 1. Baseline Linphone Runtime Model

At a high level, the app follows the standard Linphone Android structure:

1. `LinphoneApplication.kt` bootstraps the app.
2. `CorePreferences.kt` exposes persisted settings and config-backed preferences.
3. `CoreContext.kt` creates and owns the Linphone Core runtime, listens to Core events, and coordinates calls, accounts, notifications, presence, and UI transitions.
4. UI screens under `ui/` and `notifications/` react to state coming from `CoreContext` and the SDK.
5. `TelecomManager.kt` bridges Linphone calls to Android Telecom where enabled.

In default Linphone behavior:
- The application creates a config and starts `CoreContext`.
- The Core emits events through `CoreListenerStub` callbacks.
- Incoming and outgoing call state changes drive UI and notifications.
- Call history is taken from Linphone `CallLog` objects.
- Settings and assistant flows are exposed through the normal UI.

## 2. What CLB Changes Conceptually

CLB extends Linphone so it can behave like a controlled telephony client embedded in CLB hardware and management workflows.

The proprietary layer adds five main capabilities:

1. Managed configuration and provisioning
2. Hardware-triggered call control without normal Linphone UI flow
3. CLB-specific call state tracking and GSM coexistence rules
4. Policy-driven UI restrictions
5. Filtering of hardware-generated calls from user-visible history

The key design principle is that Linphone still provides SIP signaling, media, account/runtime state, and most UI, while CLB injects policy and device integration at specific lifecycle seams.

## 3. Startup and Configuration Flow

The main CLB hook is at application startup.

### 3.1 Application bootstrap

`LinphoneApplication.kt` does not use the plain baseline config path anymore. Instead, it creates the config through `CreateConfigCLB(context)` before starting the core.

That function is the entry point for CLB configuration behavior:
- It creates a bootstrap config with `Factory.instance().createConfigWithFactory(...)`.
- It uses `AppConfigHelper` to inspect managed app configuration and provisioning state.
- It persists a default CLB provisioning URL if no URI is configured: `http://config.clb.nl/linphonerc.xml`.
- It applies CLB RC and XML configuration deltas through `LinphonePreferencesCLB`.
- It stores hashes to avoid reapplying unchanged remote/local config.
- It updates CLB policy values in `CorePreferences`, including settings visibility controls.

This means CLB configuration is injected before the Linphone Core becomes active, so the runtime starts from a CLB-shaped config rather than a stock one.

### 3.2 AppConfigHelper role

`org.linphone.clb.AppConfigHelper` is the CLB coordinator for managed config and provisioning.

Its responsibilities are:
- Read Android managed app restrictions / MDM-supplied values
- Detect changes in `linphonerc` and CLB XML config
- Download remote provisioning data from the CLB server
- Extract app-specific settings from provisioning data
- Push derived policy values into `CorePreferences`

The important behavioral detail is that CLB treats provisioning as more than stock Linphone remote provisioning. The CLB layer specifically cares about the proprietary `[app]` or XML app-section values and uses them to drive policy and UI restrictions.

### 3.3 LinphonePreferencesCLB role

`org.linphone.clb.LinphonePreferencesCLB` is the parser/executor for CLB config payloads.

It handles:
- Standard `linphonerc` text updates
- XML configuration updates
- Legacy local files from device storage / downloads
- Copying config values into the active Linphone config

Functionally, `AppConfigHelper` decides whether new config exists and `LinphonePreferencesCLB` applies it.

### 3.4 Post-core provisioning pass

After `CoreContext` is ready, `LinphoneApplication.kt` runs `runClbProvisioningWhenCoreReady(...)`.

That second-stage pass exists because some provisioning behavior needs a running Core. It does two notable things:
- On first launch, it ensures media encryption is not mandatory by default.
- It downloads the remote CLB provisioning content and reapplies the CLB-specific app section into a fresh config object.

So the CLB provisioning model is two-phase:
- Phase 1: bootstrap config before Core startup
- Phase 2: provisioning-driven policy/config application once the Core is alive

## 4. Runtime CLB Receiver Registration

CLB call and login/logout integration is not primarily manifest-driven.

`org.linphone.clb.RegisterCLB` dynamically registers these receivers at runtime:
- `DirectCallReceiver`
- `HangupReceiver`
- `LoginReceiver`
- `LogoutReceiver`

This is initialized from `LinphoneApplication.kt` after core startup begins.

Important detail: the class explicitly says manifest registration was not reliable enough for this integration, so the receivers are registered programmatically with exported receiver flags and high intent-filter priority.

This means the CLB hardware/control plane communicates with the app mostly through proprietary broadcast actions rather than through standard Linphone UI entry points.

## 5. Direct Hardware-Originated Outgoing Calls

The proprietary outgoing-call path starts in `org.linphone.clb.DirectCallReceiver`.

### 5.1 What happens

When CLB hardware or messenger sends `org.linphone.action.CALL`:
- The receiver extracts the target SIP URI.
- It normalizes CLB-specific URI quirks such as missing transport in `clbsessionid` URIs.
- It stores the call identity in `CallStateCLB`.
- It ensures the core service is running, starting it if necessary through `CoreContextExt`.
- Once the service is ready, it calls `coreContext.startCall(...)` directly.

### 5.2 Why this matters

This bypasses the normal user-driven dialer flow. The call is still a Linphone SIP call, but the trigger and orchestration come from CLB hardware integration.

## 6. CLB Call State Tracking and Call Arbitration

`org.linphone.clb.CallStateCLB` is the core proprietary runtime state machine.

Its purpose is to track whether a call originated from CLB hardware and to enforce special behavior for those calls.

### 6.1 Main responsibilities

`CallStateCLB`:
- Tracks the current CLB call URI and short-form identity
- Determines whether the active call should be treated as CLB-originated
- Listens to GSM / telephony state through `TelephonyManager`
- Adds a Linphone Core listener for call-state-driven CLB logic
- Prevents inappropriate UI launches for CLB-originated calls
- Terminates CLB calls when user-driven Linphone call actions should take precedence
- Tracks recent hangups to avoid unstable state transitions

### 6.2 CoreContext integration points

The current `CoreContext.kt` uses `CallStateCLB` in several critical places:

1. `OutgoingInit`
If the call is from CLB, the code avoids the normal outgoing Linphone UI path and delegates to `CoreContextExt.OnOutgoingStarted(false)`.

2. `Connected`
If the call is from CLB, the app avoids automatically showing the normal call activity.

3. `answerCall()`
Before answering a user-facing Linphone call, the app calls `CallStateCLB.instance().EndAnyCLBCall(core)`.

That last rule is important: CLB calls are not allowed to remain as a competing call when a normal answer flow takes priority.

### 6.3 GSM coexistence

`CallStateCLB` also monitors native telephony state. This exists to enforce CLB rules when GSM calls and SIP calls overlap, especially on dedicated hardware devices where call-routing policy matters more than consumer UX symmetry.

## 7. CoreContext as the Main Integration Hub

Although many CLB classes exist under `org.linphone.clb`, the real integration hub is still `CoreContext.kt`.

That is where CLB behavior is spliced into Linphone runtime events.

### 7.1 Incoming calls

For incoming SIP calls, `CoreContext` still follows the normal Linphone model:
- detect `Call.State.IncomingReceived`
- show in-call UI
- optionally auto-answer

CLB does not replace SIP call handling. Instead, it changes what happens around it in device-specific situations.

### 7.2 Outgoing calls

For outgoing calls, CLB changes the decision about whether the standard in-call UI should appear immediately.

That distinction is central to understanding the port:
- SIP/media/call lifecycle remain Linphone responsibilities
- call-origin awareness and UI suppression are CLB responsibilities

### 7.3 History cleanup inside the core layer

`CoreContext` also removes CLB hardware-generated call logs at the core-event layer when `onCallLogUpdated()` fires, and it has a dedicated `purgeHardwareGeneratedCallLogs()` helper.

So CLB history filtering is applied both reactively and when building UI models.

## 8. Notifications and Incoming Call UI

CLB modifies the incoming-call experience to favor deterministic full-screen behavior, especially for kiosk-like or locked-device scenarios.

### 8.1 NotificationsManager changes

`NotificationsManager.kt` forces the incoming call UI into full-screen on `IncomingReceived` and `IncomingEarlyMedia`.

Instead of relying on stock notification-driven behavior, it:
- explicitly launches the call UI
- forces incoming-call UI handling
- suppresses the normal incoming notification in that path

This reduces the chance that a device-specific notification policy blocks the user from seeing the incoming call screen.

### 8.2 CallActivity changes

The call activity is also configured for lock-screen visibility in the manifest:
- `android:turnScreenOn="true"`
- `android:showWhenLocked="true"`

This complements the CLB full-screen incoming-call path and supports the dedicated-device use case.

## 9. Android Telecom Integration

The app still integrates with Android Telecom through `TelecomManager.kt`, but CLB-related behavior exposed an important failure mode.

The current Telecom bridge only calls `onCallCreated(call)` when:
- an outgoing call reaches `OutgoingProgress`, or
- an incoming call reaches `Connected`

It intentionally does not register incoming ringing calls with Telecom as soon as they enter `IncomingReceived`.

The reason is documented in repo notes: on some devices, registering ringing incoming SIP calls with Android Telecom caused immediate disconnect callbacks, which effectively declined the call before the user could answer.

This is not CLB-exclusive in code terms, but it is part of the practical integration knowledge around getting CLB/Linphone call flows stable on real devices.

## 10. Settings Restriction Policy

CLB can remotely restrict parts of the Linphone UI.

### 10.1 Policy source

The policy value flows through configuration into `CorePreferences`, specifically CLB-specific settings such as `blockSettingsByPin`.

### 10.2 Enforcement point

`org.linphone.clb.ClbSettingsBlockChecker` reads that preference and returns `true` when settings should be blocked.

### 10.3 UI hook points

`DrawerMenuFragment.kt` checks this policy before allowing the user to:
- open Settings
- start the Assistant / add accounts
- navigate to account-related settings flows

This is a good example of the overall CLB pattern: configuration-driven policy is computed in the CLB layer and enforced in selected Linphone UI entry points.

## 11. Call History Filtering

CLB hides hardware-generated calls from user-visible Linphone history.

### 11.1 Detection rule

`org.linphone.clb.CallFilter` marks a call as hardware-generated when it is an outgoing call whose URI contains or starts with one of these patterns:
- `clbinfo`
- `clbsessionid`
- `sip:ext1@`
- `sip:ext2@`

### 11.2 Filtering points

This filtering is used in multiple places:
- `CoreContext.kt` removes matching call logs from the core log store
- `HistoryListViewModel.kt` skips them when building the list view
- `HistoryViewModel.kt` skips them when building per-address history

The net effect is that device-generated signaling calls remain operationally useful but are hidden from end users as noise.

## 12. Branding and Product Flavors

The app defines multiple product flavors in `build.gradle.kts`:
- `linphone`
- `clb`
- `clbTypeM`
- `clbConfig`

The CLB flavors primarily change packaging and branding around the same Linphone runtime plus CLB integration layer.

Current naming aligned with the older 5.2 CLB branch is:
- `clb` -> `Linphone CLB`
- `clbTypeM` -> `Linphone M CLB`
- `clbConfig` -> `Linphone CLB`

## 13. End-to-End Flow Summary

### 13.1 App startup

1. Application creates a CLB-aware config.
2. CLB managed config and provisioning values are applied before Core startup.
3. Core starts.
4. CLB receivers are registered dynamically.
5. `CallStateCLB` is started after a short delay.
6. Post-core CLB provisioning/app policy is applied.

### 13.2 Hardware-triggered outgoing call

1. CLB broadcast arrives at `DirectCallReceiver`.
2. Receiver normalizes the URI and records CLB call identity.
3. Core service is started if needed.
4. `coreContext.startCall(...)` places a Linphone SIP call.
5. `CoreContext` detects that the call is CLB-originated and suppresses the normal outgoing UI path.

### 13.3 Normal incoming SIP call

1. Linphone Core reports `IncomingReceived`.
2. `CoreContext` shows call UI and may auto-answer if configured.
3. `NotificationsManager` forces full-screen incoming UI to avoid device-specific notification failures.
4. If the user answers a normal call while a CLB call exists, CLB calls are terminated first.

### 13.4 History rendering

1. Linphone stores call logs.
2. CLB filters remove hardware-generated entries.
3. User-facing history only shows calls relevant to the person using the app.

## 14. Mental Model for Future Changes

The safest way to reason about this codebase is:

- Linphone owns SIP, media, Core lifecycle, most account behavior, and most UI.
- CLB owns managed provisioning, hardware broadcast integration, dedicated-device policy, call-origin tracking, and suppression/filtering rules.
- `LinphoneApplication.kt` and `CoreContext.kt` are the highest-value files because they are where CLB actually hooks into the normal Linphone runtime.

If a future issue is about startup behavior, provisioning, or settings policy, start in:
- `LinphoneApplication.kt`
- `AppConfigHelper.java`
- `LinphonePreferencesCLB.java`
- `CorePreferences.kt`

If it is about calls, activity launching, or device coexistence, start in:
- `CoreContext.kt`
- `CallStateCLB.java`
- `DirectCallReceiver.java`
- `NotificationsManager.kt`
- `TelecomManager.kt`

If it is about hidden calls or user-visible history, start in:
- `CallFilter.java`
- `HistoryListViewModel.kt`
- `HistoryViewModel.kt`
- `CoreContext.kt`

## 15. Bottom Line

CLB does not replace Linphone. It wraps Linphone with a proprietary control layer tailored for CLB-managed devices.

The core pattern is consistent across the codebase:
- let Linphone do the SIP and media work
- intercept lifecycle points where device policy matters
- inject CLB config before or around Core operations
- suppress or reroute stock UI when calls originate from CLB hardware
- remove operational artifacts from user-facing history

That is the architectural overview to keep in mind when maintaining or extending the 6.0 CLB branch.