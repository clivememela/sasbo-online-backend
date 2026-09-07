package za.co.titandynamix.sasbo_online_backend.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Key-shaped material in files that are COMMITTED.
 *
 * <h2>Why the tracked set, and not the working tree</h2>
 * A secret in a working tree is a mistake. A secret in git history is an
 * incident: it is on every clone, in every fork and in every backup, and
 * deleting the file does not remove it. So this asks <b>git</b> what is
 * tracked, and reads the blob from the index when the file is not on disk --
 * a tracked file that has been moved aside is exactly the case a working-tree
 * scan would go green on.
 *
 * <h2>🔴 Path exclusions are applied FIRST, and that order is the design</h2>
 * A lockfile's integrity hashes are 40+ character high-entropy base64 strings
 * that no entropy test can tell from a real key. Scan them and the guard's
 * first run reports hundreds of findings -- and a first run with hundreds of
 * findings is not triaged, it is silenced. MobiZone recorded it exactly: "a
 * guard that cries wolf gets its threshold lowered by whoever is unlucky
 * enough to hit it."
 *
 * <p>The fix is to scope the <b>input</b>, never to raise the threshold. A
 * guard tuned by raising its threshold is a guard that has been switched off.
 *
 * <h2>This is the server twin of the browser's committedSecrets.js</h2>
 * Deliberately a separate implementation. Three independent repositories
 * cannot share a module, and pretending otherwise creates the sibling-path
 * coupling the plans forbid.
 */
public final class CommittedSecrets {

    private CommittedSecrets() {
    }

    /** One path never scanned, and the reason it is excluded. */
    public record Exclusion(Pattern match, String why) {
    }

    /** A file allowed to hold a real secret, with the register row that tracks it. */
    public record Allowed(String path, String row, String why) {
    }

    /** Where a secret was found. Never carries the secret itself. */
    public record Finding(String path, String signature, int line) {

        public String at() {
            return path + ":" + line;
        }
    }

    /**
     * Paths never scanned, each with its justification.
     *
     * <p>Asserted non-empty and justified entry by entry, because an exclusion
     * nobody can see is where the real key hides next time.
     */
    public static final List<Exclusion> PATH_EXCLUSIONS = List.of(
            new Exclusion(Pattern.compile("(^|/)(package-lock\\.json|gradle\\.lockfile)$"),
                    "lockfiles hold integrity hashes -- long high-entropy base64 strings that are "
                            + "indistinguishable from a key to any entropy scan, and there are "
                            + "hundreds of them in a single file."),
            new Exclusion(Pattern.compile("(?i)\\.(png|jpe?g|gif|ico|webp|svg|woff2?|ttf|eot|pdf|zip|jar|class)$"),
                    "binary and asset files. Their bytes are not source, and base64-encoded image "
                            + "data trips every entropy heuristic there is."),
            new Exclusion(Pattern.compile("(^|/)(build|out|target|\\.gradle)/"),
                    "build output. Nothing here is authored, and packaged output concatenates "
                            + "every string in the project."),
            new Exclusion(Pattern.compile("(^|/)gradle/wrapper/"),
                    "the Gradle wrapper's distribution metadata, which carries a SHA-256 checksum "
                            + "that reads as a high-entropy token."));

    /**
     * Files allowed to contain a real secret.
     *
     * 🔴 <b>Empty for this repository, and asserted empty.</b> That is a claim,
     * not an absence: the day someone adds an entry here they are making a
     * decision about a real credential in a real repository, and the assertion
     * turns that into a review conversation rather than a quiet edit.
     *
     * <p>{@code server/.env} is tracked and holds a database username and URL
     * with an <b>empty</b> password. It is a real finding -- and it is recorded
     * in the register rather than here, because an empty password is not a
     * secret and allowlisting it would train the next reader to ignore the
     * list.
     */
    public static final List<Allowed> ALLOWLIST = List.of();

    /** Read once and held -- see {@link #trackedFiles()}. */
    private static volatile List<String> TRACKED;

    /** Read once and held -- see {@link #scan()}. */
    private static volatile List<Finding> SCANNED;

    private record Signature(String name, Pattern pattern) {
    }

    private static final List<Signature> SIGNATURES = List.of(
            new Signature("Google API key", Pattern.compile("\\bAIza[0-9A-Za-z_-]{35}\\b")),
            new Signature("private key block",
                    Pattern.compile("-----BEGIN (?:[A-Z ]+ )?PRIVATE KEY-----")),
            new Signature("Stripe-style secret key",
                    Pattern.compile("\\bsk_(?:live|test)_[0-9A-Za-z]{16,}\\b")),
            new Signature("AWS access key id", Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b")),
            new Signature("Slack token", Pattern.compile("\\bxox[abprs]-[0-9A-Za-z-]{10,}\\b")),
            new Signature("JDBC URL carrying a password",
                    Pattern.compile("(?i)jdbc:[^\\s\"']*[?&;]password=[^\\s\"'&;]+")),
            new Signature("assigned credential literal",
                    Pattern.compile("(?i)\\b(?:password|passwd|secret|api[_-]?key|apikey"
                            + "|access[_-]?token|private[_-]?key|client[_-]?secret)\\b"
                            + "\\s*[:=]\\s*[\"'][^\"'\\s]{8,}[\"']")));

    private static final Pattern OBVIOUS_PLACEHOLDER = Pattern.compile(
            "(?i)^(?:x+|y+|\\*+|\\.+|-+|_+|0+|changeme|placeholder|your[_-]?\\w+|example|test"
                    + "|dummy|none|null|undefined|password|secret|todo)$");

    /** True when this path is excluded from scanning. */
    public static boolean isExcluded(String path) {
        return PATH_EXCLUSIONS.stream().anyMatch(e -> e.match().matcher(path).find());
    }

    /**
     * Every path git tracks, relative to the repository root.
     *
     * <p>Read once and held, in the idiom {@code MainSourceInventory.all()}
     * already uses next door and for the same reason. These were instance
     * fields on the guard under JUnit's default per-method lifecycle, so six
     * test methods meant six full scans of 256 tracked files and <b>twelve</b>
     * {@code git ls-files} subprocess spawns per run -- in the fast lane, which
     * is the one paid on every commit. Neither the git index nor the working
     * tree can change while the JVM is running.
     *
     * <p>{@code -z} rather than newline-separated output: git C-quotes a path
     * containing a newline or a non-ASCII byte, and a C-quoted path then fails
     * to resolve and is silently skipped -- a file excluded from the scan by
     * its own name. NUL-separated output is never quoted.
     */
    public static List<String> trackedFiles() {
        List<String> cached = TRACKED;
        if (cached == null) {
            synchronized (CommittedSecrets.class) {
                cached = TRACKED;
                if (cached == null) {
                    cached = Arrays.stream(git("ls-files", "-z").split("\0"))
                            .filter(line -> !line.isBlank())
                            .toList();
                    TRACKED = cached;
                }
            }
        }
        return cached;
    }

    /**
     * Every committed file whose contents match a secret signature.
     *
     * <p>Returns the path, the shape and the line -- <b>never the matched
     * text</b>. A guard that prints the key it found copies that key into CI
     * logs, test reports and issue trackers, which is a second disclosure
     * caused by the tool meant to prevent the first.
     */
    public static List<Finding> scan() {
        List<Finding> cached = SCANNED;
        if (cached == null) {
            synchronized (CommittedSecrets.class) {
                cached = SCANNED;
                if (cached == null) {
                    cached = doScan();
                    SCANNED = cached;
                }
            }
        }
        return cached;
    }

    /**
     * Findings outside the allowlist -- what a guard should assert on.
     *
     * <p>{@link #ALLOWLIST} was declared, documented and read by nothing:
     * {@code scan()} never consulted it, so the first person with a
     * legitimate entry to add would have reddened the guard anyway and been
     * told about a rule rather than about a mechanism. The browser
     * repository's twin already filters this way; this makes the two agree.
     */
    public static List<Finding> unallowedFindings() {
        return scan().stream()
                .filter(f -> ALLOWLIST.stream().noneMatch(a -> a.path().equals(f.path())))
                .toList();
    }

    private static List<Finding> doScan() {
        List<Finding> findings = new ArrayList<>();

        for (String path : trackedFiles()) {
            if (isExcluded(path)) {
                continue;
            }

            String contents = contentsOf(path);
            if (contents == null || contents.indexOf('\0') >= 0) {
                continue;
            }

            String[] lines = contents.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                for (Signature signature : SIGNATURES) {
                    Matcher matcher = signature.pattern().matcher(lines[i]);
                    if (!matcher.find()) {
                        continue;
                    }
                    if (OBVIOUS_PLACEHOLDER.matcher(valueOf(matcher.group())).matches()) {
                        continue;
                    }
                    findings.add(new Finding(path, signature.name(), i + 1));
                }
            }
        }

        return List.copyOf(findings);
    }

    private static String valueOf(String match) {
        int separator = Math.max(match.lastIndexOf('='), match.lastIndexOf(':'));
        String value = separator >= 0 ? match.substring(separator + 1) : match;
        return value.replace("\"", "").replace("'", "").trim();
    }

    /** The working-tree copy, or the index blob when the file is not on disk. */
    private static String contentsOf(String path) {
        Path file = Paths.get(path);
        try {
            if (Files.isRegularFile(file)) {
                if (Files.size(file) > 2_000_000) {
                    return null;
                }
                return Files.readString(file, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            // Not readable as text from disk. Fall through and ask git.
        }
        try {
            return git("show", ":" + path);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String git(String... args) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(false).start();
            String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("git " + String.join(" ", args) + " timed out");
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException(
                        "git " + String.join(" ", args) + " exited " + process.exitValue());
            }
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not run git " + String.join(" ", args), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted running git", e);
        }
    }
}
