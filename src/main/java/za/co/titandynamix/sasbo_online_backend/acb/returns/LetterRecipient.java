package za.co.titandynamix.sasbo_online_backend.acb.returns;

import java.util.ArrayList;
import java.util.List;

/**
 * What a letter needs of the member that the unpaid line did not snapshot: how to greet them, where
 * they live, and where to email them. Read at the moment the letter is rendered, and the address it
 * went to is kept on the letter.
 *
 * @param preferredEmail which address the member prefers -- {@code work} or {@code personal}
 */
public record LetterRecipient(Integer memberId, String title, String address, String personalEmail,
                              String workEmail, String preferredEmail, String areaName) {

    /** The address to email: the member's preferred one, else the other, else none. */
    public String email() {
        boolean preferWork = "work".equalsIgnoreCase(trim(preferredEmail));
        String first = trim(preferWork ? workEmail : personalEmail);
        String second = trim(preferWork ? personalEmail : workEmail);
        if (!first.isEmpty()) {
            return first;
        }
        return second.isEmpty() ? null : second;
    }

    /**
     * 🔒 Whether {@code address} is ONE address a letter may go to (SD3-C): no carriage return, line feed,
     * comma, semicolon, space or angle bracket -- nothing that could add a header or a second recipient.
     */
    public static boolean sendable(String address) {
        return address != null && address.length() <= 254
                && address.matches("[^\\s@,;<>\\r\\n]+@[^\\s@,;<>\\r\\n]+\\.[^\\s@,;<>\\r\\n]+");
    }

    /** The member's address as the letter's address block: up to three lines, split at commas. */
    public List<String> addressLines() {
        List<String> lines = new ArrayList<>();
        if (address != null) {
            for (String part : address.split("[,\\n]")) {
                if (!part.isBlank() && lines.size() < 3) {
                    lines.add(part.trim());
                }
            }
        }
        return lines;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    @Override
    public String toString() {
        return "LetterRecipient[memberId=" + memberId + "]";
    }
}
