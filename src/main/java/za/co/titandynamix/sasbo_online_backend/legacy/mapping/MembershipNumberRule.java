package za.co.titandynamix.sasbo_online_backend.legacy.mapping;

import java.util.regex.Pattern;

/**
 * How the old system's membership numbers become ours.
 *
 * <p>Members know their number, and letters carry it, so a number is KEPT -- written as {@code prefix + digits}, the
 * form capture gives ({@code M-100161}), with its leading noughts dropped: the old system holds them as whole numbers.
 * After a move the capture sequence is moved past the highest number written, so a capture never takes one.
 *
 * @param prefix   what our numbers begin with -- {@code M-}; a number that already carries it is read without it
 * @param pattern  what the old number must look like, without the prefix: {@code [0-9]{1,9}} unless the mapping says
 * @param allocate where the file carries no numbers at all: give each member the next of ours instead
 */
public record MembershipNumberRule(String prefix, Pattern pattern, boolean allocate) {

    public static final String DEFAULT_PREFIX = "M-";

    public static final String DEFAULT_PATTERN = "[0-9]{1,9}";

    public static MembershipNumberRule defaults() {
        return new MembershipNumberRule(DEFAULT_PREFIX, Pattern.compile(DEFAULT_PATTERN), false);
    }
}
