package za.co.titandynamix.sasbo_online_backend.documents;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.Deflater;

/**
 * SASBO's letterhead as a letter draws it, from "Letter Head.doc", which Stephne sent (DOD-31): the logo at the top
 * left, the address block at the top right, and the line at the foot of every page.
 *
 * <p>🔒 The logo is EMBEDDED in each document, never a remote reference a mail client would fetch (SD3-C). It is
 * decoded once and kept as a compressed image stream, laid on white, so a batch of letters copies bytes into each
 * document instead of compressing the picture again for every member.
 */
public final class Letterhead {

    static final String LOGO = "letters/letterhead/logo.png";
    static final String ADDRESS = "letters/letterhead/address.txt";
    static final String FOOTER = "letters/letterhead/footer.txt";

    private final int logoWidth;
    private final int logoHeight;
    private final byte[] logoStream;
    private final List<String> address;
    private final String footer;

    private Letterhead(int logoWidth, int logoHeight, byte[] logoStream, List<String> address, String footer) {
        this.logoWidth = logoWidth;
        this.logoHeight = logoHeight;
        this.logoStream = logoStream;
        this.address = address;
        this.footer = footer;
    }

    /** SASBO's letterhead, read once from its resources. */
    public static Letterhead sasbo() {
        return Sasbo.INSTANCE;
    }

    private static final class Sasbo {
        private static final Letterhead INSTANCE = of(bytes(LOGO), text(ADDRESS).lines().toList(),
                text(FOOTER).strip());
    }

    /** A letterhead from a PNG or JPEG logo, the address block's lines (a blank one is a gap) and the footer line. */
    static Letterhead of(byte[] logo, List<String> address, String footer) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(logo));
            if (image == null) {
                throw new IllegalStateException("The letterhead's logo is not a picture");
            }
            int width = image.getWidth();
            int height = image.getHeight();
            byte[] rgb = new byte[width * height * 3];
            int at = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int argb = image.getRGB(x, y);
                    int alpha = argb >>> 24;
                    for (int shift = 16; shift >= 0; shift -= 8) {
                        // Laid on white: the logo's PNG carries an alpha channel, though every pixel of it is opaque.
                        int channel = (argb >> shift) & 0xFF;
                        rgb[at++] = (byte) ((channel * alpha + 255 * (255 - alpha)) / 255);
                    }
                }
            }
            return new Letterhead(width, height, deflate(rgb), List.copyOf(address), footer);
        } catch (IOException e) {
            throw new UncheckedIOException("The letterhead's logo could not be read", e);
        }
    }

    /** The address block, line by line; a blank line is a gap. */
    List<String> address() {
        return address;
    }

    /** The line at the foot of every page, beside its page number. */
    String footer() {
        return footer;
    }

    /** The logo's width over its height. */
    float logoAspect() {
        return (float) logoWidth / logoHeight;
    }

    /** The logo as an image of {@code pdf} -- drawn on each of its pages from one copy. */
    PDImageXObject logo(PDDocument pdf) throws IOException {
        return new PDImageXObject(pdf, new ByteArrayInputStream(logoStream), COSName.FLATE_DECODE, logoWidth,
                logoHeight, 8, PDDeviceRGB.INSTANCE);
    }

    private static byte[] deflate(byte[] raw) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            deflater.setInput(raw);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length / 4);
            byte[] buffer = new byte[8192];
            while (!deflater.finished()) {
                out.write(buffer, 0, deflater.deflate(buffer));
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private static byte[] bytes(String resource) {
        try (InputStream stream = Letterhead.class.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("The letterhead is missing " + resource);
            }
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String text(String resource) {
        return new String(bytes(resource), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
