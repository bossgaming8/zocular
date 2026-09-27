"""Builds the GUI sprites and textures under src/main/resources/assets/zocular.

Sprites are white on transparent so the game can tint them. Run from the repo root:
    python tools/gen_gui_assets.py
"""
import json
import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/zocular"
SPRITES = ASSETS / "textures/gui/sprites"
TEXTURES = ASSETS / "textures/gui"

# '#' = opaque, '+' = half alpha, anything else = transparent
ICONS = {
    "icon/zoom": [
        "...#####....",
        "..##...##...",
        ".##.....##..",
        ".#.......#..",
        ".#.......#..",
        ".#.......#..",
        ".##.....##..",
        "..##...###..",
        "...#######..",
        "........###.",
        ".........###",
        "..........#.",
    ],
    "icon/cinematic": [
        "............",
        ".##..##..##.",
        "##..##..##..",
        "############",
        "............",
        "############",
        "############",
        "############",
        "############",
        "############",
        "############",
        "............",
    ],
    "icon/freecam": [
        "............",
        ".###..###...",
        "#+++##+++#..",
        "#+++##+++#..",
        ".###..###...",
        "#########...",
        "#########..#",
        "#########.##",
        "#########.##",
        "#########..#",
        "#########...",
        "............",
    ],
    "icon/path": [
        "..........##",
        "..........##",
        ".........#..",
        "........#...",
        "......##....",
        ".....###....",
        ".....##.....",
        "....#.......",
        "...#........",
        "..#.........",
        "##..........",
        "##..........",
    ],
    "icon/camera": [
        "....####....",
        "...######...",
        "...######...",
        "...######...",
        "....####....",
        "............",
        "..########..",
        ".##########.",
        ".##########.",
        ".##########.",
        ".##########.",
        "............",
    ],
    "icon/interface": [
        "############",
        "#..........#",
        "#.###......#",
        "#..........#",
        "#..........#",
        "#......###.#",
        "#..........#",
        "############",
        "....####....",
        "...######...",
        "............",
        "............",
    ],
    "icon/controls": [
        "............",
        "............",
        "############",
        "#.#.#.#.#.##",
        "############",
        "##.#.#.#.#.#",
        "############",
        "#.#......#.#",
        "############",
        "............",
        "............",
        "............",
    ],
    "icon/reset": [
        "..#####.#",
        ".#.....##",
        "#.....###",
        "#........",
        "#........",
        "#........",
        "#.......#",
        ".#.....#.",
        "..#####..",
    ],
    "icon/close": [
        "##....##",
        "###..###",
        ".######.",
        "..####..",
        "..####..",
        ".######.",
        "###..###",
        "##....##",
    ],
    "icon/heart": [
        ".##...##.",
        "####.####",
        "#########",
        "#########",
        ".#######.",
        "..#####..",
        "...###...",
        "....#....",
        ".........",
    ],
    "icon/bug": [
        "#..###..#",
        ".#######.",
        "..#####..",
        "#########",
        "..#####..",
        "#########",
        "..#####..",
        ".#.###.#.",
        "#.......#",
    ],
    "icon/discord": [
        "..##...##..",
        ".#########.",
        "###########",
        "##..###..##",
        "##..###..##",
        "###########",
        "##.#####.##",
        ".#.......#.",
        ".........  ",
    ],
    "rounded": [
        "..####..",
        ".######.",
        "########",
        "########",
        "########",
        "########",
        ".######.",
        "..####..",
    ],
    "rounded_outline": [
        "..####..",
        ".#....#.",
        "#......#",
        "#......#",
        "#......#",
        "#......#",
        ".#....#.",
        "..####..",
    ],
    "toggle": [
        "...##############...",
        ".##################.",
        ".##################.",
        "####################",
        "####################",
        "####################",
        "####################",
        ".##################.",
        ".##################.",
        "...##############...",
    ],
    "knob": [
        "..####..",
        ".######.",
        "########",
        "########",
        "########",
        "########",
        ".######.",
        "..####..",
    ],
    "track": [
        ".######.",
        "########",
        "########",
        ".######.",
    ],
}

NINE_SLICE = {
    "rounded": 3,
    "rounded_outline": 3,
    "track": {"left": 2, "top": 1, "right": 2, "bottom": 1},
}


def from_rows(rows):
    width = max(len(r) for r in rows)
    image = Image.new("RGBA", (width, len(rows)), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == "#":
                image.putpixel((x, y), (255, 255, 255, 255))
            elif ch == "+":
                image.putpixel((x, y), (255, 255, 255, 110))
    return image


def write_sprite(name, image, border=None):
    path = SPRITES / f"{name}.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)
    if border is not None:
        meta = {"gui": {"scaling": {"type": "nine_slice", "width": image.width,
                                    "height": image.height, "border": border}}}
        path.with_name(path.name + ".mcmeta").write_text(json.dumps(meta, indent=2) + "\n")


def shadow():
    # Soft drop shadow, stretched around panels as a nine-slice.
    size, border = 24, 10
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for y in range(size):
        for x in range(size):
            dx = max(border - x - 0.5, x + 0.5 - (size - border), 0)
            dy = max(border - y - 0.5, y + 0.5 - (size - border), 0)
            d = math.hypot(dx, dy) / border
            alpha = max(0.0, 1.0 - d) ** 2
            image.putpixel((x, y), (0, 0, 0, round(alpha * 255)))
    return image, border


def vignette():
    size = 256
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for y in range(size):
        for x in range(size):
            nx = (x + 0.5) / size * 2 - 1
            ny = (y + 0.5) / size * 2 - 1
            d = math.sqrt(nx * nx * 0.8 + ny * ny)
            alpha = min(1.0, max(0.0, (d - 0.45) / 0.75)) ** 1.6
            image.putpixel((x, y), (0, 0, 0, round(alpha * 255)))
    return image


def main():
    for name, rows in ICONS.items():
        write_sprite(name, from_rows(rows), NINE_SLICE.get(name))

    image, border = shadow()
    write_sprite("shadow", image, border)

    TEXTURES.mkdir(parents=True, exist_ok=True)
    vignette().save(TEXTURES / "vignette.png")
    (TEXTURES / "vignette.png.mcmeta").write_text('{"texture": {"blur": true, "clamp": true}}\n')

    logo = Image.open(ROOT / "docs/assets/zocular-logo.png").convert("RGBA")
    width = 512
    logo = logo.resize((width, round(logo.height * width / logo.width)), Image.LANCZOS)
    logo.save(TEXTURES / "logo.png")
    (TEXTURES / "logo.png.mcmeta").write_text('{"texture": {"blur": true, "clamp": true}}\n')
    print("logo", logo.size)


if __name__ == "__main__":
    main()
