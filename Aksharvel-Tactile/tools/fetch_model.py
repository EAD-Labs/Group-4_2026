"""
Downloads a Marathi speech model into app/src/main/assets/asr/.

    pip install huggingface_hub
    python tools/fetch_model.py

Run this from the Aksharvel-Tactile folder. It searches a list of candidate
Hugging Face repos, shows you what each one actually contains, and installs the
ONNX model plus its tokens file where Recogniser.kt expects them.

Nothing here is guesswork-free: repo names and file layouts on Hugging Face
change, and some AI4Bharat repos are gated. If every candidate fails the script
tells you exactly what it tried so you can finish by hand.
"""

import argparse
import os
import shutil
import sys

ASSET_DIR = os.path.join("app", "src", "main", "assets", "asr")
MODEL_NAME = "model.int8.onnx"
TOKENS_NAME = "tokens.txt"

# Ordered best-guess first. Each is a Hugging Face repo id.
CANDIDATES = [
    "parismitaglobalsolutions/indicconformer-sherpa-onnx",
    "meetsync/indic-conformer-onnx-sherpa",
    "trysem/indicconformer-120m-onnx",
    "ai4bharat/indicconformer_stt_mr_hybrid_ctc_rnnt_large",
]


def die(msg):
    print(f"\n{msg}")
    sys.exit(1)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", help="use this Hugging Face repo instead of the candidates")
    ap.add_argument("--lang", default="mr", help="language code to prefer in filenames (default mr)")
    ap.add_argument("--list-only", action="store_true", help="just show what each repo contains")
    args = ap.parse_args()

    if not os.path.isdir(os.path.join("app", "src", "main")):
        die("Run this from the Aksharvel-Tactile folder (the one with settings.gradle.kts).")

    try:
        from huggingface_hub import HfApi, hf_hub_download
    except ImportError:
        die("Missing package. Run:  pip install huggingface_hub")

    api = HfApi()
    repos = [args.repo] if args.repo else CANDIDATES

    for repo in repos:
        print(f"\n--- {repo}")
        try:
            files = api.list_repo_files(repo)
        except Exception as e:
            first = str(e).splitlines()[0][:160]
            print(f"    unavailable: {type(e).__name__}: {first}")
            if "gated" in str(e).lower() or "401" in str(e):
                print("    This repo is gated. To use it:")
                print(f"      1. open https://huggingface.co/{repo}")
                print("      2. sign in and accept the terms on that page")
                print("      3. make a read token at huggingface.co/settings/tokens")
                print("      4. run:  huggingface-cli login")
            continue

        onnx = [f for f in files if f.endswith(".onnx")]
        toks = [f for f in files if os.path.basename(f) in ("tokens.txt", "vocab.txt")]
        print(f"    {len(files)} files, {len(onnx)} onnx, {len(toks)} tokens/vocab")
        for f in onnx[:12]:
            print(f"      onnx:   {f}")
        for f in toks[:12]:
            print(f"      tokens: {f}")

        if args.list_only:
            continue
        if not onnx or not toks:
            print("    no usable model + tokens pair here, moving on")
            continue

        # Prefer: a whole CTC model over a transducer part, then one naming the
        # language, then an int8 build, then the shortest name.
        def rank(f):
            n = f.lower()
            parts = n.replace("\\", "/").replace("_", "/").replace("-", "/").split("/")
            lang_hit = args.lang.lower() in parts
            is_component = any(
                p in parts for p in ("encoder", "decoder", "joiner")
            )
            return (
                1 if is_component else 0,
                0 if lang_hit else 1,
                0 if "int8" in n else 1,
                len(f),
            )

        model_file = sorted(onnx, key=rank)[0]
        # pick the tokens file sitting beside the chosen model where possible
        same_dir = [t for t in toks if os.path.dirname(t) == os.path.dirname(model_file)]
        tokens_file = (same_dir or sorted(toks, key=len))[0]

        print(f"\n    downloading {model_file}")
        print(f"    and         {tokens_file}")
        print("    (this is a few hundred MB — leave it to finish)")
        try:
            m = hf_hub_download(repo, model_file)
            t = hf_hub_download(repo, tokens_file)
        except Exception as e:
            print(f"    download failed: {type(e).__name__}: {str(e).splitlines()[0][:160]}")
            continue

        os.makedirs(ASSET_DIR, exist_ok=True)
        shutil.copyfile(m, os.path.join(ASSET_DIR, MODEL_NAME))
        shutil.copyfile(t, os.path.join(ASSET_DIR, TOKENS_NAME))

        size = os.path.getsize(os.path.join(ASSET_DIR, MODEL_NAME)) / 1e6
        print(f"\n    installed into {ASSET_DIR}")
        print(f"      {MODEL_NAME}  {size:.0f} MB")
        print(f"      {TOKENS_NAME}")
        print("\nNext:")
        print("  1. In app/build.gradle.kts, uncomment the sherpa-onnx line")
        print("  2. Sync Gradle, then build and install")
        print("  3. Open the 🎤 बोला tab and say a letter")
        print(f"\nThe APK will grow by about {size:.0f} MB. Check free space on the tablets.")
        return

    die(
        "No candidate worked.\n"
        "Search Hugging Face for 'indicconformer sherpa onnx', then rerun with:\n"
        "  python tools/fetch_model.py --repo <owner/name>\n"
        "Or place the files by hand:\n"
        f"  {os.path.join(ASSET_DIR, MODEL_NAME)}\n"
        f"  {os.path.join(ASSET_DIR, TOKENS_NAME)}"
    )


if __name__ == "__main__":
    main()
