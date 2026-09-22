package za.co.titandynamix.sasbo_online_backend.enums;

/**
 * How a letter reaches its member -- the twin of V46's {@code acb_letters_channel_known} CHECK,
 * held to it by {@code AcbConstraintsIT} (R1-22).
 */
public enum LetterChannel {
    /** Rendered for an email address on record. */
    EMAIL,
    /** On paper. Nothing writes it: SASBO sends no post (Q46), and it leaves with {@code 03.3}'s letters. */
    POST
}
