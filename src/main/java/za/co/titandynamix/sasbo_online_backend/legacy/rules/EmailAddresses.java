package za.co.titandynamix.sasbo_online_backend.legacy.rules;

import java.util.regex.Pattern;

/**
 * An e-mail address's shape -- the capture screen's own rule ({@code validateEmail}: something, an at sign, something,
 * a dot, something, and no spaces), so a moved address is one the screen would have accepted. At most 254 characters.
 */
public final class EmailAddresses {

    private static final Pattern SHAPE = Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");

    private EmailAddresses() {
    }

    public static boolean valid(String address) {
        return address != null && address.length() <= 254 && SHAPE.matcher(address).matches();
    }
}
