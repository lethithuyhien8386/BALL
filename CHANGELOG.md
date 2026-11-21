# BubblePilot Astra changelog

## v0.4.0 — no-action fix from second screenshot

- Fix: add blue/cyan, purple and neutral special bubble classification to the prior R/O/G-only classifier.
- Fix: detect grid top-left origin and pitch on either original 422×757 or shifted 441×791 game layouts.
- Fix: reject colored wallpaper/scene boundaries that previously registered as phantom bubbles.
- Fix: preserve real screen coordinates when downsampling for faster on-device vision.
- Improve: floating-bar diagnostics show capture/recognition/wait/gesture outcomes instead of the generic "CHỜ BÓNG".
- Improve: reset calibration on Play; keep shot-gate state between frames to prevent duplicates.
- Add: regression test for the exact screenshot where the previous build would not tap.
- Add: GitHub CI emulator install-and-launch checks; publish a separate verified artifact only when both pass.
- Keep: `gpt-6-astra`, encrypted key, two permissions, draggable floating bar, Start/Stop, local pixel scoring, optional Astra fallback.

This change cannot prove successful interaction with the live game until tested on that device. Please do not interpret static-image tests as a real gameplay success.
