package za.co.titandynamix.sasbo_online_backend.monthend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Law Call's SFTP (October build; Stephne MEN-35): off unless all four of host, user, key path and known host are set --
 * the default, NOT_CONFIGURED -- and nothing attempted where it is off. Its delivery is recorded against the host, never
 * the user or the key.
 */
@DisplayName("The providers' SFTP")
class MonthEndSftpTest {

    static final String HOST_KEY = "sftp.example.org ssh-ed25519 AAAAINVENTEDHOSTKEY";

    @Test
    @DisplayName("not configured by default, and with any one of the four missing")
    void offUnlessAllFourAreSet() {
        assertThat(new MonthEndSftp("", 22, "", "", "", "").state()).isEqualTo(MonthEndSftp.NOT_CONFIGURED);
        assertThat(new MonthEndSftp("sftp.example.org", 22, "invented", "/etc/secrets/invented", "", "").configured())
                .as("no known host: the server would be trusted blind").isFalse();
        assertThat(new MonthEndSftp("sftp.example.org", 22, "invented", "", HOST_KEY, "").configured()).isFalse();
        assertThat(new MonthEndSftp("sftp.example.org", 22, "", "/etc/secrets/invented", HOST_KEY, "").configured())
                .isFalse();
        MonthEndSftp set = new MonthEndSftp(" sftp.example.org ", 22, "invented", "/etc/secrets/invented", HOST_KEY,
                "inbox");
        assertThat(set.state()).isEqualTo(MonthEndSftp.CONFIGURED);
        assertThat(set.destination()).isEqualTo("sftp.example.org");
    }

    @Test
    @DisplayName("asked to upload where it is off, it refuses and connects to nothing")
    void nothingAttemptedWhereOff() {
        MonthEndSftp off = new MonthEndSftp(null, 22, null, null, null, null);
        assertThatThrownBy(() -> off.upload("LAWCALL_260930.zip", new byte[] {1}))
                .isInstanceOf(IllegalStateException.class);
    }
}
