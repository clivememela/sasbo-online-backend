package za.co.titandynamix.sasbo_online_backend.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V50 IS NEVER EDITED (month end 03.2, BR51). ACB's status changes table was made by V50 and is widened by V56, in a
 * migration of its own: an applied migration edited is skipped silently by Flyway on every database that already ran
 * it, and a hosted database is exactly that (memory: flyway-reserved-versions-never-run). So its bytes are pinned --
 * line endings aside, which a checkout decides.
 */
@DisplayName("V50, as ACB applied it")
class V50UntouchedTest {

    /** SHA-256 of V50 as ACB 03.5 committed it (4f9952a), with LF line endings. */
    static final String V50 = "abd8ab5a3754a3fa2a49c681e99efb535274a5869b830e8b2b1b1233eb45b944";

    @Test
    @DisplayName("🔒 BR51: V50 is byte for byte as ACB applied it -- its reasons are widened by V56, never here")
    void v50IsUntouched() throws IOException, NoSuchAlgorithmException {
        String sql = Files.readString(Paths.get("src", "main", "resources", "db", "migration",
                "V50__member_status_changes.sql"), StandardCharsets.UTF_8).replace("\r\n", "\n");
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(sql.getBytes(StandardCharsets.UTF_8)));

        assertThat(hash).as("V50 was edited. Widen member_status_changes in a migration of its own").isEqualTo(V50);
    }
}
