package za.co.titandynamix.sasbo_online_backend.acb.file;

import java.util.ArrayList;
import java.util.List;

/**
 * Who the bureau file says is collecting: the union's identifiers with the collecting bank, and the
 * constants of its user set.
 *
 * <h2>The identifiers are configuration, never code -- and never in this repository</h2>
 * The user code, the user's branch, the nominated account and the bank-side identifier block are
 * the union's banking identifiers. They were read from the July 2026 file SASBO sent, and they are
 * supplied to the application from its environment (Render), like any other credential-shaped
 * value: nothing in the source carries them, no default stands in for them, and a file cannot be
 * rendered while any is missing -- {@link #missing()} names which, and the run says so rather than
 * printing a file with zeros where the union's account should be. SASBO's CFO has been asked to
 * confirm they are current (question 36).
 *
 * <h2>The constants are the July file's, measured</h2>
 * {@code 1001}, {@code 000018}, {@code BEFT}, {@code TWO DAY}: each read from the file Standard Bank
 * accepted, positions and all. Their NAMES are inferred -- the bank's layout document never
 * arrived -- which is why they are settings with the July values as defaults rather than literals
 * in the writer: the day the bank's document names them, a value changes and no code does.
 *
 * <p>🔒 {@link #toString()} is written by hand: a settings object in a log line would print the
 * union's account.
 *
 * @param userName          record 02, positions 7-12: {@code SASBO}
 * @param userCode          four digits: record 04 positions 3-6, and in every transaction record
 * @param userBranch        six digits: the branch of the union's own account
 * @param nominatedAccount  up to eleven digits: the union's own account, zero-filled in the file
 * @param bankIdentifiers   ten digits: record 02 positions 13-22 (inferred: the bank-side identifiers)
 * @param setNumber         four digits: record 02 positions 3-6 ({@code 1001} in July)
 * @param headerCounter     six digits: record 02 positions 35-40 ({@code 000018} in July; meaning unknown)
 * @param userReference     the first word of every member's statement line: {@code SASBO}
 * @param serviceType       record 04 positions 41-50: {@code TWO DAY}
 * @param fileLabel         record 02 positions 47-50: {@code BEFT}
 */
public record BureauFileSettings(
        String userName,
        String userCode,
        String userBranch,
        String nominatedAccount,
        String bankIdentifiers,
        String setNumber,
        String headerCounter,
        String userReference,
        String serviceType,
        String fileLabel) {

    /** Every setting that is absent or the wrong shape, by name. Empty means the file can be rendered. */
    public List<String> missing() {
        List<String> problems = new ArrayList<>();
        text(problems, "userName", userName, 6);
        digits(problems, "userCode", userCode, 4, 4);
        digits(problems, "userBranch", userBranch, 6, 6);
        digits(problems, "nominatedAccount", nominatedAccount, 1, 11);
        digits(problems, "bankIdentifiers", bankIdentifiers, 10, 10);
        digits(problems, "setNumber", setNumber, 4, 4);
        digits(problems, "headerCounter", headerCounter, 6, 6);
        text(problems, "userReference", userReference, 10);
        text(problems, "serviceType", serviceType, 10);
        text(problems, "fileLabel", fileLabel, 4);
        return problems;
    }

    private static void digits(List<String> problems, String name, String value, int min, int max) {
        if (value == null || value.isBlank()) {
            problems.add(name + " is not configured");
        } else if (!value.matches("[0-9]{" + min + "," + max + "}")) {
            problems.add(name + " must be " + (min == max ? min : min + " to " + max) + " digits");
        }
    }

    private static void text(List<String> problems, String name, String value, int max) {
        if (value == null || value.isBlank()) {
            problems.add(name + " is not configured");
        } else if (value.length() > max || !value.chars().allMatch(c -> c >= 0x20 && c < 0x7F)) {
            problems.add(name + " must be at most " + max + " printable ASCII characters");
        }
    }

    @Override
    public String toString() {
        return "BureauFileSettings[userName=" + userName + ", configured=" + missing().isEmpty() + "]";
    }
}
