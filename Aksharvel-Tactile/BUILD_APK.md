# Making the APK

Two routes. Android Studio is the reliable one, because this project is missing
its Gradle wrapper files.

## First: the missing wrapper

The zip contains `gradle/wrapper/gradle-wrapper.properties` but not:

```
gradlew
gradlew.bat
gradle/wrapper/gradle-wrapper.jar
```

Without those three, `gradlew` on the command line will not work. You have two
ways to fix it:

- **Copy them from your own working copy** of the project — you built v4, so your
  local folder has them. Copy all three across, keeping the jar in
  `gradle/wrapper/`.
- **Or let Android Studio rebuild them** — it regenerates the wrapper on sync.

If you only use Android Studio, you can ignore this entirely.

## Route A — Android Studio (recommended)

1. **File → Open** → select the `Aksharvel-Tactile` folder (the one holding
   `settings.gradle.kts`, not the folder above it)
2. Wait for the Gradle sync to finish — the first one takes a few minutes and
   writes `local.properties` with your SDK path
3. **Run the tests first:** right-click
   `app/src/test/java/com/aksharvel/tactile/PronunciationScorerTest.kt` →
   **Run 'PronunciationScorerTest'**. You should get 13 green ticks. If these
   fail, stop and fix that before building.
4. **Build → Build Bundle(s) / APK(s) → Build APK(s)**
5. When it finishes, click **locate** in the notification, or find it at:

```
app/build/outputs/apk/debug/Aksharvel-v4-Date-debug.apk
```

To install straight onto a plugged-in phone instead, use the green ▶ Run button.

## Route B — command line

Only works once the wrapper files are in place.

```powershell
cd "D:\path\to\Aksharvel-Tactile"
.\gradlew testDebugUnitTest      # 13 tests should pass
.\gradlew assembleDebug          # builds the APK
```

Note the `.\` — PowerShell will not run a program from the current folder
without it.

The APK lands in `app/build/outputs/apk/debug/`.

## A release APK

A debug APK is fine for demos and for sideloading onto school tablets. For a
signed release build:

```powershell
.\gradlew assembleRelease
```

That produces an unsigned APK, which Android will refuse to install. Signing it
needs a keystore — in Android Studio, **Build → Generate Signed Bundle / APK**
walks you through creating one. Keep the keystore file and its password safe:
without them you cannot ever update the app on a device that has it installed.

## Changing the build name

`app/build.gradle.kts` has three lines at the top that name the build:

```kotlin
val appVersion = "v4"
val appVersionCode = 4
val appCodename = "Date"
```

Bump all three together — v5 / 5 / Elderberry, following the fruit sequence in
the README. The APK filename follows automatically.

## If the build fails

| Message | Cause |
|---|---|
| `SDK location not found` | Let Android Studio finish its first sync; it writes `local.properties` |
| `gradlew is not recognized` | The wrapper files are missing — see the top of this file |
| `Unresolved reference: lifecycleScope` | The `lifecycle-runtime-ktx` dependency did not sync; re-sync Gradle |
| `Unresolved reference: SpeakHomeScreen` | `SpeakScreens.kt` is not in `app/src/main/java/com/aksharvel/tactile/` |
| Anything mentioning `sherpa` | The speech dependency is uncommented but unresolved — comment it back out; the app builds fine without it |

## What you get without the speech model

The APK builds and installs, all existing modes work unchanged, and the
🎤 बोला tab appears but reports that the practice is not ready. See
`SPEECH_SETUP.md` for switching it on.
