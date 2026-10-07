package za.co.titandynamix.sasbo_online_backend.payroll;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.poifs.crypt.ChainingMode;
import org.apache.poi.poifs.crypt.CipherAlgorithm;
import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.crypt.EncryptionMode;
import org.apache.poi.poifs.crypt.Encryptor;
import org.apache.poi.poifs.crypt.HashAlgorithm;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * A file that leaves SASBO for an employer -- the query list (PAY-21) -- protected as every file the system lets SASBO
 * send out is, month end's bank files included (POPIA; Stephne MEN-05):
 * <ul>
 *   <li><b>One password a file, generated here</b> ({@link #newPassword()}): sixteen capitals and digits a person reads
 *       aloud without a second look, in four groups -- {@code K7MW-Q3ZR-T8XH-N4CP}, about 79 bits. It is shown once to
 *       whoever produced the file, who gives it to the employer by another channel (telephone or SMS, never the e-mail
 *       that carries the file), and it is KEPT NOWHERE: not in the database, not in a log. A lost password is a file
 *       produced again. 🔒 The administrator never chooses it.</li>
 *   <li><b>A workbook</b> under Excel's own encryption -- agile, AES-256 with SHA-512 and Excel's 100,000 rounds -- so
 *       the employer opens it in Excel, which asks for the password.</li>
 *   <li><b>A PDF</b> encrypted with AES-256, the password to open it the generated one, the owner password random and
 *       discarded.</li>
 * </ul>
 * The alphabet, the groups and Excel's parameters are month end's ({@code MonthEndFileProtection}, October build), so
 * an employer and a bank receive files protected alike.
 */
public final class ProtectedFiles {

    /** Capitals and digits without look-alikes: no I, L or O, no 0 or 1. Thirty-one symbols. */
    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    /** Sixteen symbols, read and typed in four groups of four, the hyphens part of the password. */
    static final int GROUPS = 4;
    static final int GROUP_LENGTH = 4;

    /** What Excel's agile scheme stretches a password with: Excel's own default. */
    static final int SPIN_COUNT = 100_000;

    /** What the answer that shows the password says beside it. */
    public static final String PASSWORD_NOTE = "This password is shown once and is not kept. Give it to the employer by "
            + "another channel -- telephone or SMS -- never in the e-mail that carries the file. Type it exactly as "
            + "shown, hyphens included. Lost, produce the file again: a new file, with a new password.";

    private static final SecureRandom RANDOM = new SecureRandom();

    private ProtectedFiles() {
    }

    /** A new password for one file. Held by its caller until it is shown, and nowhere else. */
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

    /** {@code xlsx} under Excel's agile encryption: AES-256, SHA-512, 100,000 rounds. */
    public static byte[] workbook(byte[] xlsx, String password) {
        try (POIFSFileSystem fs = new POIFSFileSystem();
             OPCPackage pkg = OPCPackage.open(new ByteArrayInputStream(xlsx));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            EncryptionInfo info = new EncryptionInfo(EncryptionMode.agile, CipherAlgorithm.aes256, HashAlgorithm.sha512,
                    -1, -1, ChainingMode.cbc);
            info.getVerifier().setSpinCount(SPIN_COUNT);
            Encryptor encryptor = info.getEncryptor();
            encryptor.confirmPassword(password);
            try (OutputStream data = encryptor.getDataStream(fs)) {
                pkg.save(data);
            }
            fs.writeFilesystem(out);
            return out.toByteArray();
        } catch (IOException | GeneralSecurityException | org.apache.poi.openxml4j.exceptions.InvalidFormatException e) {
            throw new IllegalStateException("a workbook could not be protected", e);
        }
    }

    /** {@code pdf} encrypted with AES-256: {@code password} opens it; the owner password is random and discarded. */
    public static byte[] pdf(byte[] pdf, String password) {
        try (PDDocument document = Loader.loadPDF(pdf); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] owner = new byte[24];
            RANDOM.nextBytes(owner);
            StandardProtectionPolicy policy = new StandardProtectionPolicy(HexFormat.of().formatHex(owner), password,
                    new AccessPermission());
            policy.setEncryptionKeyLength(256);
            document.protect(policy);
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("a PDF could not be protected", e);
        }
    }
}
