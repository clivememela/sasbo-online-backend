package za.co.titandynamix.sasbo_online_backend.payroll;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.crypt.EncryptionMode;
import org.apache.poi.poifs.crypt.Encryptor;
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
 * A file that leaves SASBO for an employer, protected by a password the administrator chooses (POPIA; month end's rule
 * for files sent to banks): a workbook encrypted as Excel encrypts one (agile, AES-256), a PDF with AES-256 and a
 * random owner password. 🔒 The password is used here once and kept nowhere.
 */
public final class ProtectedFiles {

    private static final SecureRandom RANDOM = new SecureRandom();

    private ProtectedFiles() {
    }

    public static byte[] workbook(byte[] xlsx, String password) {
        try (POIFSFileSystem fs = new POIFSFileSystem();
             OPCPackage pkg = OPCPackage.open(new ByteArrayInputStream(xlsx));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Encryptor encryptor = new EncryptionInfo(EncryptionMode.agile).getEncryptor();
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
