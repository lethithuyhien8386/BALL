# BubblePilot Astra v0.4.0 — Android/GitHub source

## Why the previous build showed “CHỜ BÓNG”

The earlier image classifier only recognized red, orange and green. The second game screenshot contains purple, cyan/blue and a white special bubble, and the entire board begins lower on the screen than in the first image. The detector therefore returned an empty result and the bot did not tap. **v0.4 fixes the recognizer and automatically fits the grid to the screenshot.** This is a source-code release, not a prebuilt APK. A small (~34 KB) Java APK is not inherently invalid: the remote Astra model is not bundled in the APK.

## Build on GitHub

1. **Extract** the ZIP and upload all its contents to the root of your GitHub repository, including the hidden `.github` directory. Replace older source files; do not leave two workflows building different versions.
2. Open **Actions > Build and verify Bubble Pilot Astra v0.4 APK > Run workflow**.
3. Wait for the green `build` job **and** both green `emulator-install-check` jobs (Android 9/API 28 and Android 11/API 30), then the green `publish-verified` job.
4. Download the **BubblePilot-Astra-v0.4-APK-InstallChecked** artifact. Extract the artifact ZIP and install **app-debug.apk**. The artifact ZIP itself is not an APK.
5. If the emulator job has an infrastructure/KVM problem, the separately named `BubblePilot-Astra-Candidate` artifact may exist, but it is **NOT** marked install-checked. Inspect the logs before treating it as verified.

Build uses JDK 17, Gradle 8.9, AGP 8.7.3 and compile SDK 35. The debug APK is signed by Gradle and structurally checked for its DEX, manifest, signature, and alignment. The workflow then runs Android emulator install/start tests. These CI tests have **not been executed in the current environment** because it has no Android SDK and no external network access.

## Run on Android 8.0+

1. Uninstall the older BubblePilot if Android refuses an update due to a different debug signing key. This clears app settings/API key.
2. Open Bubble Pilot v0.4.0; grant **CLICK** (Android Accessibility Service) and **FLOAT** (Display over other apps). On Android 8–10 also grant **CAPTURE** (MediaProjection) using the button and system prompt.
3. Under settings, the model name is **gpt-6-astra**, endpoint is **https://api.xingchiapi.com/v1**, and key is stored encrypted on the phone (Android Keystore). Enter only a key you trust with that third-party provider. You can run local pixel detection without entering a key.
4. Tap **LƯU CÀI ĐẶT**, then **START — HIỆN THANH NỔI**, switch to the game, press **▶** to start. Move the bar by dragging **⠿**. Press **■** to stop or **×** to close.
5. When recognition succeeds, the app chooses a trajectory and sends an Android Accessibility tap. Afterward it monitors for a changed board and two stable frames before tapping again.

### Floating-bar diagnostics

- `KHÔNG THẤY LƯỚI`: grid could not be recognized; check screenshot, game orientation and whether the game is visible.
- `KHÔNG THẤY BÓNG BẮN`: board visible, projectile not recognized; check launcher position.
- `KHÔNG CÓ GÓC`: no valid simulated path found; may indicate an incompatible game layout.
- `CHỜ LƯỢT`: board state has not progressed sufficiently or a new frame has not stabilized.
- `POP N` / `AIM XX`: a simulated tap was successfully handed to Android's gesture dispatcher. This does not prove the game accepted it.
- `TAP ERR` / `TAP FAIL`: Accessibility rejected/cancelled the tap; check service permissions and game restrictions.
- `SCREEN [code]` / `CAPTURE OFF`: screenshot permission/system capture issue.

## What was fixed in v0.4

- **Pixel classification** recognizes `R` red, `O` orange, `G` green, `B` cyan/blue, `P` purple, `W` neutral/white special objects (obstacles, not normal color matches). The white type is recognized as a blocker; its actual in-game effect still needs testing.
- **Automatic geometry fitting** scans plausible first-row coordinates, horizontal/vertical grid spacing and center placement. It rejects straight scenery edges masquerading as circles. Calibrations are reused after a valid frame for faster continuous processing, and reset on Start.
- **Visible-pixel scoring and occlusion** exclude the circle area covered by neighboring balls. The solver traces candidate paths up to the first impact (including wall rebounds), estimates matching 3+ clusters and falling unsupported bubbles, and refines the best angle. It does not simply click a raw color on the screenshot.
- **High-resolution scaling** analyzes images over 560 pixels wide at a smaller resolution, and converts tap coordinates back to the real device screen size.
- **Continuous play** uses an observable board/shooter signature with a 2-frame stability gate after each shot; this avoids repeated taps at an unchanged board. Screenshot cadence is ~115ms plus processing and game animation. Astra can be requested for uncertain local decisions, with a 170ms maximum wait before the local fallback.
- **Clearer statuses** replace the ambiguous `CHỜ BÓNG` text so detection/gesture failure causes can be distinguished.

## Tests completed on the supplied screenshots

`bash tests/test.sh` — passes on JDK 17:

- Original 422×757 image: detects **76 balls**, active projectile **green**, and produces a simulated shot.
- New 441×791 screenshot (with floating bar): detects **64 balls**, active projectile **purple**, at least one **blue** bubble and the **white special** bubble, and produces a simulated tap near `(281,424)`.
- The new screenshot does not misidentify the background under the bubble stack as another row of bubbles.
- 2× resized screenshot test checks the detector remains scale-aware.
- Continuous-shot state machine test ensures 2 stable frames and prevents duplicate taps on the same unchanged board.
- On a desktop JDK, the new screenshot took ~80–150ms to calibrate and produce a shot the first time; later same-layout frames took ~5ms for vision and ~2ms for trajectory choice. **These are not Android performance measurements**.

## Important limitations

This is **not** a guarantee of fastest or perfect play. Actual game mechanics (special power-ups, hidden physics, shooting gesture, animation, score multipliers), phone performance, screen overlays and in-game anti-automation rules can affect accuracy. Tests on static screenshots cannot verify that the game responds to Accessibility taps. If the bar says `AIM` or `POP` but the game ignores taps, the game's interaction mode/gesture likely needs further calibration on an actual device.

The third-party Astra API endpoint/model's availability was supplied by the user and has not been independently verified. Setting `store:false` requests non-storage but cannot guarantee the provider's data policy. Never put a real API key into your public GitHub repository.
