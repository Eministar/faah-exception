# Changelog

## 1.1.2

- Added per-event image/GIF settings, separate sound and overlay durations, preset import/export, and notification quick actions for Build, Run, and Problems.
- Improved build-result routing so warnings no longer also trigger success sounds and higher-priority failures are not swallowed by global debounce timing.
- Improved overlay media rendering and updated the plugin icon so the logo appears larger and less zoomed out in JetBrains IDEs and the Marketplace.
- Kept the plugin compatible across JetBrains IDEs while continuing to bundle `error.mp3`, `succeed.mp3`, `cooked.jpg`, and `happy.gif`.
