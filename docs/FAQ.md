# Frequently Asked Questions

---

## Compatibility

**Q: What Android versions are supported?**  
A: Android 6.0+ (API 23). The Xposed hook targets `InputDispatcher.dispatchMotionLocked`, which has been stable across major Android versions.

**Q: Does this work on any device?**  
A: Any rooted device with LSPosed/Vector installed. The root manager can be Magisk (≥20400), KernelSU, or APatch.

**Q: Will an OTA update break InputBlocker?**  
A: Possibly. Major Android system updates sometimes change internal APIs. After an OTA, check LSPosed to confirm the module is still active, and test that filtering works. If it broke, a module update will be needed.

**Q: Can I use this without root?**  
A: No. System-level touch interception requires root access.

**Q: Can I use this without LSPosed?**  
A: Yes — the companion app has "Overlay Mode" which uses a WindowManager overlay instead of the Xposed hook. It's less precise (apps might briefly see touches before the overlay catches them), but it works on any rooted device without LSPosed.

---

## Functionality

**Q: How does InputBlocker differ from just blocking a screen region?**  
A: Simple screen blocking is binary — the entire region is either dead or alive. InputBlocker adds two extra dimensions: **pressure filtering** (ghost taps have very low pressure) and **duration filtering** (stuck pixels produce unnaturally long holds). This means real finger touches in a "blocked" zone still work if they have normal pressure/duration.

**Q: Can I have different configs for different apps?**  
A: Yes. Place a config named `<package_name>.conf` in the `profiles/` directory. The engine polls the foreground app every 2 seconds and loads the matching profile. If none matches, `default.conf` is used.

**Q: What's the difference between a block zone and an exclude zone?**  
A: 
- **Block zone**: Touches here are filtered (blocked if pressure/duration conditions match)
- **Exclude zone**: A "hole" inside a block zone — touches here always pass through

Exclude zones let you block a large area while keeping specific UI elements (like a keyboard or a button) fully functional.

**Q: How do normalized coordinates work?**  
A: All coordinates are 0.0–1.0 and represent a percentage of the screen, not raw pixels. A block zone at `(0.0, 0.0, 0.5, 1.0)` covers the left half of the screen regardless of resolution. This means a config works across different devices and screen sizes.

---

## Performance & Battery

**Q: Will this drain my battery?**  
A: Minimal impact. Filtering is a sub-millisecond check in the input dispatch hot path. The logging system uses a dedicated background thread so disk I/O never blocks touch processing. The foreground app poller runs every 2 seconds — negligible overhead.

**Q: Will I notice input lag?**  
A: No. The filtering is a simple arithmetic check (pressure threshold, duration check, coordinate bounds) — we're talking microseconds. The Xposed hook runs inside `InputDispatcher.dispatchMotionLocked` before any app processing happens.

---

## Ghost Tap Specifics

**Q: How do I find my ghost tap zones?**  
A: Two methods:
1. **Auto-Detection** (easiest) — Run it from the companion app. It puts the device to sleep, wakes it, captures ghost tap samples, and runs DBSCAN clustering to suggest optimized blocking regions.
2. **Manual observation** — Use the PC Designer's visualizer or a developer option "Show Touches" to watch ghost locations, then draw regions over them.

**Q: What if ghost taps change over time?**  
A: Failing digitizers often worsen. Run auto-detection periodically to capture new ghost tap patterns. The module also has **adaptive optimization** (`AdaptiveBlockingManager`) that reads the block log and tightens region bounds when it detects ≥10 ghost taps at similar locations.

**Q: Can I share my config with someone with the same device?**  
A: Yes — export as `.ibpreset` from the companion app. Because coordinates are normalized (0.0–1.0), the preset is device-agnostic. The preset includes device model metadata for reference.

**Q: What happens if my digitizer fails completely?**  
A: If the screen generates ghost taps everywhere, filtering becomes impractical — every touch would be blocked. At that point, InputBlocker can't help, and hardware repair or replacement is the only option.

---

## Crash Safety

**Q: What if the module crashes?**  
A: Two-layer protection:
1. **Hot-path** — All hook code is wrapped in try/catch. If the filter throws, it logs the error and passes the touch through (fail-open).
2. **Boot-time** — If a crash is detected across a reboot, a `crash_detected` flag is set. The service manager reads this on boot and enters safe mode (all blocking disabled) until you clear the flag.

**Q: Can a bad config cause a boot loop?**  
A: Only if the config crashes the LSPosed hook during system boot. This is rare — config parser errors cause fail-open, not crash. If it happens, boot Safe Mode, disable InputBlocker in LSPosed, then fix the config.

---

## Project

**Q: Is this open source?**  
A: Yes — MIT license. The full source is on [GitHub](https://github.com/Laviesss/InputBlocker).

**Q: How can I contribute?**  
A: See [CONTRIBUTING.md](../CONTRIBUTING.md). Bug reports, feature requests, and code PRs are all welcome. Physical device testing is especially valuable since emulators can't reproduce ghost taps.

**Q: How do I report a bug?**  
A: Open a [GitHub Issue](https://github.com/Laviesss/InputBlocker/issues) with device model, Android version, your config file, and logs (use the **Share Log** button in the companion app).
