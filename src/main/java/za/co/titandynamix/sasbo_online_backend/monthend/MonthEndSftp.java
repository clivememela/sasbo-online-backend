package za.co.titandynamix.sasbo_online_backend.monthend;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Puts a provider's monthly list onto the provider's SFTP server (October build; Stephne MEN-35: "Azoza List goes
 * directly onto their STFP"). OFF UNLESS IT IS SET UP: a host, a user, the PATH of a private key the host mounts as a
 * secret file, and the server's known-hosts line -- all four, set by the owner on a host, none of them in the
 * repository. Until then {@link #configured()} is false and nothing is attempted.
 *
 * <p>The server is checked, never trusted: its host key must match the known-hosts line ({@code StrictHostKeyChecking
 * yes}), and the key is the only way in (no password). Every step is time-boxed. A failure is thrown as its class --
 * JSch's messages can carry a host and a user, and a caller logs the class alone.
 */
@Component
public class MonthEndSftp {

    /** What the screen says of SFTP on this server. */
    public static final String CONFIGURED = "CONFIGURED";
    public static final String NOT_CONFIGURED = "NOT_CONFIGURED";

    static final int CONNECT_TIMEOUT_MILLIS = 15_000;

    private final String host;
    private final int port;
    private final String username;
    private final String privateKeyPath;
    private final String knownHosts;
    private final String directory;

    public MonthEndSftp(@Value("${month-end.sftp.host:}") String host, @Value("${month-end.sftp.port:22}") int port,
                        @Value("${month-end.sftp.username:}") String username,
                        @Value("${month-end.sftp.private-key-path:}") String privateKeyPath,
                        @Value("${month-end.sftp.known-hosts:}") String knownHosts,
                        @Value("${month-end.sftp.directory:}") String directory) {
        this.host = host == null ? "" : host.trim();
        this.port = port;
        this.username = username == null ? "" : username.trim();
        this.privateKeyPath = privateKeyPath == null ? "" : privateKeyPath.trim();
        this.knownHosts = knownHosts == null ? "" : knownHosts.trim();
        this.directory = directory == null ? "" : directory.trim();
    }

    /** Whether all four are set: host, user, key and the server's known host. */
    public boolean configured() {
        return !host.isEmpty() && !username.isEmpty() && !privateKeyPath.isEmpty() && !knownHosts.isEmpty();
    }

    /** {@value #CONFIGURED} or {@value #NOT_CONFIGURED}. */
    public String state() {
        return configured() ? CONFIGURED : NOT_CONFIGURED;
    }

    /** The server a delivery is recorded against: its host, never the user or the key. */
    public String destination() {
        return host;
    }

    /** Uploads {@code content} as {@code fileName}. Only where {@link #configured()}. */
    public void upload(String fileName, byte[] content) throws IOException {
        if (!configured()) {
            throw new IllegalStateException("SFTP is not set up on this server");
        }
        Session session = null;
        ChannelSftp channel = null;
        try {
            JSch jsch = new JSch();
            jsch.setKnownHosts(new ByteArrayInputStream(knownHosts.getBytes(StandardCharsets.UTF_8)));
            jsch.addIdentity(privateKeyPath);
            session = jsch.getSession(username, host, port);
            session.setConfig("StrictHostKeyChecking", "yes");
            session.setConfig("PreferredAuthentications", "publickey");
            session.setTimeout(CONNECT_TIMEOUT_MILLIS);
            session.connect(CONNECT_TIMEOUT_MILLIS);
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(CONNECT_TIMEOUT_MILLIS);
            if (!directory.isEmpty()) {
                channel.cd(directory);
            }
            channel.put(new ByteArrayInputStream(content), fileName);
        } catch (JSchException | SftpException failed) {
            throw new IOException(failed.getClass().getSimpleName());
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
        }
    }
}
