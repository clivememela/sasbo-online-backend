Adventor TT -- the typeface the debit order letters are set in, a stand-in for Avant Garde
=====================================================================================

WHY THIS FONT
SASBO's house font is Avant Garde (Stephne, DOD-32: "Avantgarde - is what is currently used"), a commercial font
that needs a licence and must print accented names. The owner decided on 6 Oct 2026 that the letters use
TeX Gyre Adventor -- a free font with the same look -- until a licensed Avant Garde is in hand, behind ONE setting
(sasbo.letters.font) so the swap is a folder and a property. How to swap: config/fonts/README.md.

WHAT IT IS
TeX Gyre Adventor 2.501 by the GUST e-foundry, from CTAN (https://ctan.org/pkg/tex-gyre), with two changes made by
config/fonts/otf_to_ttf.py, because PDFBox embeds only TrueType outlines:
  * its CFF (PostScript) outlines are converted to TrueType outlines (fontTools cu2qu, maximum error 1 font unit
    at 1000 units per em);
  * its GSUB and FFTM tables are removed (PDFBox's ligature pass made 500 letters take 50 s; FFTM is a timestamp);
and, as the GUST Font License requests of derived works, it is renamed "Adventor TT". Nothing else is changed.

LICENCE
GUST Font License (GUST-FONT-LICENSE.txt beside this file): the LaTeX Project Public License 1.3c or later. Embedding
it in a document, and distributing it, are permitted.

PROVENANCE (verified 2026-10-07)
  Source archive  tex-gyre.tar.xz from TeX Live (CTAN mirror mirror.ufs.ac.za), revision 68624
                  SHA-512 5422f349d15233e6f49576fe3a65f8afb5cae6a3c51e5ad7cb56617d7d064e9cf574f233f789c029c04bbfc24e
                  5948c115b2d33569e881373d68615ac709153f -- equal to TeX Live's texlive.tlpdb containerchecksum, whose
                  own SHA-512 matched texlive.tlpdb.sha512
  Originals       (also downloaded directly from CTAN's fonts/tex-gyre/opentype/, identical)
                  texgyreadventor-regular.otf  ee265c1c9728be9491c7f0d7fcdcb2f97bc35040a6dc1f9815265cee333e155b
                  texgyreadventor-bold.otf     e373ae29c808c6d9149e3add46564f9acdb6ebfcd0ee311f6a55560226994720
                  texgyreadventor-italic.otf   1f21a5a07c207f70785dd6cbe4fcb2e0a3f60823b219efb01790a72d8a61b3cb
  Converted       by config/fonts/otf_to_ttf.py --family "Adventor TT" --style Regular|Bold|Italic
                  (fontTools 4.56.0, Python 3.11; two runs give identical bytes)
                  regular.ttf  d713ebccea7ec7d30a9696577eea49a4ddf2fba03b76af7191a8ba0dc6cff441
                  bold.ttf     124a60bacb4421c4cf8e38a538a161944d1fed629650b457efbbdd1100eb42b6
                  italic.ttf   6e52a14e249d206e18d4570b8a20d219f0b8dac08a9e91b7258a8b5812f12fec
  (SHA-256 unless marked.)

COVERAGE
1,199 characters: every Latin-1 and Latin Extended-A letter (Naudé, Zoë, Côté, Muñoz, Ångström). Not covered:
the Tshivenda letters with a dot below (ḓ, ḽ, ṋ, ṱ) and ŉ -- a letter prints those from DejaVu Sans
(../dejavu/), glyph by glyph.
