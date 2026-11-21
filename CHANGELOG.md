# v0.6.0 — Video-driven no-penalty fix

- Guarded gesture dispatch against any predicted shot with fewer than three matching balls or <80% trajectory stability.
- Expanded display-geometry calibration for letterboxed/narrower arenas from actual video.
- Learned left/right playfield walls instead of outer recording borders.
- Added dual checkerboard parity (wide/narrow ceiling row) with recalibration after row changes.
- Dynamically detected shooter position and color; blocked false cyan dragon pixels and projectile-in-flight frames.
- Validated optional Astra suggestions through local `safeShotAtTap` with match/stability requirements; no blocking of a verified local shot.
- Added video regression tests from seven user-supplied frames and no-match synthetic game state.
- Preserved Android 8+ setup, Accessibility & floating overlay, and GitHub APK build/install verification.
