# Changelog

## 1.1.0

- Expanded the plugin to work across JetBrains IDEs as a platform plugin instead of being tied to IntelliJ IDEA only.
- Added per-event sound and image/GIF configuration, including bundled defaults and custom files from the IDE config folder.
- Bundled `error.mp3`, `succeed.mp3`, `cooked.jpg`, and `happy.gif` directly into the plugin.
- Added in-IDE overlay previews for images and GIFs and kept notifications available independently of sound playback.
- Reworked the settings UI so it adapts better to the available space and no longer requires manual resizing to use comfortably.
- Fixed the default build-success sound to use the shipped `succeed.mp3` resource.
