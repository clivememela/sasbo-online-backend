package za.co.titandynamix.sasbo_online_backend.documents;

/**
 * A letter holds a character neither the typeface nor its fallback can print, so the letter is refused rather than
 * printed with a '?' in it (register A68).
 *
 * <p>🔒 It names the code point and nothing else: never the member, the name or the address the character came from
 * (register B8 is open because a failure once carried an address into a response).
 */
public final class UnprintableCharacterException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    private final int codePoint;

    public UnprintableCharacterException(int codePoint) {
        super(String.format("No letter font can print U+%04X", codePoint));
        this.codePoint = codePoint;
    }

    public int codePoint() {
        return codePoint;
    }
}
