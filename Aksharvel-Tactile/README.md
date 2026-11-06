# Aksharvel - Tactile Mode (Milestone 1) - v4 Date

Offline Marathi Varnamala listening game, following the project wireframes:
path home (भाग / letter nodes), "which letter did you hear?" (4 large options + तपासा),
drag-the-letter question (3 tiles), and the lesson-complete screen.
Each question gives 3 tries (hearts); after that the answer is shown.
Nothing is locked: any letter can be opened at any time. Lesson = 6 questions (the letter + review letters).
सराव tab: random letters from the whole Varnamala, or only finished letters. Progress is saved on the phone.
Not in this milestone: writing/tracing (✏️) and speaking (🎤) - shown locked.

## Versioning
Every build has a number and a fruit name, so bug reports can say exactly which build they are about
(e.g. "bug in v2 Banana"). Both show in the app (top of the home screen and bottom of the "मी" tab)
and in the APK file name (e.g. `Aksharvel-v4-Date-debug.apk`).

| Build | Name |
|---|---|
| v1 | Apple |
| v2 | Banana |
| v3 | Cherry |
| v4 | Date |
| v5 | Elderberry |
| v6 | Fig |
| v7 | Grape |
| v8 | Honeydew |
| v9 | Imbe |
| v10 | Jackfruit |

To release a new build, edit the three lines at the top of `app/build.gradle.kts`
(`appVersion`, `appVersionCode`, `appCodename`) - nothing else needs to change.

## Release log
- v1 Apple - Tactile mode (listen and choose, drag the letter, lesson complete); voice via Android text-to-speech.
- v2 Banana - Voice is now built into the APK: recorded clips for the aksharas (normal + slow), no voice download or device settings needed.
- v3 Cherry - Added the missing recording for य and the new letter त्र (between क्ष and ज्ञ); all 50 letters now use recorded clips.
- v4 Date - Writing mode (new "✏️ लिहा" tab) with three modes: गिरवा (trace), पाहा आणि लिहा (see, then write), ऐका आणि लिहा (hear, then write). Strokes are checked on the phone by `Classifier.kt`.

## Build the APK
1. Install Android Studio (Koala or newer). Open this folder (`Aksharvel-Tactile`).
2. Let Gradle sync (needs internet ONCE on the build machine, not on the child's phone).
3. Build > Build Bundle(s) / APK(s) > Build APK(s).
4. The file is at `app/build/outputs/apk/debug/Aksharvel-v4-Date-debug.apk`. Copy it to the phone and install
   (allow "install unknown apps"). Or plug in the phone and press Run.

## Writing mode (✏️ लिहा tab)
Each round is 5 letters: learned letters not yet written first, then the next letters on the path.
- **गिरवा (trace):** a faint letter is shown; the child traces it. A missing top bar or weak quarter is highlighted.
- **पाहा आणि लिहा (see, then write):** the letter is shown big, then shrinks into the top-right corner while the child writes it on the empty pad. "👀 पाहा" shows it big again.
- **ऐका आणि लिहा (hear, then write):** the letter is played (🔊 / 🐢) and nothing is shown; the child writes it. After 2 misses a faint hint appears.
- Three tries per letter (♥). After the third the answer is shown and the app moves on, so a wrong grade never blocks a child.
- Grading (`Classifier.kt`, ported from the HTML prototype): coverage of the letter, ink outside it, scribble length, a missing top bar, and a weak quarter. In the two write-it-yourself modes the drawing is first scaled and shifted to the letter's box, so size and position do not matter.
- Thresholds are constants at the top of `Classifier.kt`. Tune them after testing with real children's writing.
- Correctly written letters are counted on the "मी" tab.

## Offline behaviour
- The app has no INTERNET permission and no network code.
- Sound comes from recorded clips inside the APK (`app/src/main/assets/audio/`), so it works on any
  phone with nothing to install. Each letter has a normal clip and a slower "हळू" clip.
- File names are `ak_<hex codepoints>.mp3` (e.g. अ = `ak_0905.mp3`, slow = `ak_0905_slow.mp3`).
- To replace or add a clip, drop a file with that name into the folder and rebuild.
- If a letter has no clip, the app falls back to Android text-to-speech for that letter only.

## Text-to-speech fallback
`SPOKEN_OVERRIDES` in `Varnamala.kt` only affects letters that have no recorded clip.
