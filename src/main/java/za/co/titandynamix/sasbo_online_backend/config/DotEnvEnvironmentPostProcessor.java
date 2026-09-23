package za.co.titandynamix.sasbo_online_backend.config;

import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads {@code .env.local} and {@code .env} into the Spring
 * {@link org.springframework.core.env.Environment} before any bean is created, so that
 * {@code application.yaml} placeholders such as {@code ${SPRING_DATASOURCE_URL}} resolve on a
 * developer machine without the variables having to be exported into the shell or duplicated into an
 * IDE run configuration.
 *
 * <p><b>Why this exists.</b> {@code application.yaml} reads its datasource settings from environment
 * variables, which a host injects in a deployment. Locally nothing read
 * {@code .env} at all, so the file sat in the repository looking authoritative while having no
 * effect. This closes that gap with no new dependency — it is plain JDK file reading.
 *
 * <h2>🔴 The developer-checkout guard</h2>
 * <b>Neither file is read unless {@code .env.local} exists.</b> That single condition is what makes
 * this safe to ship, and it is not a stylistic choice:
 *
 * <ul>
 *   <li>{@code .env} is <b>tracked in git</b>, so it is present in every deployment. Reading it
 *       unconditionally would make a committed file part of production configuration.</li>
 *   <li>That was not hypothetical here. The deployment config of the time published <b>{@code DB_SSLMODE}</b> while
 *       {@code application.yaml} reads <b>{@code ${SSL_MODE}}</b> — different names. The committed
 *       {@code .env} carries {@code SSL_MODE=prefer}. An unguarded loader would therefore quietly
 *       satisfy that placeholder in production with <i>prefer</i>, downgrading a database connection
 *       that should be <i>require</i>, and turning a loud startup failure into a silently weaker
 *       connection. A missing setting must keep failing loudly.</li>
 *   <li>{@code .env.local} is git-ignored, so it cannot exist in a deployment or in a fresh clone.
 *       Its presence is therefore a reliable marker of a developer's working copy, and its absence
 *       leaves every other environment behaving <b>exactly as it did before this class existed</b> —
 *       including a teammate who has not opted in.</li>
 * </ul>
 *
 * <h2>Precedence</h2>
 * Highest wins:
 * <ol>
 *   <li>Command-line arguments — {@code --spring.flyway.enabled=false} still overrides everything</li>
 *   <li>JVM system properties ({@code -D...})</li>
 *   <li><b>{@code .env.local}</b> — machine-local, git-ignored; overrides real environment variables so
 *       a developer can point at their own database without editing a tracked file</li>
 *   <li>Real environment variables — what the host injects in production</li>
 *   <li><b>{@code .env}</b> — shared committed defaults, so it can never mask what the host sets</li>
 *   <li>{@code application.yaml}</li>
 * </ol>
 *
 * <p>This mirrors the convention used on the MobiZone project ({@code server/src/env.js}), where
 * {@code .env} loads without override and {@code .env.local} loads with it. One deliberate
 * difference: command-line arguments and system properties stay above {@code .env.local} here,
 * because a flag typed on the command line is the most explicit statement of intent available and
 * should not be silently overridden by a file.
 *
 * <p>Files are read from the process working directory — the {@code server/} directory when started
 * with {@code ./gradlew bootRun} or {@code java -jar build/libs/...} from that folder.
 */
public class DotEnvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String DOTENV = ".env";
    private static final String DOTENV_LOCAL = ".env.local";

    private static final String SOURCE_DOTENV = "dotenv[.env]";
    private static final String SOURCE_DOTENV_LOCAL = "dotenv[.env.local]";

    /** Byte-order mark. Notepad and PowerShell redirection both write one; it would corrupt the first key. */
    private static final char BOM = '﻿';

    private final Log log;

    public DotEnvEnvironmentPostProcessor(DeferredLogFactory logFactory) {
        // EnvironmentPostProcessors run before the logging system is initialised, so logging has to be
        // deferred or it is silently dropped. Spring Boot injects this factory for exactly that reason.
        this.log = logFactory.getLog(DotEnvEnvironmentPostProcessor.class);
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path workingDirectory = Paths.get(System.getProperty("user.dir"));
        Path localFile = workingDirectory.resolve(DOTENV_LOCAL);

        // The guard. No .env.local means this is not a developer checkout — deployments and teammates
        // who have not opted in are left byte-for-byte as they were. See the class javadoc.
        if (!Files.isRegularFile(localFile)) {
            this.log.debug("No .env.local in " + workingDirectory + " — dotenv loading skipped, "
                    + "environment variables used as-is");
            return;
        }

        MutablePropertySources propertySources = environment.getPropertySources();

        // .env — shared defaults. Added LAST so real environment variables win over it, which keeps a
        // stale committed value from overriding what a host injects.
        Map<String, Object> shared = this.read(workingDirectory.resolve(DOTENV));
        if (!shared.isEmpty()) {
            propertySources.addLast(new SystemEnvironmentPropertySource(SOURCE_DOTENV, shared));
            this.log.info("Loaded " + shared.size() + " entries from .env");
        }

        // .env.local — machine-local overrides. Inserted directly ABOVE the real environment variables
        // so it beats both them and .env, while still sitting below command-line arguments.
        Map<String, Object> local = this.read(localFile);
        if (!local.isEmpty()) {
            SystemEnvironmentPropertySource source = new SystemEnvironmentPropertySource(SOURCE_DOTENV_LOCAL, local);
            if (propertySources.contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
                propertySources.addBefore(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, source);
            } else {
                propertySources.addFirst(source);
            }
            this.log.info("Loaded " + local.size() + " entries from .env.local (overrides .env and environment)");
        } else {
            // The guard passed but the file yielded nothing — worth saying out loud, because the
            // symptom otherwise is an unresolved-placeholder failure with no hint at the cause.
            this.log.warn(".env.local was found but contained no usable KEY=VALUE entries");
        }
    }

    /**
     * Reads a dotenv-style file. Returns an empty map when the file is absent or unreadable — a
     * missing file is a normal case, not a failure.
     */
    private Map<String, Object> read(Path file) {
        if (!Files.isRegularFile(file)) {
            return Map.of();
        }

        String content;
        try {
            // Decoded leniently rather than with Files.readAllLines, which throws on the first byte
            // that is not valid UTF-8. One odd character in a password should not discard the whole
            // file and leave every placeholder unresolved.
            content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            this.log.warn("Could not read " + file.getFileName() + ": " + ex.getMessage());
            return Map.of();
        }
        return parse(content);
    }

    /**
     * Parses dotenv content into ordered key/value pairs.
     *
     * <p>Supported: {@code KEY=value}, a leading {@code export }, {@code #} comments, blank lines, a
     * leading byte-order mark, CRLF or LF line endings, and values wrapped in single or double
     * quotes. Escape sequences are expanded only inside double quotes, matching the dotenv
     * implementations this mirrors. A later duplicate key wins.
     *
     * <p>Package-private and static so it can be unit tested directly, without a Spring context or a
     * particular working directory.
     */
    static Map<String, Object> parse(String content) {
        Map<String, Object> values = new LinkedHashMap<>();

        for (String raw : splitLines(content)) {
            String line = raw.trim();
            if (line.isEmpty() || line.charAt(0) == '#') {
                continue;
            }
            if (line.startsWith("export ")) {
                line = line.substring("export ".length()).trim();
            }

            int separator = line.indexOf('=');
            if (separator <= 0) {
                // No '=', or a line beginning with '=' — malformed, so skipped rather than guessed at.
                continue;
            }

            String key = line.substring(0, separator).trim();
            if (key.isEmpty()) {
                continue;
            }
            values.put(key, unquote(line.substring(separator + 1).trim()));
        }
        return values;
    }

    /** Splits on LF or CRLF and strips a leading BOM from the first line. */
    private static List<String> splitLines(String content) {
        String normalised = content.isEmpty() || content.charAt(0) != BOM ? content : content.substring(1);
        List<String> lines = new ArrayList<>();
        for (String line : normalised.split("\r?\n", -1)) {
            lines.add(line);
        }
        return lines;
    }

    /**
     * Strips one layer of matching surrounding quotes, expanding escapes only for double-quoted
     * values. An unquoted value keeps its trimmed form, so a trailing space cannot creep into a
     * password; a quoted value keeps its spaces, which is how a password with one is expressed.
     */
    private static String unquote(String value) {
        if (value.length() < 2) {
            return value;
        }

        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);

        if (first == '\'' && last == '\'') {
            return value.substring(1, value.length() - 1);
        }
        if (first == '"' && last == '"') {
            return expandEscapes(value.substring(1, value.length() - 1));
        }
        return value;
    }

    /**
     * Expands backslash escapes in a single left-to-right pass.
     *
     * <p>Chained {@code String.replace} calls cannot do this correctly: replacing {@code \n} before
     * {@code \\} turns the literal two-character sequence {@code \\n} into a newline when it should
     * produce a backslash followed by {@code n}. One pass consumes each escape exactly once.
     */
    private static String expandEscapes(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current != '\\' || i == value.length() - 1) {
                out.append(current);
                continue;
            }
            char next = value.charAt(++i);
            switch (next) {
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case '"' -> out.append('"');
                case '\\' -> out.append('\\');
                // An unrecognised escape is left verbatim rather than swallowed, so a Windows path
                // written unescaped inside quotes survives intact.
                default -> out.append('\\').append(next);
            }
        }
        return out.toString();
    }
}
