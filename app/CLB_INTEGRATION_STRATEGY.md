# Intelligent CLB Integration Strategy for Linphone 6.0

**Branch:** feature/clb-6.0-integration  
**Status:** Strategy Documentation  
**Date:** 2026-06-05

---

## EXECUTIVE SUMMARY

Rather than mechanically applying 2267 lines of diffs, we are taking an **intelligent, function-driven approach**:

1. **Understand what CLB code DOES** (functional intent, not just code)
2. **Map to 6.0 equivalent architecture** (may have different structure)
3. **Design minimal integration points** (inject CLB logic at right locations)
4. **Update APIs for SDK 5.5** (handle 5.3.47→5.5 breaking changes)
5. **Test each integration systematically**

---

## BRANCHING STRATEGY

```
release/6.0 (clean Linphone 6.0)
  ↓
feature/clb-6.0-integration (our working branch)
  - Commit 1: Add CLB folder (done ✓)
  - Commit 2: Config integration (LinphoneApplication.kt)
  - Commit 3: Call management (CoreContext.kt hooks)
  - Commit 4: Service lifecycle (CoreContextExt integration)
  - Commit 5: Broadcast receivers (DirectCallReceiver, HangupReceiver, etc.)
  - Commit 6: Settings & History (CallFilter, ClbSettingsBlockChecker)
  - Commit 7: SDK 5.5 API updates (RegisterCLB, etc.)
  - Commit 8: Resource files (manifest, strings, etc.)
  - Commit 9: Testing & fixes
  ↓
feature/sdk-5.5-migration (future - will merge this branch)
```

---

## INTEGRATION APPROACH BY PRIORITY

### PRIORITY 1: Configuration Management (HIGH IMPACT)

**Files to modify:**
- `src/main/java/org/linphone/LinphoneApplication.kt`

**Changes needed:**
1. Import CLB classes:
   ```kotlin
   import org.linphone.clb.AppConfigHelper
   import org.linphone.clb.LinphonePreferencesCLB
   import org.linphone.clb.RegisterCLB
   import org.linphone.clb.CallStateCLB
   ```

2. Replace config creation in `createConfig()` companion function:
   ```kotlin
   // BEFORE (6.0 baseline):
   val config = Factory.instance().createConfigWithFactory()
   
   // AFTER (with CLB):
   val config = CreateConfigCLB(context)
   ```

3. Add `CreateConfigCLB()` function:
   ```kotlin
   private fun CreateConfigCLB(context: Context): Config {
       // Get restrictions data (AppConfigHelper)
       val ach = AppConfigHelper(context, corePreferences)
       // ... (copy from 5.2.xclb LinphoneApplication.kt lines 195-250)
   }
   ```

4. Add provisioning URL setup in `ensureCoreExists()`:
   ```kotlin
   if (coreContext.core.provisioningUri == null) {
       val configUrl = "http://config.clb.nl/linphonerc.xml"
       coreContext.core.setProvisioningUri(configUrl)
   }
   ```

5. Add remote provisioning check:
   ```kotlin
   val ach = AppConfigHelper(context, corePreferences)
   val contents = ach.checkRemoteProvisioning(false, coreContext, ...)
   if (contents != null && contents.length > 0) {
       if (LinphonePreferencesCLB.instance().UpdateFromLinphoneXmlData(...)) {
           ach.updateShowSettingsToCorePreferences(config)
       }
   }
   ```

6. Add RegisterCLB initialization:
   ```kotlin
   val registerCLB: RegisterCLB = RegisterCLB(coreContext.context.applicationContext)
   registerCLB.RegisterReceivers()
   ```

7. Add CallStateCLB delayed initialization:
   ```kotlin
   Timer().schedule(2000) {
       try {
           val instance = CallStateCLB.instance()
           instance.Restart()
       } catch (e: Exception) {
           Log.i("[Application] Can't start CallStateCLB $e")
       }
   }
   ```

**Validation:**
- [ ] Provisioning URL is set correctly
- [ ] Config can be created without errors
- [ ] AppConfigHelper runs without exceptions
- [ ] RegisterCLB receivers are registered

---

### PRIORITY 2: Call Management Integration (CRITICAL)

**Files to modify:**
- `src/main/java/org/linphone/core/CoreContext.kt`

**Changes needed:**

1. Add import:
   ```kotlin
   import org.linphone.clb.CallStateCLB
   import org.linphone.clb.kt.CoreContextExt
   ```

2. In `answerCall()` function (before creating call params):
   ```kotlin
   fun answerCall(call: Call) {
       Log.i("[Context] Answering call $call")
       // NEW: End any CLB calls first
       CallStateCLB.instance().EndAnyCLBCall(core)
       // ... rest of function
   }
   ```

3. Modify `onOutgoingStarted()` function to check for CLB calls:
   ```kotlin
   private fun onOutgoingStarted() {
       // NEW: Check if call is from CLB
       if (CallStateCLB.instance().IsCallFromCLB()) {
           val coreExt = CoreContextExt()
           coreExt.OnOutgoingStarted(false)
           return  // SKIP normal Linphone flow
       }
       // ... rest of function
   }
   ```

4. Modify `onCallStarted()` function:
   ```kotlin
   fun onCallStarted() {
       // NEW: Skip activity display for CLB calls
       if (CallStateCLB.instance().IsCallFromCLB()) {
           return
       }
       // ... rest of function
   }
   ```

**Validation:**
- [ ] CoreContext compiles without errors
- [ ] CallStateCLB methods are accessible
- [ ] Call state transitions work correctly
- [ ] CLB calls don't show UI inappropriately

---

### PRIORITY 3: Broadcast Receiver Registration

**Files to modify:**
- `AndroidManifest.xml`

**Changes needed:**
- Register DirectCallReceiver, HangupReceiver, LoginReceiver, LogoutReceiver as broadcast receivers
- Verify receiver actions and permissions are defined

**Validation:**
- [ ] Manifest parses without errors
- [ ] All receivers are exported (if needed for system intents)

---

### PRIORITY 4: SDK 5.5 API Updates

**Critical CLB Classes Requiring SDK API Review:**
1. **RegisterCLB.java** - Check if ProxyConfig still exists or use Account API
2. **CallStateCLB.java** - Verify CoreListenerStub callback signatures
3. **DirectCallReceiver.java** - Verify Call creation/acceptance API
4. **HangupReceiver.java** - Verify Call termination API

**Actions:**
- [ ] Read RegisterCLB.java and check ProxyConfig usage
- [ ] Compare Linphone SDK 5.3.47 vs 5.5 documentation for breaking changes
- [ ] Update RegisterCLB if Account API is required
- [ ] Update CallStateCLB listener signatures if changed
- [ ] Test each CLB class compiles correctly

---

### PRIORITY 5: Supporting Integrations

**Files/Classes:**
- CallFilter - Call history filtering
- ClbSettingsBlockChecker - Settings access control
- CorePreferences.kt - Settings integration points
- NotificationsManager.kt - Call notification handling
- MainActivity.kt - Activity lifecycle hooks

**Approach:**
- Search 6.0 codebase for these components
- Apply CLB-marked code from 5.2 at equivalent locations

---

## DEPENDENCY RESEARCH REQUIRED

Before proceeding, answer these questions:

1. **ProxyConfig → Account Migration (SDK 5.4+)**
   - Does Linphone SDK 5.5 still have ProxyConfig class?
   - If not, what's the Account API equivalent?
   - Impact: RegisterCLB, LoginReceiver, LogoutReceiver

2. **CoreListenerStub Callbacks (SDK 5.5)**
   - Are callback method signatures same as 5.3?
   - `onRegistrationStateChanged()` still exists?
   - `onCallStateChanged()` still exists?
   - Impact: CallStateCLB

3. **Call and Address APIs**
   - `Call.getState()` still exists or renamed?
   - Address construction/parsing methods same?
   - Impact: CallStateCLB, DirectCallReceiver

4. **Factory and Config APIs**
   - `Factory.instance().createConfigWithFactory()` still exists?
   - Provisioning URI configuration same?
   - Impact: AppConfigHelper, CreateConfigCLB

5. **CoreService Lifecycle**
   - `startForegroundService()` intent flags compatible?
   - LauncherActivity still launched for CLB intents?
   - Impact: CoreContextExt

---

## WORK PLAN (Detailed Steps)

### Week 1: Research & Planning
- [ ] Read SDK 5.5 release notes for breaking changes
- [ ] Compare Linphone SDK 5.3 vs 5.5 API documentation
- [ ] Identify which CLB classes need refactoring
- [ ] Create detailed API change mapping document

### Week 2: Phase 1 Integration (Configuration)
- [ ] Update LinphoneApplication.kt with CreateConfigCLB
- [ ] Update provisioning URL setup
- [ ] Update RegisterCLB initialization
- [ ] Update CallStateCLB initialization
- [ ] Test: Config creation works, provisioning URL set correctly

### Week 3: Phase 2 Integration (Call Management)
- [ ] Update CoreContext.kt with CallStateCLB hooks
- [ ] Add CallStateCLB checks in answerCall, onOutgoingStarted, onCallStarted
- [ ] Register DirectCallReceiver and HangupReceiver
- [ ] Test: Call routing works, CLB calls handled correctly

### Week 4: Phase 3 Integration (SDK API Updates)
- [ ] Review and update RegisterCLB for Account API (if needed)
- [ ] Update CallStateCLB for callback signature changes
- [ ] Update DirectCallReceiver for Call API changes
- [ ] Update HangupReceiver for Call API changes
- [ ] Test: All CLB classes compile, integration points work

### Week 5: Phase 4 Integration (Supporting Code)
- [ ] Integrate CallFilter for call history
- [ ] Integrate ClbSettingsBlockChecker
- [ ] Update CorePreferences integration points
- [ ] Update NotificationsManager integration points
- [ ] Test: Settings blocking works, call history filtered correctly

### Week 6: Testing & Fixes
- [ ] Build complete app with all CLB integrations
- [ ] Test incoming call routing from CLB
- [ ] Test outgoing call behavior
- [ ] Test device lock handling
- [ ] Test provisioning config download
- [ ] Fix any runtime issues
- [ ] Performance & stability testing

### Week 7: Documentation & Release
- [ ] Update CHANGELOG
- [ ] Document all CLB changes made
- [ ] Create migration guide for future updates
- [ ] Tag release v6.0.0-clb
- [ ] Merge to main feature branch

---

## SUCCESS CRITERIA

✅ **Build Success:**
- App compiles without errors
- All CLB classes included in APK
- All build variants (clb, clbTypeM, clbConfig, linphone) build successfully

✅ **Functional Success:**
- Incoming calls from CLB hardware are accepted and routed correctly
- Outgoing calls work normally
- Call history is properly filtered
- Device lock state prevents UI display when locked
- Settings can't be modified without PIN
- Provisioning URL is set and configuration is downloaded
- Remote config changes are applied

✅ **Integration Success:**
- No CLB API breaking changes vs 5.2.xclb
- No performance regressions vs 5.2.xclb
- All call flows work end-to-end
- Telecom framework still integrated
- Notification handling still works
- Account registration works with SDK 5.5

---

## CURRENT STATUS

✅ **Completed:**
- Created feature/clb-6.0-integration branch from release/6.0
- Copied CLB folder (15 files) from release/5.2.xclb
- Created functional analysis of all CLB modifications
- Documented integration strategy

**Next Action:**
- Begin PRIORITY 1: Configuration Management Integration
- Start by analyzing 6.0's LinphoneApplication.kt structure
- Compare with 5.2 version to understand changes
- Implement CreateConfigCLB and other config changes

---

