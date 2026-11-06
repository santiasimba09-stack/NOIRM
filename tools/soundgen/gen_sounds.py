"""Generates every NOIRM sound effect from scratch (no recorded or downloaded audio) and writes
assets/noirm/sounds/**.ogg plus assets/noirm/sounds.json.

    python3 tools/soundgen/gen_sounds.py                    # everything
    python3 tools/soundgen/gen_sounds.py --only combat.parry # one sound (all its variants)
    python3 tools/soundgen/gen_sounds.py --report DIR        # also stats + a spectrogram per file in DIR
    python3 tools/soundgen/gen_sounds.py --samples           # also refresh docs/sound-samples/

Recipes live in recipes.py, the building blocks in dsp.py. Output is mono 44.1 kHz Ogg Vorbis
(ffmpeg + libvorbis). The seed of each file is fixed (from its name), so running the script
twice gives the same sounds; change SEED_SALT to roll a fresh set.
"""
import argparse
import json
import os
import subprocess
import sys
import tempfile
import wave
import zlib

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
import dsp  # noqa: E402
from recipes import SOUNDS  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/noirm")
SOUND_DIR = os.path.join(ASSETS, "sounds")
SAMPLES_DIR = os.path.join(ROOT, "docs/sound-samples")
SEED_SALT = 7
VORBIS_QUALITY = "4"  # about 64-80 kbit/s mono: clean, and small

# How far away each group can be heard, in blocks (vanilla's default is 16).
ATTENUATION = {
    "big.": 40,
    "nature.explosion_boom": 32,
    "nature.": 24,
    "chakra.release": 24,
    "combat.wall_splat": 20,
    "ui.rank_up": 16,
}
# Long files stream from disk instead of sitting in memory.
STREAM = {"ambient.village", "ambient.sanctuary"}
SAMPLE_PICKS = ["seal.complete", "chakra.release", "combat.hit_heavy", "weapon.clash", "nature.fire_roar",
                "nature.lightning_snap", "nature.yin_swell", "big.impact", "ui.rank_up", "ambient.village"]


def file_base(event, variant):
    category, name = event.split(".", 1)
    return f"{category}/{name}_{variant}"


def master(x, peak_db, loop):
    x = np.asarray(x, dtype=float)
    x = dsp.remove_dc(x, circular=loop)
    if not loop:
        x = dsp.trim_silence(x, -60)
        x = dsp.fade(x, 0.001, min(0.04, len(x) / dsp.SR / 4))
    # Vorbis can overshoot by a dB or so on hard transients: keep that headroom.
    return dsp.norm(x, min(peak_db, -1.5))


def write_ogg(x, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    pcm = np.clip(np.round(x * 32767), -32768, 32767).astype("<i2")
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        wav_path = tmp.name
    try:
        with wave.open(wav_path, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(dsp.SR)
            w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav_path, "-ac", "1", "-ar", "44100",
                        "-c:a", "libvorbis", "-q:a", VORBIS_QUALITY, "-map_metadata", "-1", path], check=True)
    finally:
        os.remove(wav_path)


def decode_ogg(path):
    out = subprocess.run(["ffmpeg", "-loglevel", "error", "-i", path, "-f", "s16le", "-ac", "1", "-ar", "44100", "-"],
                         check=True, capture_output=True).stdout
    return np.frombuffer(out, dtype="<i2").astype(float) / 32768


def stats(x):
    spec = np.abs(np.fft.rfft(x * np.hanning(len(x)))) ** 2
    f = np.fft.rfftfreq(len(x), 1 / dsp.SR)
    centroid = float(np.sum(f * spec) / (np.sum(spec) + 1e-12))
    low = float(np.sum(spec[f < 150]) / (np.sum(spec) + 1e-12))
    return {
        "seconds": round(len(x) / dsp.SR, 3),
        "peak_db": round(20 * np.log10(np.max(np.abs(x)) + 1e-12), 2),
        "rms_db": round(20 * np.log10(np.sqrt(np.mean(x ** 2)) + 1e-12), 2),
        "dc": round(float(np.mean(x)), 5),
        "centroid_hz": round(centroid),
        "low_share": round(low, 3),
        "clipped": int(np.sum(np.abs(x) >= 0.999)),
    }


# ------------------------------------------------------------------ spectrograms (PIL, no matplotlib)

def _colour(v):
    """Ink palette: black -> deep red -> orange -> bone white."""
    stops = np.array([[0, 0, 0], [90, 10, 15], [200, 60, 20], [250, 190, 90], [250, 245, 230]], float)
    v = np.clip(v, 0, 1) * (len(stops) - 1)
    i = np.minimum(v.astype(int), len(stops) - 2)
    k = (v - i)[..., None]
    return (stops[i] * (1 - k) + stops[i + 1] * k).astype(np.uint8)


def spectrogram_png(x, path, title, width=420, height=180):
    from PIL import Image, ImageDraw
    frame, hop = 1024, max(64, len(x) // width)
    pad = np.concatenate([x, np.zeros(frame)])
    cols = []
    for s in range(0, len(x), hop):
        seg = pad[s:s + frame] * np.hanning(frame)
        cols.append(np.abs(np.fft.rfft(seg)))
    mag = np.array(cols).T  # freq x time
    f = np.fft.rfftfreq(frame, 1 / dsp.SR)
    edges = np.geomspace(30, 20000, height + 1)
    rows = []
    for lo, hi in zip(edges[:-1], edges[1:]):
        m = (f >= lo) & (f < hi)
        rows.append(mag[m].mean(axis=0) if m.any() else mag[np.argmin(np.abs(f - lo))])
    img = 20 * np.log10(np.array(rows)[::-1] + 1e-9)
    img = (img - (img.max() - 80)) / 80
    rgb = _colour(img)
    im = Image.fromarray(rgb).resize((width, height))
    canvas = Image.new("RGB", (width, height + 60), (20, 20, 20))
    canvas.paste(im, (0, 16))
    wave_y = height + 16
    d = ImageDraw.Draw(canvas)
    d.text((4, 2), title, fill=(230, 230, 230))
    env = np.abs(x)
    step = max(1, len(env) // width)
    for i in range(width):
        seg = env[i * step:(i + 1) * step]
        if len(seg):
            h = int(seg.max() * 20)
            d.line([(i, wave_y + 22 - h), (i, wave_y + 22 + h)], fill=(200, 200, 200))
    canvas.save(path)


def contact_sheet(paths, out, cols=4):
    from PIL import Image
    ims = [Image.open(p) for p in paths]
    w, h = ims[0].size
    rows = (len(ims) + cols - 1) // cols
    sheet = Image.new("RGB", (w * cols, h * rows), (0, 0, 0))
    for i, im in enumerate(ims):
        sheet.paste(im, ((i % cols) * w, (i // cols) * h))
    sheet.save(out)


# ------------------------------------------------------------------ main

def sounds_json(variants_of):
    data = {}
    for event, (_, count, _, _) in SOUNDS.items():
        entries = []
        for v in range(1, variants_of(event) + 1):
            e = {"name": f"noirm:{file_base(event, v)}"}
            for prefix, dist in ATTENUATION.items():
                if event.startswith(prefix):
                    e["attenuation_distance"] = dist
                    break
            if event in STREAM:
                e["stream"] = True
            entries.append(e if len(e) > 1 else e["name"])
        data[event] = {"subtitle": f"subtitles.noirm.{event}", "sounds": entries}
    return data


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", nargs="*", help="event ids to (re)generate")
    ap.add_argument("--report", help="folder for stats.json, spectrograms and contact sheets")
    ap.add_argument("--samples", action="store_true", help="refresh docs/sound-samples/")
    args = ap.parse_args()

    events = args.only or list(SOUNDS)
    report = {}
    pngs = []
    for event in events:
        recipe, count, peak_db, loop = SOUNDS[event]
        for v in range(1, count + 1):
            seed = zlib.crc32(f"{event}#{v}#{SEED_SALT}".encode())
            rng = np.random.default_rng(seed)
            x = master(recipe(rng, v), peak_db, loop)
            path = os.path.join(SOUND_DIR, file_base(event, v) + ".ogg")
            write_ogg(x, path)
            for _ in range(6):  # Vorbis can overshoot on hard transients: pull it back under -0.5 dB
                over = np.max(np.abs(decode_ogg(path)))
                if over < 10 ** (-0.5 / 20):
                    break
                x = x * (10 ** (-1.2 / 20) / over)
                write_ogg(x, path)
            if args.report:
                y = decode_ogg(path)
                s = stats(y)
                s["bytes"] = os.path.getsize(path)
                if loop:
                    s["loop_seam"] = round(float(abs(x[-1] - x[0])), 5)
                report[f"{event}#{v}"] = s
                png = os.path.join(args.report, f"{event}_{v}.png")
                spectrogram_png(y, png, f"{event} #{v}  {s['seconds']}s  pk {s['peak_db']}  rms {s['rms_db']}  c {s['centroid_hz']}Hz")
                pngs.append(png)
            print(f"{event} #{v}", flush=True)

    with open(os.path.join(ASSETS, "sounds.json"), "w", encoding="utf-8") as f:
        json.dump(sounds_json(lambda e: SOUNDS[e][1]), f, indent=2)
        f.write("\n")

    if args.report:
        with open(os.path.join(args.report, "stats.json"), "w") as f:
            json.dump(report, f, indent=1)
        for i in range(0, len(pngs), 16):
            contact_sheet(pngs[i:i + 16], os.path.join(args.report, f"sheet_{i // 16:02d}.png"))

    if args.samples:
        os.makedirs(SAMPLES_DIR, exist_ok=True)
        for event in SAMPLE_PICKS:
            src = os.path.join(SOUND_DIR, file_base(event, 1) + ".ogg")
            dst = os.path.join(SAMPLES_DIR, event.replace(".", "_") + ".ogg")
            with open(src, "rb") as a, open(dst, "wb") as b:
                b.write(a.read())

    total = sum(os.path.getsize(os.path.join(d, n)) for d, _, ns in os.walk(SOUND_DIR) for n in ns)
    print(f"total audio: {total / 1e6:.2f} MB")


if __name__ == "__main__":
    main()
