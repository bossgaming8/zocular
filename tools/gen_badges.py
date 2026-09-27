"""Draws the pixel-art link badges used on the Modrinth page into docs/assets/badges.

    python tools/gen_badges.py
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs/assets/badges"
SCALE = 4

GLYPHS = {
    "A": [".####.", "##..##", "##..##", "######", "##..##", "##..##", "##..##"],
    "B": ["#####.", "##..##", "##..##", "#####.", "##..##", "##..##", "#####."],
    "C": [".#####", "##....", "##....", "##....", "##....", "##....", ".#####"],
    "D": ["#####.", "##..##", "##..##", "##..##", "##..##", "##..##", "#####."],
    "E": ["######", "##....", "##....", "#####.", "##....", "##....", "######"],
    "G": [".#####", "##....", "##....", "##.###", "##..##", "##..##", ".#####"],
    "H": ["##..##", "##..##", "##..##", "######", "##..##", "##..##", "##..##"],
    "I": ["######", "..##..", "..##..", "..##..", "..##..", "..##..", "######"],
    "L": ["##....", "##....", "##....", "##....", "##....", "##....", "######"],
    "N": ["##..##", "###.##", "######", "##.###", "##..##", "##..##", "##..##"],
    "O": [".####.", "##..##", "##..##", "##..##", "##..##", "##..##", ".####."],
    "P": ["#####.", "##..##", "##..##", "#####.", "##....", "##....", "##...."],
    "R": ["#####.", "##..##", "##..##", "#####.", "##.##.", "##..##", "##..##"],
    "S": [".#####", "##....", "##....", ".####.", "....##", "....##", "#####."],
    "T": ["######", "..##..", "..##..", "..##..", "..##..", "..##..", "..##.."],
    "U": ["##..##", "##..##", "##..##", "##..##", "##..##", "##..##", ".####."],
    "Y": ["##..##", "##..##", "##..##", ".####.", "..##..", "..##..", "..##.."],
    " ": ["...", "...", "...", "...", "...", "...", "..."],
}

ICONS = {
    "discord": ([
        "..##...##..",
        ".#########.",
        "###########",
        "##..###..##",
        "##..###..##",
        "###########",
        "##.#####.##",
        ".#.......#.",
    ], (88, 101, 242)),
    "issues": ([
        "..#####..",
        ".##...##.",
        "##.....##",
        "#...#...#",
        "#..###..#",
        "#...#...#",
        "##.....##",
        ".##...##.",
        "..#####..",
    ], (63, 185, 80)),
    "paypal": ([
        "######..",
        "##...##.",
        "##...##.",
        "######..",
        "##......",
        "##......",
        "##......",
    ], (0, 156, 222)),
    "source": ([
        "..#.....#..",
        ".##.....##.",
        "##...#...##",
        "#...#.....#",
        "##.#.....##",
        ".##.....##.",
        "..#.....#..",
    ], (242, 169, 59)),
}

BADGES = {
    "discord": "DISCORD",
    "issues": "ISSUES",
    "paypal": "PAYPAL",
    "source": "SOURCE",
}

OUTLINE = (20, 14, 10, 255)
EXTRUDE = (84, 52, 20, 255)
TEXT_TOP = (255, 250, 240)
TEXT_BOTTOM = (255, 214, 150)


def mask_for(text, icon):
    """Returns a {(x, y): colour} map in font pixels."""
    pixels = {}
    x = 0
    rows, colour = icon
    top = (7 - len(rows)) // 2
    for y, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch == "#":
                pixels[(x + i, top + y)] = colour
    x += max(len(r) for r in rows) + 3

    for ch in text:
        glyph = GLYPHS[ch]
        for y, row in enumerate(glyph):
            for i, c in enumerate(row):
                if c == "#":
                    t = y / 6
                    pixels[(x + i, y)] = tuple(round(a + (b - a) * t) for a, b in zip(TEXT_TOP, TEXT_BOTTOM))
        x += len(glyph[0]) + 1
    return pixels


def render(pixels):
    # Every badge spans rows -1..8 (tallest icon plus extrusion) so they line up side by side.
    top, bottom = -1, 8
    pad = 1
    width = max(p[0] for p in pixels) + 1 + pad * 2
    height = bottom - top + 1 + pad * 2
    image = Image.new("RGBA", (width * SCALE, height * SCALE), (0, 0, 0, 0))

    def put(x, y, colour):
        for dy in range(SCALE):
            for dx in range(SCALE):
                image.putpixel(((x + pad) * SCALE + dx, (y - top + pad) * SCALE + dy), colour)

    filled = set(pixels)
    extruded = {(x, y + 1) for x, y in filled}
    body = filled | extruded
    outline = {(x + dx, y + dy) for x, y in body for dx in (-1, 0, 1) for dy in (-1, 0, 1)} - body

    for x, y in outline:
        put(x, y, OUTLINE)
    for x, y in extruded - filled:
        put(x, y, EXTRUDE)
    for (x, y), colour in pixels.items():
        put(x, y, colour + (255,) if len(colour) == 3 else colour)
    return image


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, text in BADGES.items():
        image = render(mask_for(text, ICONS[name]))
        image.save(OUT / f"{name}.png")
        print(name, image.size)


if __name__ == "__main__":
    main()
