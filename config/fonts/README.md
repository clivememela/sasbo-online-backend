# The letters' typeface, and how to swap it

The debit order letters are set in **Adventor TT** (TeX Gyre Adventor, free, the look of Avant Garde), embedded in
every PDF. SASBO's house font is Avant Garde (Stephne, DOD-32), a commercial font. The owner decided on 6 Oct 2026
that the letters use the free look-alike until a licensed Avant Garde is in hand, behind **one setting**.

## The one setting

`sasbo.letters.font` (environment `SASBO_LETTERS_FONT`), a Spring resource location of a **folder**:

| File in the folder | Needed | Used for |
| :-- | :-- | :-- |
| `regular.ttf` | yes | everything not below |
| `bold.ttf` | no (falls back to regular) | the subject line, headings, the signing position |
| `italic.ttf` | no (falls back to regular) | the letterhead's address block |

The default is `classpath:fonts/letters/adventor/`. A licensed font need not enter the repository: point the
setting at a folder on the host, for example `file:/etc/sasbo/fonts/avant-garde/`.

## Swapping in a licensed Avant Garde

1. **Check the licence first.** It must allow embedding the font in PDFs sent to members, and, if you have only the
   OpenType (`.otf`) files, converting them (step 2). Many foundries sell a TrueType (`.ttf`) edition, which needs
   no conversion.
2. **TrueType outlines only.** PDFBox cannot embed CFF (`.otf`) outlines. Convert each face:
   `python config/fonts/otf_to_ttf.py AvantGarde-Book.otf regular.ttf` (and `bold.ttf`, `italic.ttf`).
3. Put the files in a folder named as above, set `sasbo.letters.font` to it, and restart. The start-up refuses a
   font that cannot be used, and says why: CFF outlines, a licence flag that forbids embedding, or a missing accented
   letter of Latin-1 (é, ë, ô, ñ…: the accents most names carry must print in the house font itself).
4. Look at a preview: `GET /api/acb/letters/specimen/RE_DEBIT` (needs `DOD_PREPARE`).

A letter the typeface cannot print (the Tshivenda ḓ, for one) prints from DejaVu Sans, glyph by glyph
(`src/main/resources/fonts/letters/dejavu/`). That fallback is not part of the setting.
