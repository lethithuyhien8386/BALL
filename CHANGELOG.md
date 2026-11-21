# v0.9.0 — Skills only / full-screen + combo dialog

- Preserve v0.8 normal shot planner and ShotGate. Add only dedicated skill board/launcher helpers and skill dispatch changes.
- Discover movable inventory icons using rim/appearance search; add X2 and full-screen golden/rainbow templates from the new screenshot.
- Prioritize X2 immediately, followed by TIME, independently of the ordinary shot gate. Ignore blue.
- Recognize the combo dialog even when the launcher is hidden, score the actual three available colors with the unchanged planner, tap the selected option, then confirm the loaded color and replan for it.
- Reserve gold for the no-ordinary-POP fallback, after an eligible rainbow; verify golden launcher before firing. No assumed blast radius.
- Add screenshot, slot relocation, scale, selected-color, pending-state and synthetic loaded-projectile tests. Existing Java regression suite passes; no Android build/run performed here.
- Version code 9 / name 0.9.0; updated GitHub workflow artifact names.

# v0.8.0 — Offline / video training regression

- Remove remote advisor, credential UI, remote configuration, and Internet permission; retain only credential cleanup on upgrade.
- Separate yellow/orange and magenta/violet, improve recorded viewport calibration and reject score screens.
- Add skill icon template matching, state machine, time / gold / rainbow selection, explicit blue exclusion. X2 awaits a verified icon template.
- Add in-flight detection, preview in the turn signature, confirmed-flight handling, overlay hit guard, and plan caching.
- Preserve existing geometry/occlusion/installation checks; add real-frame training regression and skill controller tests.
- Change package version to 0.8.0 / code 8, update GitHub artifact naming. APK not built in the editing environment.
