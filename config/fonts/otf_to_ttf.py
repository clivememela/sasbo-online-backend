"""Convert an OpenType font with CFF outlines to TrueType outlines that PDFBox can embed in the letters.

PDFBox 3 embeds only TrueType-outline fonts ("True Type fonts using CFF outlines are not supported"), and CTAN ships
TeX Gyre Adventor only as CFF OpenType. This is how src/main/resources/fonts/letters/adventor/ was made:

    python config/fonts/otf_to_ttf.py texgyreadventor-regular.otf regular.ttf --family "Adventor TT" --style Regular
    python config/fonts/otf_to_ttf.py texgyreadventor-bold.otf    bold.ttf    --family "Adventor TT" --style Bold
    python config/fonts/otf_to_ttf.py texgyreadventor-italic.otf  italic.ttf  --family "Adventor TT" --style Italic

What it changes: the CFF table becomes glyf/loca (cubic curves to quadratic, fontTools cu2qu, maximum error 1 font
unit), maxp and post are rebuilt for TrueType, and GSUB and FFTM are dropped -- PDFBox runs its ligature pass on
every line drawn in a font with a GSUB table (500 letters took 50 s instead of 5), and FFTM is FontForge's
timestamp. With --family the font is renamed, as the GUST Font License asks of derived works; without it the names
are kept. Glyph order, metrics, cmap and kerning are untouched.

Needs: Python 3.9+ and fontTools (pip install fonttools); made with fontTools 4.56.0.
"""
import argparse
import hashlib

from fontTools.pens.cu2quPen import Cu2QuPen
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.ttLib import TTFont, newTable

MAX_ERR = 1.0
DROPPED = ("VORG", "GSUB", "FFTM")


def quadratic_glyphs(glyph_set):
    glyphs = {}
    for name in glyph_set.keys():
        pen = TTGlyphPen(glyph_set)
        glyph_set[name].draw(Cu2QuPen(pen, MAX_ERR, reverse_direction=True))
        glyphs[name] = pen.glyph()
    return glyphs


def convert(font):
    if font.sfntVersion != "OTTO" or "CFF " not in font:
        raise SystemExit("not an OpenType font with CFF outlines -- nothing to convert")
    order = font.getGlyphOrder()
    font["loca"] = newTable("loca")
    font["glyf"] = glyf = newTable("glyf")
    glyf.glyphOrder = order
    glyf.glyphs = quadratic_glyphs(font.getGlyphSet())
    del font["CFF "]
    for tag in DROPPED:
        if tag in font:
            del font[tag]
    glyf.compile(font)
    hmtx = font["hmtx"]
    for name, glyph in glyf.glyphs.items():
        if hasattr(glyph, "xMin"):
            hmtx[name] = (hmtx[name][0], glyph.xMin)
    font["maxp"] = maxp = newTable("maxp")
    maxp.tableVersion = 0x00010000
    maxp.maxZones = 1
    for field in ("maxTwilightPoints", "maxStorage", "maxFunctionDefs", "maxInstructionDefs",
                  "maxStackElements", "maxSizeOfInstructions"):
        setattr(maxp, field, 0)
    maxp.maxComponentElements = max(
        (len(g.components) for g in glyf.glyphs.values() if hasattr(g, "components")), default=0)
    maxp.compile(font)
    post = font["post"]
    post.formatType = 2.0
    post.extraNames = []
    post.mapping = {}
    post.glyphOrder = order
    font["head"].glyphDataFormat = 0
    font.sfntVersion = "\000\001\000\000"


def rename(font, family, style):
    name = font["name"]
    original_full = name.getDebugName(4) or "the original font"
    original_version = name.getDebugName(5) or ""
    full = f"{family} {style}"
    postscript = f"{family.replace(' ', '')}-{style}"
    description = (f"{full} is {original_full} with its CFF outlines converted to TrueType outlines and its GSUB "
                   f"and FFTM tables removed, by config/fonts/otf_to_ttf.py, and renamed as its licence asks of "
                   f"derived works. Nothing else is changed.")
    for record in list(name.names):
        if record.nameID in (1, 2, 3, 4, 5, 6, 10, 16, 17, 18, 21, 22):
            name.removeNames(nameID=record.nameID)
    for platform in ((3, 1, 0x409), (1, 0, 0)):
        name.setName(family, 1, *platform)
        name.setName(style, 2, *platform)
        name.setName(f"{postscript} (converted to TrueType outlines)", 3, *platform)
        name.setName(full, 4, *platform)
        name.setName(f"{original_version}; converted to TrueType outlines".lstrip("; "), 5, *platform)
        name.setName(postscript, 6, *platform)
        name.setName(description, 10, *platform)


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("source", help="the .otf font with CFF outlines")
    parser.add_argument("target", help="the .ttf to write")
    parser.add_argument("--family", help="rename the font to this family (the GUST licence asks this of derived works)")
    parser.add_argument("--style", default="Regular", help="the style name written with --family")
    args = parser.parse_args()
    # recalcTimestamp=False keeps the original's head.modified, so the same input always gives the same bytes.
    font = TTFont(args.source, recalcTimestamp=False)
    convert(font)
    if args.family:
        rename(font, args.family, args.style)
    font.save(args.target)
    with open(args.target, "rb") as handle:
        data = handle.read()
    print(f"{args.target}: {len(data)} bytes, SHA-256 {hashlib.sha256(data).hexdigest()}")


if __name__ == "__main__":
    main()
