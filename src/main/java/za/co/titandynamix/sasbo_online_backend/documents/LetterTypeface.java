package za.co.titandynamix.sasbo_online_backend.documents;

import org.apache.fontbox.ttf.CmapLookup;
import org.apache.fontbox.ttf.OS2WindowsMetricsTable;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The typeface the letters are set in, and the font that prints a letter it lacks.
 *
 * <h2>One setting swaps it</h2>
 * SASBO's house font is Avant Garde (Stephne, DOD-32), a commercial font. The owner decided on 6 Oct 2026 that the
 * letters use TeX Gyre Adventor, the free font with the same look, until a licensed Avant Garde is in hand -- behind
 * ONE setting, {@value #PROPERTY}: a Spring resource location of a folder holding {@code regular.ttf} and, if it has
 * them, {@code bold.ttf} and {@code italic.ttf} (each missing face is the regular one). A licensed font need not enter
 * the repository: {@code file:/etc/sasbo/fonts/avant-garde/} serves as well as the bundled {@value #BUNDLED}.
 * {@code config/fonts/README.md} has the procedure.
 *
 * <h2>A font that cannot be used is refused at start-up, saying why</h2>
 * Never discovered in a member's letter: CFF (PostScript) outlines, which PDFBox cannot embed; a licence flag that
 * forbids embedding or subsetting; and a missing letter of {@link #REQUIRED} -- every accented letter of Latin-1
 * (é, ë, ô, ñ...), so that the accents most names carry never print in a second font.
 *
 * <h2>A letter the typeface lacks prints from DejaVu Sans</h2>
 * Glyph by glyph ({@value #FALLBACK}): the Tshivenda ḓ, for one, which Adventor does not have. That font is not part of
 * the setting. A character neither has refuses the letter ({@link UnprintableCharacterException}) -- never a '?' in
 * a member's name (register A68).
 *
 * <h2>No GSUB table is ever handed to PDFBox</h2>
 * PDFBox 3 runs its ligature pass on every line drawn in a font that has one: 500 letters took 50 seconds instead of
 * five, and a ligature can lose its text when the PDF is read back. The letters need no ligatures, so the table is
 * left out of the bytes as they are loaded -- for any font the setting names, not only the bundled one.
 */
public final class LetterTypeface {

    /** The one setting: where the typeface's folder is. */
    public static final String PROPERTY = "sasbo.letters.font";

    /** TeX Gyre Adventor as TrueType outlines (the owner's decision, 6 Oct 2026). */
    public static final String BUNDLED = "classpath:fonts/letters/adventor/";

    /** DejaVu Sans, for the glyphs the typeface lacks. */
    static final String FALLBACK = "classpath:fonts/letters/dejavu/";

    /**
     * What the typeface must print itself: printable ASCII, every letter of Latin-1, and the punctuation the letters
     * use (curly quotes, dashes, the bullet, the middle dot).
     */
    static final String REQUIRED = IntStream.concat(
                    IntStream.rangeClosed(0x20, 0x7E),
                    IntStream.rangeClosed(0xC0, 0xFF).filter(cp -> cp != 0xD7 && cp != 0xF7))
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
            + "·–—‘’“”•";

    /** The faces a letter uses. */
    public enum Face {
        REGULAR("regular.ttf"),
        BOLD("bold.ttf"),
        ITALIC("italic.ttf");

        private final String file;

        Face(String file) {
            this.file = file;
        }
    }

    private static final int TRUETYPE = 0x00010000;
    private static final int TAG_TRUE = tag("true");
    private static final int TAG_OTTO = tag("OTTO");
    private static final int TAG_TTCF = tag("ttcf");
    private static final int TAG_GSUB = tag("GSUB");

    private final String location;
    private final String family;
    private final Map<Face, byte[]> faces;
    private final byte[] fallback;

    private LetterTypeface(String location, String family, Map<Face, byte[]> faces, byte[] fallback) {
        this.location = location;
        this.family = family;
        this.faces = faces;
        this.fallback = fallback;
    }

    /** The typeface in the folder at {@code location} -- refused, saying why, when it cannot be used. */
    public static LetterTypeface load(ResourceLoader loader, String location) {
        String folder = location.endsWith("/") ? location : location + "/";
        Map<Face, byte[]> faces = new EnumMap<>(Face.class);
        byte[] regular = usable(folder, Face.REGULAR.file, read(loader, folder + Face.REGULAR.file, true), true);
        faces.put(Face.REGULAR, regular);
        for (Face face : List.of(Face.BOLD, Face.ITALIC)) {
            byte[] bytes = read(loader, folder + face.file, false);
            // A face the folder lacks is the regular one -- the same bytes, so a batch parses them once.
            faces.put(face, bytes == null ? regular : usable(folder, face.file, bytes, true));
        }
        byte[] fallback = usable(FALLBACK, Face.REGULAR.file, read(loader, FALLBACK + Face.REGULAR.file, true), false);
        return new LetterTypeface(folder, familyOf(regular, Face.REGULAR.file), faces, fallback);
    }

    /** The bundled typeface, read once: what a letter is set in when nothing says otherwise. */
    public static LetterTypeface bundled() {
        return Bundled.INSTANCE;
    }

    private static final class Bundled {
        private static final LetterTypeface INSTANCE = load(new DefaultResourceLoader(), BUNDLED);
    }

    /** The family's name, as the font file gives it -- {@code Adventor TT} for the bundled one. */
    public String family() {
        return family;
    }

    /** Where it was read from. */
    public String location() {
        return location;
    }

    /**
     * The faces parsed for one batch of letters. PDFBox's parsed fonts are not safe across threads, so each render
     * opens its own; a batch opens once and embeds from the same parse into every letter.
     */
    Opened open() {
        return new Opened(this);
    }

    /** The parsed faces of one batch, closed when the batch's documents are saved. */
    static final class Opened implements AutoCloseable {

        private final Map<Face, TrueTypeFont> faces = new EnumMap<>(Face.class);
        private final Map<TrueTypeFont, CmapLookup> cmaps = new IdentityHashMap<>();
        private final List<TrueTypeFont> parsed = new ArrayList<>();
        private final byte[] fallbackBytes;
        private TrueTypeFont fallback;

        private Opened(LetterTypeface typeface) {
            Map<byte[], TrueTypeFont> byBytes = new IdentityHashMap<>();
            for (Face face : Face.values()) {
                faces.put(face, byBytes.computeIfAbsent(typeface.faces.get(face), this::parse));
            }
            this.fallbackBytes = typeface.fallback;
        }

        TrueTypeFont face(Face face) {
            return faces.get(face);
        }

        /** The fallback, parsed the first time a letter needs it. */
        TrueTypeFont fallback() {
            if (fallback == null) {
                fallback = parse(fallbackBytes);
            }
            return fallback;
        }

        /** Whether {@code font} has a glyph for {@code codePoint}. */
        boolean prints(TrueTypeFont font, int codePoint) {
            return cmaps.get(font).getGlyphId(codePoint) != 0;
        }

        private TrueTypeFont parse(byte[] bytes) {
            try {
                TrueTypeFont font = new TTFParser().parse(new RandomAccessReadBuffer(bytes));
                cmaps.put(font, font.getUnicodeCmapLookup());
                parsed.add(font);
                return font;
            } catch (IOException e) {
                throw new UncheckedIOException("A letter font could not be read", e);
            }
        }

        @Override
        public void close() {
            for (TrueTypeFont font : parsed) {
                try {
                    font.close();
                } catch (IOException ignored) {
                    // Closing a font read from memory releases nothing that matters.
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------

    private static byte[] read(ResourceLoader loader, String location, boolean required) {
        Resource resource = loader.getResource(location);
        if (!resource.exists()) {
            if (required) {
                throw new IllegalStateException("The letters' typeface needs " + location + ", which is not there ("
                        + PROPERTY + ")");
            }
            return null;
        }
        try (InputStream stream = resource.getInputStream()) {
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("The letters' typeface could not read " + location, e);
        }
    }

    /**
     * The face's bytes as a letter uses them -- its GSUB table left out -- or a refusal saying why it cannot be used:
     * TrueType outlines, embeddable and subsettable, and, for the typeface (not the fallback), every character of
     * {@link #REQUIRED}.
     */
    private static byte[] usable(String folder, String file, byte[] bytes, boolean mustCover) {
        String where = folder + file;
        int version = bytes.length < 12 ? 0 : ByteBuffer.wrap(bytes).getInt(0);
        if (version == TAG_OTTO) {
            throw new IllegalStateException(where + " has CFF (PostScript) outlines, which PDFBox cannot embed in a "
                    + "letter: convert it to TrueType outlines first (config/fonts/README.md)");
        }
        if (version == TAG_TTCF) {
            throw new IllegalStateException(where + " is a font collection; the letters need one font per file");
        }
        if (version != TRUETYPE && version != TAG_TRUE) {
            throw new IllegalStateException(where + " is not a TrueType font");
        }
        byte[] used = withoutGsub(bytes);
        try (TrueTypeFont font = new TTFParser().parse(new RandomAccessReadBuffer(used))) {
            OS2WindowsMetricsTable os2 = font.getOS2Windows();
            int fsType = os2 == null ? 0 : os2.getFsType();
            if ((fsType & 0x000F) == OS2WindowsMetricsTable.FSTYPE_RESTRICTED
                    || (fsType & OS2WindowsMetricsTable.FSTYPE_BITMAP_ONLY) != 0) {
                throw new IllegalStateException(where + "'s licence flag forbids embedding it in a document");
            }
            if ((fsType & OS2WindowsMetricsTable.FSTYPE_NO_SUBSETTING) != 0) {
                throw new IllegalStateException(where + "'s licence flag forbids embedding part of it, and a letter "
                        + "embeds only the glyphs it prints");
            }
            if (mustCover) {
                CmapLookup cmap = font.getUnicodeCmapLookup();
                List<Integer> missing = REQUIRED.codePoints().filter(cp -> cmap.getGlyphId(cp) == 0).boxed().toList();
                if (!missing.isEmpty()) {
                    throw new IllegalStateException(where + " cannot print " + missing.stream()
                            .map(cp -> String.format("U+%04X %s", cp, new String(Character.toChars(cp))))
                            .collect(Collectors.joining(", "))
                            + " -- the letters' typeface must print every accented letter of Latin-1 itself");
                }
            }
            return used;
        } catch (IOException e) {
            throw new IllegalStateException(where + " could not be read as a font", e);
        }
    }

    /** The family's name the font gives, or its file's name when it gives none. */
    private static String familyOf(byte[] bytes, String file) {
        try (TrueTypeFont font = new TTFParser().parse(new RandomAccessReadBuffer(bytes))) {
            String name = font.getNaming() == null ? null : font.getNaming().getFontFamily();
            return name == null || name.isBlank() ? file : name;
        } catch (IOException e) {
            throw new IllegalStateException(file + " could not be read as a font", e);
        }
    }

    /**
     * {@code font} as it is used: its GSUB table left out, everything else byte for byte. A TrueType file is a
     * directory of tables; this writes the directory again without the one entry, and the tables after it.
     */
    static byte[] withoutGsub(byte[] font) {
        ByteBuffer in = ByteBuffer.wrap(font);
        int tables = Short.toUnsignedInt(in.getShort(4));
        List<int[]> kept = new ArrayList<>();
        for (int i = 0; i < tables; i++) {
            int record = 12 + 16 * i;
            int[] entry = {in.getInt(record), in.getInt(record + 4), in.getInt(record + 8), in.getInt(record + 12)};
            if (entry[0] != TAG_GSUB) {
                kept.add(entry);
            }
        }
        if (kept.size() == tables) {
            return font;
        }
        int count = kept.size();
        int power = Integer.highestOneBit(count);
        ByteArrayOutputStream out = new ByteArrayOutputStream(font.length);
        ByteBuffer header = ByteBuffer.allocate(12 + 16 * count);
        header.putInt(in.getInt(0)).putShort((short) count).putShort((short) (power * 16))
                .putShort((short) Integer.numberOfTrailingZeros(power)).putShort((short) (count * 16 - power * 16));
        int offset = header.capacity();
        for (int[] entry : kept) {
            header.putInt(entry[0]).putInt(entry[1]).putInt(offset).putInt(entry[3]);
            offset += (entry[3] + 3) & ~3;
        }
        out.writeBytes(header.array());
        for (int[] entry : kept) {
            out.write(font, entry[2], entry[3]);
            out.writeBytes(new byte[((entry[3] + 3) & ~3) - entry[3]]);
        }
        return out.toByteArray();
    }

    private static int tag(String tag) {
        return ByteBuffer.wrap(tag.getBytes(StandardCharsets.US_ASCII)).getInt();
    }
}
