# CLB Code Analysis for Intelligent Porting to Linphone 6.0

**Document:** Detailed CLB integration mapping from 5.2.xclb  
**Purpose:** Understand functional intent of CLB code to properly map to 6.0 architecture  
**Status:** Analysis in progress

---

## PART 1: CLB FUNCTIONAL CATEGORIES

### Category 1: Configuration Management (3 CLB classes)

#### 1.1 AppConfigHelper (Remote provisioning + MDM)
**Purpose:** Download and parse remote provisioning config from CLB server, with app-specific section handling

**Integration Points in 5.2.xclb:**

1. **LinphoneApplication.kt - ensureCoreExists() (Line ~120)**
   ```
   val ach = AppConfigHelper(context, corePreferences)
   val contents = ach.checkRemoteProvisioning(false, coreContext, provisioningPath)
   if (contents != null && contents.length > 0) {
       if (LinphonePreferencesCLB.instance().UpdateFromLinphoneXmlData(contents, config)) {
           ach.updateShowSettingsToCorePreferences(config)
       }
   }
   ```
   **Function:** Remote provisioning of app-specific settings

2. **LinphoneApplication.kt - CreateConfigCLB() (Line ~195)**
   ```
   val ach = AppConfigHelper(context, corePreferences)
   ach.checkAppConfig()  // Check MDM/RestrictionsManager
   if (ach.linphoneRcHasChanges()) {
       LinphonePreferencesCLB.instance().UpdateFromLinphoneRcData(...)
   }
   if (ach.linphoneRcXmlHasChanges(null)) {
       LinphonePreferencesCLB.instance().UpdateFromLinphoneXmlData(...)
   }
   ```
   **Function:** Check MDM restrictions, linphonerc updates, XML config parsing

**Linphone 6.0 Equivalent:** Factory.instance().createConfigWithFactory(...) - same API

**Porting Strategy:**
- Direct injection of AppConfigHelper usage into 6.0's config creation path
- May need to understand how 6.0 handles provisioning URI configuration
- Check if CorePreferences structure changed

---

#### 1.2 LinphonePreferencesCLB (Config file parsing)
**Purpose:** Parse linphonerc files (both standard RC format and XML format) from device storage and remote

**Integration Points:**
- `UpdateFromLinphoneRcData(data, configPath)` - RC format parsing
- `UpdateFromLinphoneXmlData(data, config)` - XML config parsing
- `MoveLinphoneRcFromDownloads(context, corePreferences)` - Local file handling
- `ParseLocalXmlFileConfig(config, corePreferences)` - Parse XML file from device

**Porting Strategy:**
- LinphonePreferencesCLB is independent class - no changes needed
- Just ensure it's called at correct lifecycle points in 6.0

---

#### 1.3 HashUtil (Change detection)
**Purpose:** Hash-based change detection for config files

**Porting Strategy:**
- Independent utility class - no changes needed

---

### Category 2: Call Management & Routing (5 CLB classes + integration points)

#### 2.1 CallStateCLB (Call state tracking + device lock detection)
**Purpose:** Track call states from CLB hardware, prevent activity launching on locked devices, manage call visibility

**Key Methods:**
- `instance()` - Singleton access
- `IsCallFromCLB()` - Check if current call originated from CLB
- `IsJustHangUp()` - Check if call was just hung up
- `EndAnyCLBCall(core)` - Terminate CLB-originated calls
- `Restart()` - Reinitialize listener

**Integration Points in 5.2.xclb:**

1. **LinphoneApplication.kt - ensureCoreExists() (Line ~155-170)**
   ```kotlin
   Timer().schedule(2000) {
       try {
           Log.i("[Application] Creating CallStateCLB")
           val instance = CallStateCLB.instance()
           Log.i("[Application] Restarting CallStateCLB")
           instance.Restart()
       } catch (e: Exception) {
           Log.i("[Application] Can't start CallStateCLB $e")
       }
   }
   ```
   **Purpose:** Delayed initialization of CallStateCLB after core is ready

2. **CoreContext.kt - answerCall() (Line ~800)**
   ```kotlin
   fun answerCall(call: Call) {
       Log.i("[Context] Answering call $call")
       CallStateCLB.instance().EndAnyCLBCall(core)  // END any CLB call first
       val params = core.createCallParams(call)
       ...
   }
   ```
   **Purpose:** When user answers a call, terminate any parallel CLB calls

3. **CoreContext.kt - onOutgoingStarted() (Line ~1218)**
   ```kotlin
   private fun onOutgoingStarted() {
       if (CallStateCLB.instance().IsCallFromCLB()) {
           val coreExt = org.linphone.clb.kt.CoreContextExt()
           coreExt.OnOutgoingStarted(false)
           return  // SKIP normal outgoing processing
       }
       // ... normal Linphone outgoing processing
   }
   ```
   **Purpose:** Outgoing calls from CLB bypass normal Linphone UI flow

4. **CoreContext.kt - onCallStarted() (Line ~1239)**
   ```kotlin
   fun onCallStarted() {
       // CLB => Not showing Activity on direct call
       if (CallStateCLB.instance().IsCallFromCLB()) {
           return  // SKIP showing activity
       }
       // ... normal activity showing
   }
   ```
   **Purpose:** CLB calls don't trigger Linphone activity display

**Porting Strategy:**
- CallStateCLB.java uses Core/Call/Address APIs from SDK 5.3.47
- **CRITICAL:** Needs SDK 5.3.47→5.5 API updates (Call methods may have changed)
- Integration points must be replicated at same logic locations in 6.0 CoreContext
- Must verify CoreListenerStub signatures match 6.0 expectations

---

#### 2.2 DirectCallReceiver (Direct call incoming handling)
**Purpose:** Handle incoming calls routed directly from CLB hardware, accept automatically, bypass normal UI flow

**Integration Points:**
- Likely registered as BroadcastReceiver in AndroidManifest.xml or code
- Needs to be re-registered in 6.0

**Porting Strategy:**
- Search 6.0 for how incoming calls are normally handled
- Ensure DirectCallReceiver can still intercept/handle calls in 6.0
- May need API updates for Call creation/acceptance (5.3.47→5.5)

---

#### 2.3 HangupReceiver (Call termination handling)
**Purpose:** Handle call termination events from CLB, update call history

**Porting Strategy:**
- Similar to DirectCallReceiver - ensure broadcast receiver registration works in 6.0
- Verify Call API for termination still compatible

---

#### 2.4 RegisterCLB (CLB account registration)
**Purpose:** Register CLB-specific accounts (may use different API than standard Linphone)

**Integration Point in 5.2.xclb - LinphoneApplication.kt (Line ~150)**
```kotlin
// CLB Registration
val registerCLB: RegisterCLB = org.linphone.clb.RegisterCLB(
    coreContext.context.applicationContext
)
registerCLB.RegisterReceivers()
```
**Purpose:** Initialize CLB account registration and broadcast receiver setup

**Porting Strategy:**
- **CRITICAL:** In SDK 5.4+ (including 5.5), ProxyConfig was deprecated in favor of Account
- RegisterCLB may need complete refactoring for Account API
- Check if 6.0 CoreContext uses Account instead of ProxyConfig

---

#### 2.5 LoginReceiver / LogoutReceiver
**Purpose:** Track account login/logout state

**Porting Strategy:**
- Should integrate with Account lifecycle (if Account API changed in 6.0)
- Ensure broadcast receivers still work

---

### Category 3: Device Integration (3 CLB classes)

#### 3.1 LockHelper / LockHelperExt (Device lock detection)
**Purpose:** Detect if device is locked (uses Keyguard Manager API)

**Integration Points:**
- Called from CoreContextExt.kt

**Porting Strategy:**
- Android API only - should not change
- Verify method signatures unchanged

---

#### 3.2 CoreContextExt.kt (Service lifecycle management)
**Purpose:** Start foreground service, manage activity visibility for locked devices

**Key Methods:**
- `StartCoreService(context)` - Start CoreService with foreground intent
- `OnOutgoingStarted(isJustHangup)` - Called when outgoing call starts (from CallStateCLB)
- `IsServiceReady()` - Check if CoreService is active

**Integration Point - CoreContext.kt onOutgoingStarted() (Line ~1218-1222)**
```kotlin
if (CallStateCLB.instance().IsCallFromCLB()) {
    val coreExt = org.linphone.clb.kt.CoreContextExt()
    coreExt.OnOutgoingStarted(false)
    return
}
```

**Porting Strategy:**
- Check if CoreService API changed in 6.0
- Verify startForegroundService() still uses same Intent flags
- Ensure LauncherActivity still exists for CLB intent dispatch

---

#### 3.3 PermissionHelperCLB
**Purpose:** Custom permission request handling

**Porting Strategy:**
- Check if Android permission APIs changed significantly
- Verify permission strings still exist in manifest

---

### Category 4: Call History & Filtering (1 CLB class)

#### 4.1 CallFilter (Remove hardware-generated calls)
**Purpose:** Filter out calls from CLB hardware from the call history list

**Integration Points:**
- Likely called from CallLogsListViewModel.kt

**Methods:**
- `RemoveCallsFromHardware()`
- `CallsFromHardwareToRemove_5_0_3()`

**Porting Strategy:**
- Query/Cursor API may have changed in 6.0
- Verify call history database access still works same way
- May need to update query predicates

---

### Category 5: Settings Access Control (1 CLB class)

#### 5.1 ClbSettingsBlockChecker
**Purpose:** Enforce PIN-based settings blocking

**Porting Strategy:**
- Likely just SharedPreferences checks - should not change
- Verify settings UI integration points

---

## PART 2: LINPHONE 6.0 BASELINE DIFFERENCES

### Build Configuration Changes
- Build tool: Groovy DSL → Kotlin DSL (build.gradle → build.gradle.kts)
- Android SDK: 34→35 (compileSdk, targetSdk)
- Min SDK: 24→28 (drops Android 7)
- Linphone SDK: 5.3.47→5.5

### Architectural Changes to Investigate
1. **Config Management:**
   - Does 6.0 still use Factory.instance().createConfigWithFactory()?
   - Is provisioning URI configuration still supported?

2. **Call Lifecycle:**
   - CoreListenerStub - are callback signatures same?
   - Call API - what methods changed? (getState()→getCallState()?)
   - Address API - constructor/parsing methods same?

3. **Account vs ProxyConfig:**
   - SDK 5.4+ deprecated ProxyConfig for Account
   - Is RegisterCLB using old API that no longer exists?

4. **CoreService:**
   - Is startForegroundService() call still same?
   - Are Intent flags compatible?
   - Does LauncherActivity still exist for activity launching?

5. **CoreContext:**
   - Are lifecycle callbacks same (onOutgoingStarted, onCallStarted)?
   - Core object API - still same methods?

---

## PART 3: PORTING CHECKLIST

### Phase 1: Dependency Analysis (Priority 1)
- [ ] Compare Factory API between SDK 5.3.47 and 5.5
- [ ] Check if ProxyConfig exists in 5.5 or if Account is required
- [ ] Verify CoreListenerStub callback signatures in 5.5
- [ ] Check Call and Address API changes in 5.5

### Phase 2: Configuration Integration (Priority 2)
- [ ] Update LinphoneApplication.kt to call CreateConfigCLB()
- [ ] Ensure AppConfigHelper works with 6.0's Factory API
- [ ] Test provisioning URI configuration in 6.0

### Phase 3: Call Management Integration (Priority 3)
- [ ] Add CallStateCLB integration points to CoreContext callbacks
- [ ] Integrate DirectCallReceiver for incoming calls
- [ ] Add HangupReceiver broadcast receiver registration
- [ ] Update RegisterCLB to use correct Account/ProxyConfig API

### Phase 4: Service Lifecycle Integration (Priority 4)
- [ ] Integrate CoreContextExt service startup
- [ ] Verify CoreService still works with foreground service APIs
- [ ] Test activity launching behavior on locked devices

### Phase 5: Call History & Settings Integration (Priority 5)
- [ ] Integrate CallFilter for call history
- [ ] Integrate ClbSettingsBlockChecker
- [ ] Update any query APIs if changed in 6.0

### Phase 6: Testing & Validation (Priority 6)
- [ ] Compile with all CLB integrations
- [ ] Test incoming call routing from CLB
- [ ] Test outgoing call behavior
- [ ] Test device lock handling
- [ ] Test provisioning config download

---

## NEXT STEPS

**Immediate Actions:**
1. Switch back to 6.0 baseline (feature/clb-6.0-integration branch)
2. Read 6.0's CoreContext.kt to understand current callback structure
3. Read 6.0's LinphoneApplication.kt to understand config initialization
4. Identify which Linphone SDK 5.5 APIs changed vs 5.3.47

**Then:**
Start Phase 1 (Dependency Analysis) to determine exact API changes needed in CLB classes

---

**Status:** Analysis Complete - Ready for 6.0 Baseline Review  
**Branch:** feature/clb-6.0-integration (on 5.2.xclb codebase currently)  
**Next Branch:** Will switch back to release/6.0 for comparison work

