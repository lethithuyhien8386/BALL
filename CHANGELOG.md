# v0.7.0 — Continuous fire, fastest local planning, video 2 regression

- Remove no-POP automatic idle: every valid current ball gets a physics-checked trajectory. Strongly favor pops first, then same-color future setups, then low-cost next-ball advances.
- Fix launcher misidentification with rotated cannon and animated blue character at 68s/100s of user video 2.
- Refit shifted lattice when observed ball count drops sharply; resolve partial-grid scoring from the user video.
- Expand candidate diversity from 28 to 96 routes, include angle perturbations and wall banks.
- Cut nominal frame check interval from 115 to 55ms and minimum post-shot gate from 240 to 120ms, preserving two stable frames and adding screenshot API throttle backoff.
- Keep optionally enabled nonblocking GPT-6 Astra advice: no remote request can hold up a ready shot.
- Add VideoContinuousTest with 13 selected frames from user video 2. Retain older palette, geometry, trajectory and gate tests.
- Update version to 0.7.0 and GitHub Actions artifact names.

Caveat: Only offline frame tests were run in the creation environment; Android compilation, installation and live-game play must still be verified on GitHub/device.
