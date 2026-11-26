#!/usr/bin/env python3
"""Generate Ongero's icons and wordmark from the source logo, in the brand palette.

The logo as supplied is neon purple (#9D30F2) and near-black purple (#3A0146) on a
solid BLACK card. The app is not that: its design system is warm paper (#F7F4F0) with
a muted violet accent (#5E54A4), chosen so the product reads as calm. Dropping the
source art in unchanged would have put a neon badge on a deliberately quiet app, and
the wordmark — dark purple on black — is close to unreadable at any size.

So the mark is recoloured onto the design tokens rather than the palette being bent to
the mark, and the background is dropped so the same art works on paper and at night.

This script exists so the assets are reproducible. Regenerating them should be editing
two colours here and re-running, not opening an image editor and hoping.

    python3 tools/generate_brand_assets.py path/to/source-logo.(webp|png)

Outputs:
    app/src/main/res/drawable/ongero_logo.png        square heart, transparent
    app/src/main/res/mipmap-*/ic_launcher.png        legacy launcher icons
    app/src/main/res/mipmap-*/ic_launcher_round.png  legacy round icons
    website/app/icon.png                             favicon (Next.js app router)
    website/app/apple-icon.png                       iOS home-screen icon
    ongero_logo.png                                  full wordmark, on paper
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]

# --- the design tokens, from app/src/main/java/.../ui/redesign/theme/Theme.kt --------
PAPER = (247, 244, 240)          # Paper      #F7F4F0
ACCENT = (94, 84, 164)           # Violet     #5E54A4
ACCENT_DEEP = (207, 197, 238)    # VioletDeep #CFC5EE
INK = (34, 31, 46)               # Ink        #221F2E

# --- the colours actually in the source art -----------------------------------------
SOURCE_DARK = (58, 1, 70)        # #3A0146, the shadowed half and the wordmark
SOURCE_BRIGHT = (157, 48, 242)   # #9D30F2, the lit half

# The lit half becomes the ACCENT and the shadowed half the light violet, not the other
# way round: the lit half is the larger shape, and the darker colour on the smaller
# shape keeps the two faces readable at 48px.
RECOLOUR = [(SOURCE_BRIGHT, ACCENT), (SOURCE_DARK, ACCENT_DEEP)]

LAUNCHER_DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def nearest(pixel, palette):
    """Which source colour is this closest to? The art is anti-aliased, so an exact
    match covers only the flat interiors and leaves every edge black."""
    r, g, b = pixel[:3]
    best, best_distance = None, None
    for source, target in palette:
        distance = (r - source[0]) ** 2 + (g - source[1]) ** 2 + (b - source[2]) ** 2
        if best_distance is None or distance < best_distance:
            best, best_distance = target, distance
    return best


def recolour(image: Image.Image) -> Image.Image:
    """Map the art onto the brand palette, with the black card becoming transparency.

    Luminance drives the alpha: the source is light-on-black, so how far a pixel is
    from black is exactly how opaque it should be. That keeps the anti-aliased edges
    smooth instead of producing a hard, jagged cut-out.
    """
    image = image.convert("RGB")
    width, height = image.size
    out = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    source, target = image.load(), out.load()

    for y in range(height):
        for x in range(width):
            r, g, b = source[x, y]
            luminance = 0.299 * r + 0.587 * g + 0.114 * b
            if luminance < 4:
                continue  # the card
            # Full opacity well before pure white, so interiors are solid.
            alpha = min(255, int(luminance / 28 * 255))
            target[x, y] = (*nearest((r, g, b), RECOLOUR), alpha)
    return out


def square(image: Image.Image, size: int, padding: float = 0.0) -> Image.Image:
    """Centre the art on a transparent square, leaving `padding` of it as margin."""
    inner = int(size * (1 - 2 * padding))
    art = image.copy()
    art.thumbnail((inner, inner), Image.LANCZOS)
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    canvas.paste(art, ((size - art.width) // 2, (size - art.height) // 2), art)
    return canvas


def on_paper(image: Image.Image, colour=PAPER) -> Image.Image:
    plate = Image.new("RGBA", image.size, (*colour, 255))
    plate.alpha_composite(image)
    return plate


def circle(image: Image.Image, colour=PAPER) -> Image.Image:
    """A round legacy icon: the art on a paper disc, everything outside it cut away."""
    size = image.size[0]
    plate = Image.new("RGBA", image.size, (0, 0, 0, 0))
    ImageDraw.Draw(plate).ellipse((0, 0, size - 1, size - 1), fill=(*colour, 255))
    plate.alpha_composite(image)
    mask = Image.new("L", image.size, 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
    plate.putalpha(mask)
    return plate


def heart_bounds(art: Image.Image) -> tuple[int, int, int, int]:
    """Where the heart ends and the wordmark begins.

    Found rather than hard-coded. Splitting at a third of the width put a sliver of
    the "O" beside the heart, because the recolour keeps anti-aliasing far fainter
    than eyeballing the source suggests — the letter starts earlier than it looks.

    So: take the first run of fully empty columns after the mark starts, and treat
    everything before it as the heart. A gap that survives at this threshold is a real
    gap and not a thin part of a glyph.
    """
    width, height = art.size
    alpha = art.split()[3].load()

    def column_is_empty(x: int) -> bool:
        return all(alpha[x, y] == 0 for y in range(height))

    first = next(x for x in range(width) if not column_is_empty(x))

    gap_start = None
    min_gap = max(8, width // 100)
    for x in range(first, width):
        if column_is_empty(x):
            if gap_start is None:
                gap_start = x
            elif x - gap_start >= min_gap:
                break
        else:
            gap_start = None
    else:
        raise SystemExit("No gap found between the heart and the wordmark")

    box = art.crop((0, 0, gap_start, height)).getbbox()
    if box is None:
        raise SystemExit("The heart crop is empty")
    return box


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    source = Image.open(sys.argv[1])
    art = recolour(source)

    full = art.crop(art.getbbox())
    heart = art.crop(heart_bounds(art))

    written = []

    def save(relative: str, image: Image.Image):
        path = ROOT / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        image.save(path)
        written.append(f"{relative}  {image.size[0]}x{image.size[1]}")

    # The adaptive-icon foreground. ic_launcher_foreground.xml centres this at 84dp
    # inside the 108dp canvas, which already supplies the safe zone, so no padding.
    save("app/src/main/res/drawable/ongero_logo.png", square(heart, 432))

    # Legacy launchers (pre-API 26) get a flattened square and a disc.
    for density, size in LAUNCHER_DENSITIES.items():
        icon = square(heart, size, padding=0.12)
        save(f"app/src/main/res/mipmap-{density}/ic_launcher.png", on_paper(icon))
        save(f"app/src/main/res/mipmap-{density}/ic_launcher_round.png", circle(icon))

    # The website. Next.js's app router serves app/icon.png as the favicon and
    # app/apple-icon.png as the home-screen icon, both ahead of any favicon.ico.
    save("website/app/icon.png", on_paper(square(heart, 64, padding=0.06)))
    save("website/app/apple-icon.png", on_paper(square(heart, 180, padding=0.10)))

    # The full lockup, for READMEs and anywhere the name belongs beside the mark.
    save("ongero_logo.png", on_paper(full))

    for line in written:
        print(f"  wrote {line}")
    print(f"\n{len(written)} file(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
