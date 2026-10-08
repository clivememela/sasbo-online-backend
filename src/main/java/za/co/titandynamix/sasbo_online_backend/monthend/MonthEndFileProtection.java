package za.co.titandynamix.sasbo_online_backend.monthend;

import net.lingala.zip4j.io.outputstream.ZipOutputStream;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.AesVersion;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.EncryptionMethod;
import org.apache.poi.poifs.crypt.ChainingMode;
import org.apache.poi.poifs.crypt.CipherAlgorithm;
import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.crypt.EncryptionMode;
import org.apache.poi.poifs.crypt.Encryptor;
import org.apache.poi.poifs.crypt.HashAlgorithm;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

/**
 * How a file that leaves SASBO for a bank or a provider is protected (October build; Stephne MEN-05: "We would need Bank
 * reports to have a password access as this is a requirement for POPIA").
 *
 * <h2>The mechanism, and why</h2>
 * <ul>
 *   <li><b>A workbook</b> is encrypted by Excel's own scheme -- ECMA-376 agile encryption, AES-256 with SHA-512 and
 *       100,000 rounds of key stretching -- so the bank opens it in Excel, which asks for the password; nothing to
 *       install;</li>
 *   <li><b>A text file</b> (Absa's fixed-width records, a CSV) has no password of its own, so it travels inside a ZIP
 *       encrypted with AES-256 (WinZip's AE-2), which 7-Zip, WinZip and WinRAR open. Never ZipCrypto, the old ZIP
 *       password: it falls to a known-plaintext attack in minutes, and a fixed-width file's every line ends alike;</li>
 *   <li><b>One password a file</b>, generated here, shown once to the person who produced the file to give to the bank
 *       by another channel -- telephone or SMS, never the e-mail that carries the file -- and KEPT NOWHERE: not in the
 *       database, not in a log. A lost password is a file produced again. A per-bank password kept for months would have
 *       to be stored, so a leaked database would open every file; a password nobody keeps opens one file, once.</li>
 * </ul>
 * Whether the banks would rather agree one password each and keep it (MEN-05's clarification) changes the source of the
 * password, not the encryption.
 */
public final class MonthEndFileProtection {

    /**
     * What a password is made of: capitals and digits a person reads aloud without a second look -- no I, L or O, no 0
     * or 1. Thirty-one symbols, sixteen of them: about 79 bits, beyond guessing even offline.
     */
    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    /** Sixteen symbols, read and typed in four groups of four, the hyphens part of the password. */
    static final int GROUPS = 4;
    static final int GROUP_LENGTH = 4;

    /** What Excel's agile scheme stretches a password with: Excel's own default. */
    static final int SPIN_COUNT = 100_000;

    private static final SecureRandom RANDOM = new SecureRandom();

    private MonthEndFileProtection() {
    }

    /** A new password for one file: {@code K7MW-Q3ZR-T8XH-N4CP}. Held by its caller until it is shown, and nowhere else. */
    public static String newPassword() {
        StringBuilder password = new StringBuilder(GROUPS * (GROUP_LENGTH + 1));
        for (int g = 0; g < GROUPS; g++) {
            if (g > 0) {
                password.append('-');
            }
            for (int c = 0; c < GROUP_LENGTH; c++) {
                password.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
        }
        return password.toString();
    }

    /** What writes a file's own bytes. */
    @FunctionalInterface
    public interface Body {
        void writeTo(OutputStream out) throws IOException;
    }

    /**
     * {@code body} written as the one entry {@code entryName} of a ZIP encrypted with AES-256, to {@code out} -- streamed,
     * never held whole. The entry is dated {@code modifiedMillis}, the file's own date rather than the clock's second.
     */
    public static void aesZip(OutputStream out, String password, String entryName, long modifiedMillis, Body body) {
        ZipParameters entry = new ZipParameters();
        entry.setCompressionMethod(CompressionMethod.DEFLATE);
        entry.setEncryptFiles(true);
        entry.setEncryptionMethod(EncryptionMethod.AES);
        entry.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
        entry.setAesVersion(AesVersion.TWO);
        entry.setFileNameInZip(entryName);
        entry.setLastModifiedFileTime(modifiedMillis);
        char[] secret = password.toCharArray();
        try (ZipOutputStream zip = new ZipOutputStream(new Unclosed(out), secret)) {
            zip.putNextEntry(entry);
            body.writeTo(zip);
            zip.closeEntry();
        } catch (IOException unwritable) {
            throw new UncheckedIOException("A protected file could not be written", unwritable);
        } finally {
            java.util.Arrays.fill(secret, '\0');
        }
    }

    /**
     * A workbook written by {@code body}, encrypted by Excel's agile scheme (AES-256, SHA-512), to {@code out}. The
     * workbook's own bytes pass through the encryptor's stream and are never kept in the clear.
     */
    public static void excelEncrypted(OutputStream out, String password, Body body) {
        EncryptionInfo info = new EncryptionInfo(EncryptionMode.agile, CipherAlgorithm.aes256, HashAlgorithm.sha512,
                -1, -1, ChainingMode.cbc);
        info.getVerifier().setSpinCount(SPIN_COUNT);
        Encryptor encryptor = info.getEncryptor();
        encryptor.confirmPassword(password);
        try (POIFSFileSystem container = new POIFSFileSystem()) {
            try (OutputStream encrypted = encryptor.getDataStream(container)) {
                body.writeTo(encrypted);
            }
            container.writeFilesystem(out);
        } catch (IOException unwritable) {
            throw new UncheckedIOException("A protected workbook could not be written", unwritable);
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException("Excel's encryption is not available on this server", unavailable);
        }
    }

    /** Lets an encrypting stream finish without closing the stream it was handed: its caller closes that. */
    private static final class Unclosed extends java.io.FilterOutputStream {
        Unclosed(OutputStream out) {
            super(out);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }
}
