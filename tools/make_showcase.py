"""Turns the frames recorded by ShowcaseRecorder into the media used on the Modrinth page.

    ./gradlew runClientGameTest -Pshowcase
    python tools/make_showcase.py
"""
import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
SHOTS = ROOT / "build/run/clientGameTest/screenshots"
OUT = ROOT / "docs/assets/showcase"
GALLERY = ROOT / "build/gallery"

CLIP_SIZE = (720, 405)
CLIP_FPS = 30
CLIP_SECONDS = 8
RECORD_TICKS = 40 * 20


def clip():
    """The recorder names each frame after the game tick it shows while the path plays linearly and slowly.
    Picking the frame nearest to each point of an eased, evenly timed schedule gives a smooth clip."""
    recorded = []
    for f in (SHOTS / "clip").glob("frame-*.png"):
        tick = int(f.stem.split("-t")[1])
        recorded.append((tick / RECORD_TICKS, f))
    if not recorded:
        raise SystemExit("no clip frames; run the showcase recorder first")
    recorded.sort()

    count = CLIP_FPS * CLIP_SECONDS
    chosen = []
    for k in range(count):
        target = 0.5 - 0.5 * math.cos(math.pi * k / (count - 1))
        chosen.append(min(recorded, key=lambda frame: abs(frame[0] - target))[1])

    cache = {}
    images = []
    for f in chosen:
        if f not in cache:
            cache[f] = Image.open(f).convert("RGB").resize(CLIP_SIZE, Image.LANCZOS)
        images.append(cache[f])
    out = OUT / "camera-path.webp"
    images[0].save(out, save_all=True, append_images=images[1:], duration=round(1000 / CLIP_FPS),
                   loop=0, quality=58, method=6)
    print(out.name, len(recorded), "recorded,", len(set(chosen)), "used,", out.stat().st_size // 1024, "KB")


# Stills that the README and the Modrinth description embed; everything else only goes to the gallery.
WEB_STILLS = {"settings", "cinematic-1"}


def stills():
    for shot in sorted(SHOTS.glob("showcase-*.png")):
        name = shot.stem.removeprefix("showcase-")
        image = Image.open(shot).convert("RGB")
        image.save(GALLERY / f"{name}.png")
        if name in WEB_STILLS:
            web = image.resize((1280, round(image.height * 1280 / image.width)), Image.LANCZOS)
            web.save(OUT / f"{name}.webp", quality=86, method=6)
        print(name)


def zoom_pair():
    """Side-by-side of the same view at 1x and zoomed in, for the gallery."""
    left = Image.open(SHOTS / "showcase-zoom-1x.png").convert("RGB")
    right = Image.open(SHOTS / "showcase-zoom-8x.png").convert("RGB")
    width, height = left.width // 2, left.height // 2
    gap = 8
    pair = Image.new("RGB", (width * 2 + gap, height), (20, 18, 23))
    pair.paste(left.resize((width, height), Image.LANCZOS), (0, 0))
    pair.paste(right.resize((width, height), Image.LANCZOS), (width + gap, 0))
    pair.save(GALLERY / "zoom-compare.png")
    pair.save(OUT / "zoom-compare.webp", quality=86, method=6)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    GALLERY.mkdir(parents=True, exist_ok=True)
    stills()
    zoom_pair()
    clip()


if __name__ == "__main__":
    main()
