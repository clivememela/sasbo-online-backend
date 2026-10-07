package za.co.titandynamix.sasbo_online_backend.documents;

import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TTFSubsetter;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.fontbox.ttf.model.GsubData;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.DescriptiveResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The letters' typeface behind its one setting: TeX Gyre Adventor by default (the owner's decision, 6 Oct 2026), any
 * other font by naming it -- and refused at start-up, saying why, when it cannot be used.
 */
@DisplayName("LetterTypeface -- the letters' typeface, behind one setting")
class LetterTypefaceTest {

    private static final String DEJAVU = "classpath:fonts/letters/dejavu/";

    @Test
    @DisplayName("the bundled typeface is TeX Gyre Adventor as TrueType, with its licence beside it and the fallback's")
    void theBundledTypeface() throws IOException {
        LetterTypeface typeface = LetterTypeface.bundled();

        assertThat(typeface.family()).isEqualTo("Adventor TT");
        assertThat(typeface.location()).isEqualTo(LetterTypeface.BUNDLED);
        assertThat(text("fonts/letters/adventor/GUST-FONT-LICENSE.txt")).contains("GUST Font License")
                .contains("LaTeX Project Public License");
        assertThat(text("fonts/letters/dejavu/LICENSE.txt")).contains("Bitstream Vera");
    }

    @Test
    @DisplayName("🔑 the swap point: the one setting names another font, and the letters are set in it")
    void theSettingSwapsTheTypeface() throws IOException {
        ApplicationContextRunner context = new ApplicationContextRunner()
                .withUserConfiguration(LetterTypefaceConfiguration.class);
        context.run(started -> assertThat(started.getBean(LetterTypeface.class).family()).isEqualTo("Adventor TT"));
        context.withPropertyValues(LetterTypeface.PROPERTY + "=" + DEJAVU)
                .run(started -> assertThat(started.getBean(LetterTypeface.class).family()).isEqualTo("DejaVu Sans"));

        // ...and a letter set in it embeds that font, and only it.
        LetterTypeface swapped = LetterTypeface.load(new DefaultResourceLoader(), DEJAVU);
        byte[] pdf = LetterLayout.render("# A letter\nDear Ms Exemplé\n", swapped, Letterhead.sasbo());
        assertThat(fontNames(pdf)).isNotEmpty().allSatisfy(name -> assertThat(name).contains("DejaVuSans"));
    }

    @Test
    @DisplayName("a font with CFF outlines is refused at start-up, saying to convert it first")
    void cffOutlinesAreRefused() {
        byte[] otto = new byte[64];
        System.arraycopy("OTTO".getBytes(StandardCharsets.US_ASCII), 0, otto, 0, 4);

        assertThatThrownBy(() -> LetterTypeface.load(serving(otto), "memory:typeface/"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CFF (PostScript) outlines")
                .hasMessageContaining("config/fonts/README.md");
    }

    @Test
    @DisplayName("a font missing an accented letter of Latin-1 is refused, naming each letter it lacks")
    void aFontWithoutTheAccentsIsRefused() throws IOException {
        byte[] asciiOnly = subset(bytes("fonts/letters/adventor/regular.ttf"), IntStream.rangeClosed(0x20, 0x7E));

        assertThatThrownBy(() -> LetterTypeface.load(serving(asciiOnly), "memory:typeface/"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("U+00E9 é")
                .hasMessageContaining("U+00EB ë")
                .hasMessageContaining("must print every accented letter of Latin-1 itself");
    }

    @Test
    @DisplayName("a font whose licence flag forbids embedding is refused")
    void aFontThatForbidsEmbeddingIsRefused() throws IOException {
        byte[] restricted = withFsType(bytes("fonts/letters/adventor/regular.ttf"), 0x0002);

        assertThatThrownBy(() -> LetterTypeface.load(serving(restricted), "memory:typeface/"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("forbids embedding");
    }

    @Test
    @DisplayName("a folder without its regular face is refused, naming the setting")
    void aMissingFaceIsRefused() {
        assertThatThrownBy(() -> LetterTypeface.load(new DefaultResourceLoader(), "classpath:fonts/no-such-folder/"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("regular.ttf")
                .hasMessageContaining(LetterTypeface.PROPERTY);
    }

    @Test
    @DisplayName("🔒 no face reaches PDFBox with a GSUB table -- the fallback, unmodified on disk, included")
    void noGsubReachesPdfBox() throws IOException {
        byte[] onDisk = bytes("fonts/letters/dejavu/regular.ttf");
        assertThat(tables(onDisk)).as("DejaVu Sans as shipped has one").contains("GSUB");

        try (LetterTypeface.Opened faces = LetterTypeface.load(new DefaultResourceLoader(), DEJAVU).open()) {
            assertThat(faces.face(LetterTypeface.Face.REGULAR).getGsubData()).isSameAs(GsubData.NO_DATA_FOUND);
            assertThat(faces.fallback().getGsubData()).isSameAs(GsubData.NO_DATA_FOUND);
        }
        byte[] without = LetterTypeface.withoutGsub(onDisk);
        List<String> kept = new ArrayList<>(tables(onDisk));
        kept.remove("GSUB");
        assertThat(tables(without)).containsExactlyElementsOf(kept);
        try (TrueTypeFont font = new TTFParser().parse(new RandomAccessReadBuffer(without));
             TrueTypeFont original = new TTFParser().parse(new RandomAccessReadBuffer(onDisk))) {
            assertThat(font.getNumberOfGlyphs()).isEqualTo(original.getNumberOfGlyphs());
            assertThat(font.getUnicodeCmapLookup().getGlyphId('é'))
                    .isEqualTo(original.getUnicodeCmapLookup().getGlyphId('é'));
        }
    }

    // ------------------------------------------------------------------------------------------

    /** A loader that serves {@code regular} as a folder's regular face, and the classpath for everything else. */
    private static ResourceLoader serving(byte[] regular) {
        DefaultResourceLoader classpath = new DefaultResourceLoader();
        return new ResourceLoader() {
            @Override
            public Resource getResource(String location) {
                if (location.startsWith("memory:")) {
                    return location.endsWith("/regular.ttf") ? new ByteArrayResource(regular)
                            : new DescriptiveResource(location);
                }
                return classpath.getResource(location);
            }

            @Override
            public ClassLoader getClassLoader() {
                return classpath.getClassLoader();
            }
        };
    }

    private static byte[] subset(byte[] font, IntStream codePoints) throws IOException {
        try (TrueTypeFont parsed = new TTFParser().parse(new RandomAccessReadBuffer(font))) {
            TTFSubsetter subsetter = new TTFSubsetter(parsed);
            Set<Integer> wanted = new HashSet<>();
            codePoints.forEach(wanted::add);
            subsetter.addAll(wanted);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            subsetter.writeToStream(out);
            return out.toByteArray();
        }
    }

    /** {@code font} with its OS/2 table's embedding flags set to {@code fsType}. */
    private static byte[] withFsType(byte[] font, int fsType) {
        byte[] copy = font.clone();
        ByteBuffer buffer = ByteBuffer.wrap(copy);
        int tables = Short.toUnsignedInt(buffer.getShort(4));
        for (int i = 0; i < tables; i++) {
            int record = 12 + 16 * i;
            String tag = new String(copy, record, 4, StandardCharsets.US_ASCII);
            if (tag.equals("OS/2")) {
                buffer.putShort(buffer.getInt(record + 8) + 8, (short) fsType);
                return copy;
            }
        }
        throw new AssertionError("no OS/2 table");
    }

    private static List<String> tables(byte[] font) {
        ByteBuffer buffer = ByteBuffer.wrap(font);
        int count = Short.toUnsignedInt(buffer.getShort(4));
        List<String> tags = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            tags.add(new String(font, 12 + 16 * i, 4, StandardCharsets.US_ASCII));
        }
        return tags;
    }

    private static List<String> fontNames(byte[] pdf) throws IOException {
        List<String> names = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDResources resources = document.getPage(0).getResources();
            for (COSName name : resources.getFontNames()) {
                names.add(resources.getFont(name).getName());
            }
        }
        return names;
    }

    private static byte[] bytes(String resource) throws IOException {
        try (InputStream stream = LetterTypefaceTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(stream).as(resource).isNotNull();
            return stream.readAllBytes();
        }
    }

    private static String text(String resource) throws IOException {
        return new String(bytes(resource), StandardCharsets.UTF_8);
    }
}
