# Speaking mode — turning it on

The speaking mode is wired in and the app builds right now, but it **cannot
recognise speech until you add a model**. Until then the 🎤 बोला tab opens and
says the practice is not ready, and nothing else in the app changes. That is
deliberate: you can build and ship an APK today without waiting on the model.

## What is already done

| Piece | State |
|---|---|
| `Devanagari.kt` — splits Marathi into letters a reader would count | Built, 13 tests passing |
| `PronunciationScorer.kt` — grades a spoken attempt | Built, 13 tests passing |
| `Listener.kt` — 16 kHz mic capture, in memory only | Built, type-checked |
| `Recogniser.kt` — sherpa-onnx wrapper, optional at runtime | Built, type-checked |
| `SpeakScreens.kt` — the 🎤 बोला tab and session | Built, not compiled |
| `RECORD_AUDIO` permission | Added |
| Store tracks letters spoken correctly | Added |
| The speech model itself | **You need to add this** |

## Step 1 — get a Marathi model

### The quick way

From the `Aksharvel-Tactile` folder:

```
pip install huggingface_hub
python tools/fetch_model.py
```

Windows users can double-click `tools/FETCH_MODEL.bat` instead.

It tries several known Hugging Face repos, prints what each actually contains,
picks the Marathi int8 model and its tokens file, and installs both into
`app/src/main/assets/asr/` under the names `Recogniser.kt` expects.

See first what is in a repo without downloading:

```
python tools/fetch_model.py --list-only
```

Use a specific repo:

```
python tools/fetch_model.py --repo owner/name
```

If every candidate fails, the script prints exactly what it tried. Repo names
and layouts on Hugging Face move around, so this may need a `--repo` you find
by searching "indicconformer sherpa onnx" yourself.

### Doing it by hand

The app expects a sherpa-onnx CTC model. Known starting points:

- `ai4bharat/indicconformer_stt_mr_hybrid_ctc_rnnt_large` — AI4Bharat's
  Marathi-specific model, from IIT Madras, MIT licensed
- Community sherpa-onnx ONNX exports of IndicConformer, int8 quantised,
  roughly 190 MB

Search Hugging Face for "indicconformer sherpa onnx". Some AI4Bharat repos are
gated and need you to accept the licence and sign in before downloading.

## Step 2 — put it in assets

```
app/src/main/assets/asr/model.int8.onnx
app/src/main/assets/asr/tokens.txt
```

The names must match exactly, or change the constants at the top of
`Recogniser.kt`.

## Step 3 — enable the library

In `app/build.gradle.kts`, uncomment:

```kotlin
implementation("com.k2-fsa.sherpa:sherpa-onnx:1.10.32")
```

Check the current coordinate and version first. If the Maven artifact does not
resolve, download the prebuilt Android AAR from the sherpa-onnx releases page
and drop it in `app/libs/` instead, adding:

```kotlin
implementation(fileTree("libs") { include("*.aar") })
```

Sync Gradle.

## Step 4 — check the call signature

`Recogniser.transcribe` reaches sherpa-onnx by reflection and looks for a method
named `recognise` or `recognize`. **I have not been able to run this against the
real library.** If the speaking tab loads the model but every attempt comes back
empty, the signature is the thing to check — open `Recogniser.kt`, look at the
`transcribe` function, and compare it against the sherpa-onnx Kotlin API docs.

## Step 5 — test it

1. Build and install
2. Open the 🎤 बोला tab — it should offer to start, not report "not ready"
3. Say a letter and watch what comes back

If it says "not ready", check Logcat for lines beginning `Recogniser` — the
`detail` field explains which step failed.

## Size warning

A 190 MB model means an APK around 200 MB. **Check the free space on the school
tablets before you commit to bundling it.** If space is tight, the alternative
is to ship the model as a separate download on first run — but that would need
the INTERNET permission, which the app currently and deliberately does not have.

## Worth knowing about accuracy

The scoring thresholds in `PronunciationScorer.kt` — 65% to pass a short letter,
40% to count as close — are reasoned guesses, not measured values. Speech models
are also trained mostly on connected speech, so performance on a single isolated
letter can be poor regardless of the model. Test on real recordings from real
children early, and expect to tune the thresholds or scope the mode to words
rather than single letters.
