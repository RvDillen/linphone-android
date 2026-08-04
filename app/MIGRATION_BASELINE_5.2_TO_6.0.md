# Linphone Android Migration Plan: 5.2.8 CLB → 6.0.x + SDK 5.5

**Status:** Migration Assessment  
**Date:** 2026-06-05  
**Current Baseline:** CLB Version 5.2.8 (Linphone SDK 5.3.47)  
**Target Baseline:** Linphone Android 6.0.x (Linphone SDK 5.5)

---

## PART 1: CURRENT CODEBASE BASELINE (5.2.xclb)

### 1.1 Build Configuration Summary

| Property | Current (5.2.8) | Target (6.0.x) | Notes |
|----------|-----------------|-----------------|-------|
| **Linphone SDK** | 5.3.47 | 5.5 | [CRITICAL] SDK version bump - breaking changes expected |
| **Build SDK** | 34 | 35 | Android 2025 minimum required |
| **Target SDK** | 34 | 35 | Android runtime features |
| **Min SDK** | 24 | 28 | Dropping Android 7 support |
| **Java/Kotlin Target** | 21 | 17 (or 21) | No change if 21 is acceptable |
| **Build Tool** | Gradle (Groovy DSL) | Gradle (Kotlin DSL) | build.gradle → build.gradle.kts conversion |
| **Package Manager** | AGP (Android Gradle Plugin) v8.x | AGP v9.x+ | Version compatibility impact |
| **Custom App Version** | 5.2.8 | → 6.0.0 (proposed) | Linphone version tracks major releases |
| **Build Variants** | clb, clbTypeM, clbConfig, linphone | Same (preserved) | All custom flavors remain intact |

### 1.2 Dependencies Affected by SDK 5.3.47 → 5.5 Migration

**Primary SDK Dependency:**
```gradle
implementation 'org.linphone:linphone-sdk-android:5.3.47'  // CURRENT
implementation 'org.linphone:linphone-sdk-android:5.5'     // TARGET
```

**Linphone Core API Imports Used in CLB:**
- `org.linphone.core.Core`
- `org.linphone.core.Call`
- `org.linphone.core.Address`
- `org.linphone.core.Config`
- `org.linphone.core.CoreContext`
- `org.linphone.core.CorePreferences`
- `org.linphone.core.CoreService`
- `org.linphone.core.CoreListenerStub`
- `org.linphone.core.ProxyConfig`
- `org.linphone.core.Account` (new in 5.4, may be affecting RegisterCLB)
- `org.linphone.core.Reason` (enum - may have changed)

---

## PART 2: CUSTOM CLB SOURCE INVENTORY (15 Files)

### 2.1 CLB Package Structure
**Location:** `src/main/java/org/linphone/clb/`

| # | File | Purpose | Risk Level | SDK 5.3→5.5 Impact |
|---|------|---------|------------|-------------------|
| 1 | **AppConfigHelper.java** | XML-based remote config import + MDM support (RestrictionsManager) | MEDIUM | Core/Config API changes |
| 2 | **CallStateCLB.java** | Call state monitoring, device lock detection, activity visibility | HIGH | Core/Call/Address API changes, TelephonyCallback compat |
| 3 | **CallFilter.java** | Remove hardware-generated calls from history | LOW | May need query API updates |
| 4 | **ClbSettingsBlockChecker.java** | PIN-based settings blocking enforcement | LOW | SharedPreferences/Config access |
| 5 | **CoreContextExt.kt** | Service lifecycle extensions (StartCoreService, OnOutgoingStarted) | HIGH | CoreService lifecycle changes in 6.0 |
| 6 | **DirectCallReceiver.java** | Handle direct incoming calls with custom routing | HIGH | Call/Core API changes |
| 7 | **HangupReceiver.java** | BroadcastReceiver for call termination events | MEDIUM | Call API changes |
| 8 | **HashUtil.java** | Config change detection via hash calculation | LOW | No SDK dependency |
| 9 | **LinphonePreferencesCLB.java** | Local linphonerc file import from downloads folder | MEDIUM | Config API changes, file permission handling |
| 10 | **LockHelper.java** | Device lock state detection (Keyguard API) | LOW | Android API only |
| 11 | **LockHelperExt.kt** | Kotlin extension for lock detection | LOW | Android API only |
| 12 | **LoginReceiver.java** | Account login lifecycle tracking | MEDIUM | Account API changes (new in 5.4+) |
| 13 | **LogoutReceiver.java** | Account logout lifecycle tracking | MEDIUM | Account API changes |
| 14 | **PermissionHelperCLB.java** | Custom permission request handling | LOW | Android permission framework |
| 15 | **RegisterCLB.java** | Custom account registration logic | HIGH | ProxyConfig→Account migration (5.4+) |

**Risk Summary:**
- **HIGH RISK (5 files):** CallStateCLB, CoreContextExt, DirectCallReceiver, RegisterCLB, HangupReceiver
- **MEDIUM RISK (5 files):** AppConfigHelper, LinphonePreferencesCLB, LoginReceiver, LogoutReceiver, HangupReceiver
- **LOW RISK (5 files):** CallFilter, ClbSettingsBlockChecker, HashUtil, LockHelper, PermissionHelperCLB, LockHelperExt

---

## PART 3: CLB INTEGRATION POINTS IN MAIN CODEBASE (23+ Files)

### 3.1 Files with CLB Comment Markers (by integration density)

| File | CLB Refs | Purpose | Type |
|------|----------|---------|------|
| **CoreContext.kt** | 1219 | Core service lifecycle, listeners, call handling | CRITICAL |
| **NotificationsManager.kt** | 615 | Foreground notification management | CRITICAL |
| **MainActivity.kt** | 256 | Activity lifecycle, call routing | CRITICAL |
| **ControlsViewModel.kt** | 225 | Call control UI logic | CRITICAL |
| **TelecomHelper.kt** | 87 | Telecom framework integration | MAJOR |
| **TelecomConnectionService.kt** | 295 | System call integration | MAJOR |
| **NativeCallWrapper.kt** | 105 | Native call wrapping | MAJOR |
| **CorePreferences.kt** | 458 | Settings persistence and retrieval | MAJOR |
| **AdvancedSettingsViewModel.kt** | 28 | Settings UI | MINOR |
| **SingleCallFragment.kt** | 206 | Call UI display | MINOR |
| **CallOverlayViewModel.kt** | 46 | Call overlay logic | MINOR |
| **SideMenuFragment.kt** | 119 | Navigation menu | MINOR |
| **DialerFragment.kt** | 212 | Dialer UI | MINOR |
| **CallLogsListViewModel.kt** | 161 | Call history display + CallFilter integration | MAJOR |
| **LauncherActivity.kt** | 46 | Launch activity, activity routing | MINOR |
| **Api34Compatibility.kt** | 132 | Android 11+ compatibility | MINOR |
| **contacts.xml** | 8 | Custom MIME type for CLB SIP addresses | CRITICAL |
| **network_security_config.xml** | 4 | Network security policies | MINOR |
| **strings.xml** | 407 | UI strings | MINOR |
| **strings-nl.xml** | 144 | Dutch translations | MINOR |

**Total CLB Integration Points:** 23 files with 4,000+ line references to CLB functionality

---

## PART 4: KEY BREAKING CHANGES (SDK 5.3.47 → 5.5)

### 4.1 Core API Changes

#### **ProxyConfig → Account Migration (5.4+)**
**Impact:** RegisterCLB, LoginReceiver, LogoutReceiver

In SDK 5.3.47:
```java
ProxyConfig config = core.createProxyConfig();
config.setIdentityAddress(...)
core.addProxyConfig(config);
```

In SDK 5.5:
```java
Account account = core.createAccount();
account.setIdentityAddress(...)
core.addAccount(account);
```

**Migration Required:** Update RegisterCLB to use new Account API instead of deprecated ProxyConfig

---

#### **CoreListenerStub Changes (5.4+)**
**Impact:** CallStateCLB, CoreContextExt

New event patterns in 5.4+ require callback signature updates:
```java
// OLD (5.3):
public void onRegistrationStateChanged(...) { }

// NEW (5.5):
public void onAccountRegistrationStateChanged(...) { }
```

**Migration Required:** Update all CoreListenerStub implementations to match new signatures

---

#### **Call API Changes**
**Impact:** CallStateCLB, DirectCallReceiver, CallFilter

- `Call.getState()` may have been renamed to `Call.getCallState()`
- `Reason` enum changes - verify all reason comparisons
- Call parameters API restructuring

**Migration Required:** Audit all Call object method calls

---

#### **Core Service Lifecycle (5.5+)**
**Impact:** CoreContextExt, CorePreferences, NotificationsManager

Android 6.0 (Linphone) restructured CoreService initialization:
- Foreground service requirements stricter
- Notification binding changes
- Service component detection methods changed

**Migration Required:** Update CoreContextExt.StartCoreService() for new API

---

#### **Address API Changes**
**Impact:** CallStateCLB, DirectCallReceiver

`Address` parsing and creation methods may have been refactored in 5.5:
- Constructor signatures
- Parsing from SIP URIs
- Parameter accessors

**Migration Required:** Verify Address creation and parsing in call handling code

---

### 4.2 Android Platform Changes (API 34 → 35, Min 24 → 28)

| Change | Impact | Files Affected |
|--------|--------|-----------------|
| **Storage Scoped Access** | File permission handling tighter | LinphonePreferencesCLB, AppConfigHelper |
| **Notification Runtime Permissions** | Required for foreground services | NotificationsManager, CoreContextExt |
| **Telecom API Updates** | System call integration changes | TelecomHelper, TelecomConnectionService |
| **Strictmode Violations** | More aggressive on main thread | CoreContext |
| **Predicate/Cursor API** | CallFilter query changes | CallFilter |

---

## PART 5: MIGRATION STRATEGY (Phased Approach)

### Phase 1: Build Configuration Update (Days 1-2)
**Objective:** Get project compiling with new SDK

1. Update `build.gradle` → `build.gradle.kts` (if needed)
2. Update Linphone SDK: 5.3.47 → 5.5
3. Update compileSdk: 34 → 35, targetSdk: 34 → 35, minSdk: 24 → 28
4. Run `./gradlew clean` and resolve dependency conflicts
5. **Parallel:** Fix AndroidX/Kotlin stdlib version conflicts

**Deliverable:** Project builds with no compilation errors

---

### Phase 2: CLB Core API Migration (Days 3-7)
**Objective:** Update CLB classes to use SDK 5.5 APIs

**Priority 1 (Critical Path):**
1. **RegisterCLB.java** - ProxyConfig → Account migration
   - Change all `createProxyConfig()` to `createAccount()`
   - Update method signatures for Account API
   - Verify LoginReceiver/LogoutReceiver still work with new Account lifecycle

2. **CallStateCLB.java** - Update Core/Call/Address APIs
   - Verify `CoreListenerStub` callback signatures match 5.5
   - Update `Call.getState()` → `Call.getCallState()` (if changed)
   - Verify `Address` API usage
   - Test call state transitions

3. **CoreContextExt.kt** - Service lifecycle updates
   - Update `CoreService` initialization for 5.5
   - Verify foreground service binding
   - Test service startup sequences

**Priority 2 (High Risk):**
1. **DirectCallReceiver.java** - Call creation/acceptance updates
2. **HangupReceiver.java** - Call termination API changes
3. **LoginReceiver.java** / **LogoutReceiver.java** - Account lifecycle changes

**Priority 3 (Medium Risk):**
1. **AppConfigHelper.java** - Config API updates
2. **LinphonePreferencesCLB.java** - Preferences access changes
3. **CallFilter.java** - Query/Cursor API updates

**Deliverable:** All CLB classes compile and unit tests pass

---

### Phase 3: Main Codebase CLB Integration Points (Days 8-12)
**Objective:** Update CLB comment-marked code in main application

**Files to Update (Priority Order):**
1. **CoreContext.kt** (1219 refs) - Most critical, highest impact
2. **NotificationsManager.kt** (615 refs) - Notification handling
3. **TelecomConnectionService.kt** (295 refs) - System integration
4. **CallLogsListViewModel.kt** (161 refs) - Call history + CallFilter
5. **CorePreferences.kt** (458 refs) - Settings handling
6. **ControlsViewModel.kt** (225 refs) - Call controls
7. **SingleCallFragment.kt** (206 refs) - Call UI
8. **DialerFragment.kt** (212 refs) - Dialer logic

**Approach:**
- Line-by-line review of all CLB-marked code
- Update API calls for SDK 5.5 equivalents
- Run tests after each file group update

**Deliverable:** No CLB comment code has SDK 5.3 API references; all updated to 5.5

---

### Phase 4: Build Flavors & Packaging (Days 13-14)
**Objective:** Verify all build variants work correctly

1. Build all 4 flavors: `clb`, `clbTypeM`, `clbConfig`, `linphone`
2. Verify package names resolve correctly
3. Test variant-specific code paths
4. Verify Firebase/Crashlytics integration still works

**Deliverable:** All APK variants build and can be installed

---

### Phase 5: System Integration Testing (Days 15-20)
**Objective:** Verify CLB functionality works end-to-end

**Test Scenarios:**
1. **Call Reception:** Incoming calls trigger DirectCallReceiver → handled correctly
2. **Call Hangup:** Calls end, HangupReceiver fires, call history updated
3. **Device Lock:** Behavior matches LockHelper/CoreContextExt expectations (no mic blocking)
4. **Account Registration:** RegisterCLB creates account, LoginReceiver fires
5. **Settings Blocking:** ClbSettingsBlockChecker prevents unauthorized access
6. **Config Import:** AppConfigHelper parses remote config correctly
7. **Call History:** CallFilter removes hardware-generated calls

**Deliverable:** All CLB features work; call flows are uninterrupted

---

### Phase 6: Performance & Compatibility (Days 21-25)
**Objective:** Ensure no regressions; verify Android 6.0+ features

1. Run Lint analysis, address warnings
2. Test on Android 10, 11, 12, 13, 14, 15 devices (min: API 28)
3. Profile memory/CPU usage for service lifecycle
4. Verify Telecom framework integration (CallScreeningService, etc.)
5. Test in low-memory/battery-saver scenarios

**Deliverable:** No performance regressions; passes Android compatibility tests

---

### Phase 7: Documentation & Version Bump (Days 26-28)
**Objective:** Document changes and prepare release

1. Document all CLB API changes and rationale
2. Create migration guide for future updates
3. Bump version: 5.2.8 → 6.0.0 (aligning with Linphone Android release)
4. Tag release and merge to master
5. Create release notes

**Deliverable:** Release-ready 6.0.0 with complete documentation

---

## PART 6: FILE-BY-FILE MIGRATION CHECKLIST

### High-Risk CLB Classes (Detailed Attention Required)

#### **RegisterCLB.java**
- [ ] Convert ProxyConfig → Account API
- [ ] Update all createProxyConfig() calls
- [ ] Verify authentication flow with new Account
- [ ] Test with different account types (SIP, VOIP)
- [ ] Update related LoginReceiver/LogoutReceiver

#### **CallStateCLB.java**
- [ ] Update CoreListenerStub signatures for 5.5
- [ ] Verify Call.getState() method name
- [ ] Update Address API usage
- [ ] Test device lock detection (Build.VERSION checks)
- [ ] Verify TelephonyCallback compatibility

#### **CoreContextExt.kt**
- [ ] Update CoreService startup for 5.5
- [ ] Verify foreground service notification binding
- [ ] Update Intent flags if changed
- [ ] Test activity launch from background
- [ ] Verify Android 11+ device lock handling

#### **DirectCallReceiver.java**
- [ ] Update Call creation/acceptance API
- [ ] Verify Address parsing
- [ ] Test custom routing logic
- [ ] Update timeout handling if changed

#### **HangupReceiver.java**
- [ ] Update Call termination API
- [ ] Verify decline/hangup reason handling
- [ ] Test call history updates

---

## PART 7: RISK MATRIX & EFFORT ESTIMATION

| Task | Complexity | Effort | Risk | Test Coverage |
|------|-----------|--------|------|-------------------|
| Build config update | Low | 4h | Low | Gradle validation |
| SDK dependency bump | Low | 2h | Low | Compilation check |
| RegisterCLB migration | High | 16h | Critical | Integration tests |
| CallStateCLB migration | High | 20h | Critical | Call flow tests |
| CoreContextExt update | High | 16h | Critical | Service lifecycle tests |
| DirectCallReceiver update | High | 16h | Critical | Incoming call tests |
| Other CLB files (5 files) | Medium | 20h | High | Unit tests |
| CoreContext.kt CLB refs | High | 24h | High | Full app testing |
| Other main code updates (6 files) | Medium | 24h | Medium | Integration tests |
| System integration testing | Medium | 40h | High | End-to-end testing |
| Performance & compatibility | Medium | 20h | Low | Profiling |
| Documentation | Low | 8h | Low | Review |

**TOTAL ESTIMATED EFFORT: 210 hours (5-6 weeks, 1 developer)**

---

## PART 8: RECOMMENDED NEXT STEPS

### Immediate Actions (This Week)
1. **Create feature branch:** `feature/sdk-5.5-migration` from `release/5.2.xclb`
2. **Set up test environment:** Prepare devices/emulators for API 28, 34, 35
3. **Begin Phase 1:** Update build.gradle for SDK 5.5 compatibility
4. **Lock RegisterCLB:** Mark critical ProxyConfig→Account changes as Priority 1

### Dependencies to Research
1. Check Linphone SDK 5.4 → 5.5 official changelog for breaking changes
2. Verify ProxyConfig deprecation timeline in 5.4+
3. Review Account API documentation for feature parity
4. Check Android 6.0 (Linphone) architecture changes in CoreService

---

## APPENDIX A: CLB Modification Categories

### Configuration Management (3 files)
- AppConfigHelper.java - Remote config + MDM
- LinphonePreferencesCLB.java - Local file import
- HashUtil.java - Change detection

### Call Control (3 files)
- CallStateCLB.java - State monitoring
- DirectCallReceiver.java - Incoming call routing
- HangupReceiver.java - Call termination

### Account Management (3 files)
- RegisterCLB.java - Registration
- LoginReceiver.java - Login events
- LogoutReceiver.java - Logout events

### Device Integration (3 files)
- LockHelper.java - Lock state (Java)
- LockHelperExt.kt - Lock state (Kotlin)
- PermissionHelperCLB.java - Permissions

### Access Control & Filtering (2 files)
- ClbSettingsBlockChecker.java - Settings blocking
- CallFilter.java - History filtering

### Service Lifecycle (1 file)
- CoreContextExt.kt - Service management

---

**End of Migration Baseline Assessment**

