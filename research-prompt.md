================================================================================
RESEARCH REQUEST: Debug and Fix Magisk Module Installation Failure
================================================================================

The InputBlocker Magisk module ZIP keeps failing to install on Magisk with "This zip is not a Magisk module!" error. We need you to thoroughly research, diagnose, and provide the exact fix.

================================================================================
PART 1: FUNDAMENTAL RESEARCH
================================================================================

First, research and explain EXACTLY what makes Magisk accept a module ZIP:

A. What file MUST be at the ZIP ROOT (not in any subfolder)?
B. What exact format/fields MUST be in module.prop? List each required field with example values.
C. What line endings are required - LF vs CRLF? How do you verify/detect this?
D. Is META-INF required for Magisk Manager app install, or only for TWRP recovery?
E. Does the order of files in the ZIP matter?
F. What does the ZIP compression method need to be (deflate vs stored)?

================================================================================
PART 2: CURRENT STATE ANALYSIS
================================================================================

Our current module folder structure (module/) contains:

1. META-INF/com/google/android/update-binary - contains:
   #!/sbin/sh
   umask 022
   ui_print() { echo "$1"; }
   require_new_magisk() { ...exit 1 }
   OUTFD=$2
   ZIPFILE=$3
   mount /data 2>/dev/null
   [ -f /data/adb/magisk/util_functions.sh ] || require_new_magisk
   . /data/adb/magisk/util_functions.sh
   [ $MAGISK_VER_CODE -lt 20400 ] && require_new_magisk
   install_module
   exit 0

2. META-INF/com/google/android/updater-script - contains single line: #MAGISK

3. module.prop - contains:
   id=inputblocker
   name=InputBlocker
   version=v1.0.0
   versionCode=1
   author=Laviesss
   description=Block ghost taps and unwanted touch inputs by defining rectangular regions

4. customize.sh - KernelSU/APatch customization script

5. service.sh - Boot service that auto-installs APK

6. install.sh - Universal module installer that detects Magisk/KernelSU/APatch/SuperSU

7. system/bin/inputblocker - CLI tool

8. common/blocked_regions.conf - Default config file

9. The CI will also copy and include: common/InputBlocker.apk (the Android APK)

Research and identify: What's WRONG with this structure? What needs to be added/removed/fixed?

================================================================================
PART 3: MAGISK MANAGER VS TWRP INSTALLATION
================================================================================

Research the differences:

A. When installing via Magisk Manager app (NOT TWRP):
   - Does META-INF get processed or ignored?
   - What files does Magisk Manager actually read from the ZIP?
   - Does it call update-binary or just extract files?

B. When installing via TWRP recovery:
   - What triggers the update-binary execution?
   - What's the purpose of #MAGISK in updater-script?

C. Are there DIFFERENT requirements for each method?

================================================================================
PART 4: MINIMAL MODULE STRUCTURE
================================================================================

Research and provide the EXACT minimal working structure for:

A. Magisk Manager app install (most common use case):
   - List all required files
   - Show exact contents of each file
   - Explain what each file does

B. KernelSU Manager app install:
   - Does this need anything different?
   - Do we still need META-INF or is it ignored?

C. APatch Manager app install:
   - Does this need anything different?

D. A single ZIP that works on ALL THREE managers:
   - What's the common subset?
   - What conflicts need to be resolved?

================================================================================
PART 5: THE APK AUTO-INSTALL PROBLEM
================================================================================

Research how the companion APK gets installed:

A. Should the APK be placed in common/ or somewhere else for auto-install?
B. Does the module installer copy and install it, or does a separate service.sh handle it?
C. What's the proper way to have Magisk install an APK as a system app (or regular app)?

================================================================================
PART 6: CI BUILD DEBUG
================================================================================

The current CI workflow has this step:

```yaml
- name: Create module ZIP
  run: |
    mkdir -p release
    cp module/common/app-debug.apk module/common/InputBlocker.apk
    cd module
    zip -r ../release/InputBlocker.zip . -x "*.DS_Store" "*.git*"
    unzip -l ../release/InputBlocker.zip
```

Research and suggest:

A. What additional debug commands would verify the ZIP is correct?
B. How do you check line endings of files in the ZIP?
C. How do you verify module.prop is at the root level?
D. What commands would help diagnose WHY it's being rejected?

================================================================================
PART 7: PROVIDE THE FIX
================================================================================

Finally, provide:

A. The COMPLETE corrected module folder structure with:
   - Which files to keep
   - Which files to remove
   - Which files to add
   - Exact new contents for each file

B. For the CI workflow, any changes needed to properly create the ZIP

C. Clear explanation of WHY your fix will work (the root cause)

================================================================================
IMPORTANT NOTES
================================================================================

- This module must work when users install it via Magisk Manager app
- Must also work on KernelSU and APatch if possible
- The companion APK (InputBlocker.apk) must be included in the ZIP
- We need a single solution that works, not separate ZIPs for each manager

Research thoroughly and provide a definitive fix!