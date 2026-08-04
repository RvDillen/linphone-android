# CLB Integration Porting Plan: 6.0 Baseline

**Strategy:** Use 6.0 as clean baseline, systematically port CLB code from 5.2.xclb

---

## Phase 1: Core CLB Integration Points (High Priority)

### 1.1 LinphoneApplication.kt
**Risk:** HIGH - Application lifecycle and core initialization  
**Status:** PENDING  
**Files Involved:**
- `src/main/java/org/linphone/LinphoneApplication.kt` (6.0 baseline)
- Reference: `release/5.2.xclb:app/src/main/java/org/linphone/LinphoneApplication.kt` (5.2 with CLB)

**CLB Changes to Port:**
- [ ] CLB-specific initialization code
- [ ] CLB broadcast receivers registration
- [ ] CLB service lifecycle hooks

---

### 1.2 CoreContext.kt
**Risk:** CRITICAL - Core Linphone service lifecycle  
**Status:** PENDING  
**Files Involved:**
- `src/main/java/org/linphone/core/CoreContext.kt` (6.0 baseline)
- Reference: `release/5.2.xclb:app/src/main/java/org/linphone/core/CoreContext.kt` (5.2 with CLB)

**CLB Changes to Port:**
- [ ] CallStateCLB integration
- [ ] DirectCallReceiver hooks
- [ ] HangupReceiver hooks
- [ ] CoreContextExt service lifecycle calls
- [ ] CLB account registration logic

---

### 1.3 NotificationsManager.kt
**Risk:** HIGH - Notification and foreground service handling  
**Status:** PENDING  
**Files Involved:**
- `src/main/java/org/linphone/notifications/NotificationsManager.kt` (6.0 baseline)
- Reference: `release/5.2.xclb:app/src/main/java/org/linphone/notifications/NotificationsManager.kt` (5.2 with CLB)

**CLB Changes to Port:**
- [ ] CLB-specific notification handling
- [ ] Foreground service setup with CLB requirements
- [ ] Call notification routing

---

### 1.4 MainActivity.kt
**Risk:** MEDIUM - Main activity and call routing  
**Status:** PENDING  
**Files Involved:**
- `src/main/java/org/linphone/activities/main/MainActivity.kt` (6.0 baseline)
- Reference: `release/5.2.xclb:app/src/main/java/org/linphone/activities/main/MainActivity.kt` (5.2 with CLB)

**CLB Changes to Port:**
- [ ] CLB call routing logic
- [ ] Activity lifecycle hooks for CLB
- [ ] CLB-specific permission handling

---

## Phase 2: Secondary Integration Points (Medium Priority)

### 2.1 Call Management Files
- [ ] `src/main/java/org/linphone/activities/voip/fragments/SingleCallFragment.kt`
- [ ] `src/main/java/org/linphone/activities/main/viewmodels/ControlsViewModel.kt`
- [ ] `src/main/java/org/linphone/activities/voip/viewmodels/ControlsViewModel.kt`
- [ ] `src/main/java/org/linphone/telecom/TelecomConnectionService.kt`
- [ ] `src/main/java/org/linphone/telecom/TelecomHelper.kt`

### 2.2 History and Settings
- [ ] `src/main/java/org/linphone/activities/main/history/viewmodels/CallLogsListViewModel.kt` (CallFilter integration)
- [ ] `src/main/java/org/linphone/activities/main/settings/viewmodels/AdvancedSettingsViewModel.kt`
- [ ] `src/main/java/org/linphone/core/CorePreferences.kt`

### 2.3 UI Fragments and Activities
- [ ] `src/main/java/org/linphone/activities/launcher/LauncherActivity.kt`
- [ ] `src/main/java/org/linphone/activities/main/dialer/fragments/DialerFragment.kt`
- [ ] `src/main/java/org/linphone/activities/main/sidemenu/fragments/SideMenuFragment.kt`

---

## Phase 3: Remaining Integrations (Lower Priority)

### 3.1 Compatibility and Notifications
- [ ] `src/main/java/org/linphone/compatibility/Api34Compatibility.kt`
- [ ] `src/main/java/org/linphone/telecom/NativeCallWrapper.kt`

### 3.2 Resource Files
- [ ] `src/main/res/xml/contacts.xml` (MIME type definition)
- [ ] `src/main/res/xml/network_security_config.xml`
- [ ] `src/main/res/values/strings.xml`
- [ ] `src/main/res/values-nl/strings.xml`

---

## Next Steps

1. **Extract CLB code sections from 5.2.xclb for each file**
   - Use git diff to identify CLB-specific lines
   - Document insertion points and context

2. **Port high-priority files first (CoreContext, NotificationsManager)**
   - These are blocking compilation and functionality

3. **Test compilation after each phase**
   - Verify imports resolve
   - Check for missing CLB dependencies

4. **Run CLB functional tests**
   - Verify call reception, routing, history filtering work

---

## Porting Method

For each file, use:
```bash
# View CLB changes in 5.2
git diff release/6.0..release/5.2.xclb -- <file>

# Extract specific sections and apply manually
```

Or use git cherry-pick for commits that exclusively contain CLB changes.

---

**Status:** Phase 1 starting  
**Last Updated:** 2026-06-05

