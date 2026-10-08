package za.co.titandynamix.sasbo_online_backend.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * How far a caller may see: everything, some regions, or some areas.
 *
 * <p>The 5 Oct Show &amp; Tell asked for "region and area limits": a Regional Secretary sees their region, an organiser
 * their area, head office everything (Stephne's answers, 6 Oct: each region gets only its own information). A member
 * is in scope when the area of their workplace, or that area's region, is one of the codes. <b>A member with no
 * workplace is in scope only for {@link Type#ALL}</b> -- a limited user is never shown a member nobody has placed.
 *
 * <p>Codes are {@code sasbo_regions.code} and {@code sasbo_areas.letter}. Queries that list members filter with this
 * IN SQL -- a {@code WHERE} on the workplace's area or region -- never by loading everything and filtering here.
 *
 * <p>Until sign-in is in force ({@code sasbo.signin.mode=open}), an anonymous caller is given {@link #all()}, which is
 * what every listing shows today; the sign-in filter decides what an anonymous caller may reach at all.
 */
public record CallerScope(Type type, Set<String> codes) {

    /** The three kinds of limit a user can carry. */
    public enum Type { ALL, REGION, AREA }

    private static final CallerScope ALL = new CallerScope(Type.ALL, Set.of());

    public CallerScope {
        Objects.requireNonNull(type, "type");
        codes = codes == null ? Set.of() : codes.stream()
                .filter(Objects::nonNull)
                .map(code -> code.trim().toUpperCase(Locale.ROOT))
                .filter(code -> !code.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        if (type == Type.ALL && !codes.isEmpty()) {
            throw new IllegalArgumentException("A scope of everything carries no codes");
        }
        if (type != Type.ALL && codes.isEmpty()) {
            throw new IllegalArgumentException("A " + type + " scope needs at least one code");
        }
    }

    /** No limit: head office, and every anonymous caller while sign-in is open. */
    public static CallerScope all() {
        return ALL;
    }

    public static CallerScope regions(Set<String> regionCodes) {
        return new CallerScope(Type.REGION, regionCodes);
    }

    public static CallerScope areas(Set<String> areaLetters) {
        return new CallerScope(Type.AREA, areaLetters);
    }

    /** The scope of the caller on this thread: the signed-in user's, or {@link #all()} when there is none. */
    public static CallerScope current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof ScopedPrincipal principal) {
            return principal.scope();
        }
        return ALL;
    }

    public boolean isAll() {
        return type == Type.ALL;
    }

    /** The region codes of a {@link Type#REGION} scope; empty otherwise. */
    public Set<String> regionCodes() {
        return type == Type.REGION ? codes : Set.of();
    }

    /** The area letters of a {@link Type#AREA} scope; empty otherwise. */
    public Set<String> areaLetters() {
        return type == Type.AREA ? codes : Set.of();
    }

    /**
     * Whether a member at a workplace in this area (and that area's region) is in scope. Either may be null: a member
     * with no workplace, or an area no region claims, is in scope only for {@link Type#ALL}.
     */
    public boolean allows(String areaLetter, String regionCode) {
        return switch (type) {
            case ALL -> true;
            case AREA -> areaLetter != null && codes.contains(areaLetter.trim().toUpperCase(Locale.ROOT));
            case REGION -> regionCode != null && codes.contains(regionCode.trim().toUpperCase(Locale.ROOT));
        };
    }
}
