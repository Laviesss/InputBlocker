# InputBlocker User Acquisition Strategy

This document outlines the strategy for growing the InputBlocker user base and building a sustainable community around the project.

## Target Audience Analysis

### Primary Users
*   **Damaged Hardware Owners:** People with older or damaged devices experiencing "ghost taps" (phantom touches) who want to extend the life of their hardware without expensive screen replacements.
*   **Android Enthusiasts:** Users who enjoy modding their devices and are already familiar with Magisk, KernelSU, or LSPosed.
*   **Budget-Conscious Users:** People in regions where repair costs are high relative to device value.

### Where They Hang Out
*   **XDA Forums:** The historical home of Android modding.
*   **Reddit:** Communities like r/Android, r/androidafterlife, r/Xiaomi, and r/LineageOS.
*   **Telegram:** Support groups for Magisk, KernelSU, and specific device development channels.
*   **Discord:** Server communities focused on custom ROMs and rooting.

## Platform Posting Strategy

### XDA Developers Forum
**Why:** This is the most critical platform. XDA users are technical, provide high quality logs, and are the primary demographic for root modules.

*   **Post Format:** [MODULE][ROOT] InputBlocker - Filter Ghost Taps via InputDispatcher
*   **Body Structure:**
    1.  The Hook: "Is your phone clicking things on its own? Don't trash it yet."
    2.  What it is: Brief technical explanation of InputDispatcher hooking.
    3.  Key Features: LSPosed/Overlay modes, DBSCAN detection, PC Designer.
    4.  Installation: Requirements (Root, LSPosed).
    5.  Links: GitHub, Download, Telegram Support.
*   **Best Times:** Tuesday or Thursday mornings (EST) to catch both European and American traffic.
*   **Example Pitch:** "InputBlocker uses DBSCAN clustering to identify and kill phantom touches before they reach your apps. It's open source, lightweight, and saves you a $150 screen repair."

### Reddit
**Why:** High visibility and potential for viral growth in niche subreddits.

*   **Subreddits:** r/Android (General), r/androidafterlife (Old devices), r/Xiaomi (Common ghost tap victims), r/LineageOS (Power users).
*   **Post Format:** [Dev] I made an open-source tool to fix ghost taps on rooted Android phones.
*   **Key Hooks:** Focus on the "Save your device" and "Open Source" aspects.
*   **Example Pitch:** "I got tired of my old phone clicking random buttons, so I wrote a root module that filters them out at the system level. It uses spatial clustering to tell the difference between you and a failing digitizer."

### Telegram Groups
**Why:** Direct access to active modders.

*   **Channels:** Magisk, KernelSU, LSPosed, and device-specific "Photography" or "Development" groups.
*   **Format:** Short, punchy message with a screenshot or short video.
*   **Example Pitch:** "New module alert: InputBlocker. Fixes ghost taps using LSPosed or Overlay mode. Perfect for those cracked screens that still have some life in them. Check it out on GitHub."

### GitHub
**Why:** Central hub for the code and technical documentation.

*   **Strategy:** Use GitHub Topics effectively. Pin a "Show and Tell" discussion in the Discussions tab.
*   **Topics:** `android`, `root`, `magisk`, `lsposed`, `xposed-module`, `ghost-taps`, `input-dispatcher`.

## Pitch Angle Suggestions

*   **The "Repair Alternative":** "Don't throw away your phone. Fix ghost taps for free with software."
*   **The "Technical Superiority":** "The only open-source ghost-tap filter that hooks directly into InputDispatcher for zero-latency filtering."
*   **The "Customization":** "Use the PC Designer tool to map out your dead zones with surgical precision."

## Content Marketing

### Demo Video Script Outline (60-90 seconds)
1.  **The Problem (0-15s):** Show a device with "Show Taps" enabled in Developer Options, flickering with ghost touches.
2.  **The Solution (15-30s):** Open InputBlocker, enable the service.
3.  **The Result (30-60s):** Show the same device. Ghost taps are visible (if using overlay) but the UI isn't reacting to them.
4.  **The Tools (60-75s):** Quick montage of the PC Designer and the companion app settings.
5.  **Call to Action (75-90s):** "Download on GitHub. Link in description."

### Before/After Comparison
*   Side-by-side video of a "Ghost Tap Test" app.
*   Left side: Unfiltered (chaos).
*   Right side: InputBlocker active (clean).

### Screenshot Strategy
*   Capture the "Visualizer" mode showing blocked taps in red and real taps in green.
*   Show the PC Designer tool with a complex filter map.
*   Clean, Material You interface of the companion app.

## Community Building

### Growing the Support Group
*   Link the Telegram/Matrix group in the README and app "About" section.
*   Be active in responding to initial queries.
*   Create a FAQ based on common setup hurdles.

### Contributor Onboarding
*   Maintain a "Good First Issue" label.
*   Keep the `BUILD.md` updated so it's easy to compile.
*   Document the hook logic in `DOCUMENTATION.md` for other developers.

## SEO / Discoverability

### Keywords to Target
*   Android ghost tap fix
*   Phantom touch filter root
*   LSPosed input blocker
*   Fix cracked screen touches
*   InputDispatcher hook example

### Package Name Optimization
*   Ensure the package name `com.inputblocker` or similar is used consistently in all listings to help with search indexing.
