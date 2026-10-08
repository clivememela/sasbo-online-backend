package za.co.titandynamix.sasbo_online_backend.legacy.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One problem with a row: its code and the field it is about -- a member field's name, or a column of the file for a
 * problem met while reading it. Stored as a token, {@code CODE@field}, in a comma-separated list. Never a value.
 */
public record Problem(RuleCode code, String field) {

    public Problem {
        Objects.requireNonNull(code, "code");
        if (field != null && (field.isBlank() || field.indexOf(',') >= 0 || field.indexOf('@') >= 0)) {
            // A column name is the file's own text: one that would break the token list is named by its code alone.
            field = null;
        }
    }

    /** The token as stored: {@code CODE} or {@code CODE@field}. */
    public String token() {
        return field == null ? code.name() : code.name() + "@" + field;
    }

    /** A token back to its problem; one this version does not know is dropped, never guessed at. */
    public static Problem parse(String token) {
        int at = token.indexOf('@');
        String name = at < 0 ? token.trim() : token.substring(0, at).trim();
        try {
            return new Problem(RuleCode.valueOf(name), at < 0 ? null : token.substring(at + 1));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    /** A stored list back to its problems, in order. */
    public static List<Problem> parseAll(String tokens) {
        List<Problem> problems = new ArrayList<>();
        if (tokens == null || tokens.isBlank()) {
            return problems;
        }
        for (String token : tokens.split(",")) {
            Problem problem = parse(token);
            if (problem != null) {
                problems.add(problem);
            }
        }
        return problems;
    }

    /**
     * Problems as a stored list, each once, cut short (whole tokens only) at {@code maxChars}: a row with that many
     * problems is held back by the first of them anyway. {@code null} for none.
     */
    public static String joinAll(List<Problem> problems, int maxChars) {
        if (problems == null || problems.isEmpty()) {
            return null;
        }
        StringBuilder joined = new StringBuilder();
        List<String> seen = new ArrayList<>();
        for (Problem problem : problems) {
            String token = problem.token();
            if (seen.contains(token)) {
                continue;
            }
            int added = (joined.isEmpty() ? 0 : 1) + token.length();
            if (joined.length() + added > maxChars) {
                break;
            }
            if (!joined.isEmpty()) {
                joined.append(',');
            }
            joined.append(token);
            seen.add(token);
        }
        return joined.isEmpty() ? null : joined.toString();
    }
}
