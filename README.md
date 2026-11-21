# BubblePilot Astra — Android + GitHub Actions

> **Prototype code**. Test screenshot + state machine PASS; Android APK has **not been compiled or run on a physical device here**. Do not assume exact in-game success rates or that the custom proxy API supports the requested model.

## 1. Build APK directly on GitHub (no Android Studio required)

The ZIP was built so that `.github/workflows/build-apk.yml` sits in the **root** of a GitHub repository, alongside `app/`, `build.gradle`, and `settings.gradle`.

1. Create a GitHub repository, preferably **Private**, and choose the default branch **main**.
2. **Extract** the ZIP and upload **all files and folders inside**, including hidden `.github/workflows/build-apk.yml`, into the repository root. Do **not** upload only the ZIP itself. For an upload through a terminal: `git add -A`, `git commit`, and `git push` are sufficient once your repository is configured.
3. Open **Actions** > **Build Android APK - Astra** > **Run workflow**. A push to `main` also starts the workflow automatically.
4. After a successful run, open the build and find **Artifacts** > **BubblePilot-Astra-debug-APK**.
5. Download and extract the GitHub artifact ZIP to obtain `app-debug.apk`. Transfer it to Android and install it (allow installation from that source if Android prompts you).

GitHub downloads Gradle **8.9**, Android SDK **35** and JDK **17**. The workflow uses `android-actions/setup-android@v4` to avoid the deprecated Android SDK `tools` package error seen with `@v3`. If you already uploaded an older ZIP to GitHub, replace `.github/workflows/build-apk.yml` with this updated file and commit before rerunning. The workflow tests the solver and state machine, compiles the debug APK, and publishes it as an artifact. **No private API key is needed in GitHub Actions.** Enter the key only inside the installed app.

Build can also be done in Android Studio using JDK 17 / Gradle 8.9 / SDK 35. There is no bundled Gradle-wrapper JAR; the GitHub workflow uses a known installed Gradle version instead.

## 2. Start the bot

- Open the installed app. Enable **CLICK (Accessibility)** and **FLOAT (Draw over other apps)** from the settings buttons.
- The app's Settings page defaults to endpoint `https://api.xingchiapi.com/v1`, model **`gpt-6-astra`**, and encrypted API key storage using Android Keystore AES-GCM.
- Press **LƯU CÀI ĐẶT**. Press **START — HIỆN THANH NỔI**.
- Open the bubble-shooter game and press **▶** on the floating bar. Drag the bar using **⠿**; press **■** to pause or **×** to close. The app doesn't require root.
- Keep the floating bar outside the grid area; otherwise it can cover balls in screenshots.

## 3. Continuous game logic

- Take screenshots through Android Accessibility screenshot API. Extract RGB pixels into memory; detect red, orange and green bubbles on a staggered grid, and compute the count of visible-color pixels per bubble while excluding overlaps with already detected bubbles.
- Search **311** half-degree candidate shots plus 11 fine refinements. Trace the projectile against the **first** bubble it hits, accounting for side-wall rebounds. Hidden balls behind blockers aren't treated as direct collision targets. Estimate groups removed (3+) and unsupported bubbles that drop; maximize predicted score with an exposed-pixel weight.
- Send tap through Accessibility gestures. Observe subsequent frames continuously and ensure **two matching valid frames** after seeing a change from the previous shot before shooting the next ball. This prioritizes no duplicate shots over maximum possible tapping rate. Frame recheck target is ~125ms but system screenshot throttling, animations, and hardware add latency.
- The **Astra** Responses API is only consulted if the local best shot scores poorly and a key is configured. Wait budget ~260ms, then fall back to the computed local shot; API requests can continue in the background, but only the current screenshot's result is eligible. An Astra suggestion is locally simulated and accepted **only if it scores higher**. Cloud replies may be slower than this budget and hence unused.
- The code sends `"model":"gpt-6-astra"`, `"store":false`, plus an image via the `/responses` endpoint. `astra-model.example.json` records the client-side context/output limits and variant names from the supplied configuration. These are not automatically transmitted to an API; your provider must actually support the model and route.

## 4. Important limitations

- The detector is **calibrated to the submitted 422 × 757 screenshot**. It assumes 3 bubble colors, simple match-3 rules, tap-to-aim, and a fixed staggered grid. Different layouts, aspect ratios, obstructed views, special bubbles, animation and post-shot rules may invalidate a shot.
- Continuous monitoring is built in, but a new ball must be detected and the screenshot must become stable. It **cannot be guaranteed instantaneous** on all phones and games. If the board does not visibly transition, the anti-double-tap state machine may wait rather than repeatedly tap.
- `gpt-6-astra` and the stated limits/variants are **user-supplied provider identifiers**: the third-party endpoint has not been verified. Passing `"store":false` requests non-storage, but does not guarantee a third party's retention policy.
- Accessibility can read what's on screen and tap on behalf of the user. Enable it only when desired. Some games restrict overlay, screenshot or synthetic taps.
- APK from CI is a **debug build**, not a production-signed release. No API key or secret is hardcoded in the ZIP.

## Layout

- `.github/workflows/build-apk.yml` — one-click GitHub Actions build + artifact
- `app/src/main/java/com/example/bubblepilot/MainActivity.java` — permissions, model, endpoint and encrypted key UI
- `GameAccessibilityService.java` — continuous screenshots, floating UI, gestures, Astra fallback
- `GameSolver.java` — pixel classification and score-maximizing trajectory planner
- `ShotGate.java` — no-repeat, new-ball detection state machine
- `RemoteAdvisor.java` — HTTPS `/responses` model call
- `SecretStore.java` — Android Keystore storage
- `tests/` — local Java tests: `bash tests/test.sh`
- `astra-model.example.json` — sanitized model-provider example, do not put a real key in Git
