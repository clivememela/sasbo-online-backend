package za.co.titandynamix.sasbo_online_backend.guards;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.security.Permission;
import za.co.titandynamix.sasbo_online_backend.support.MainSourceInventory;
import za.co.titandynamix.sasbo_online_backend.support.MainSourceInventory.MainClassSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static za.co.titandynamix.sasbo_online_backend.support.NonVacuousScan.assertNonVacuousScan;

/**
 * Every {@code @PreAuthorize} in the main sources names real permissions (contracts/permissions.md).
 *
 * <h2>Why a guard</h2>
 * {@code hasAuthority('MEMBER_VIEW')} -- one letter short -- compiles, starts and refuses EVERY caller for ever: no
 * role can hold a name the catalogue does not have, so the route is dead and nothing says why. Twenty streams write
 * these annotations at once. So every literal is held to {@link Permission} here, by reading the sources.
 *
 * <p>What an expression may be: one that names catalogue permissions with {@code hasAuthority} or
 * {@code hasAnyAuthority} (alone, or beside {@code isAnonymous()}), or {@code isAuthenticated()} on its own. Never a
 * {@code hasRole} -- this system grants authorities, not roles -- and never an expression built by concatenation, which
 * no scan can read.
 */
@DisplayName("Every @PreAuthorize names a real permission")
class PermissionCatalogueTest {

    /** An annotation and its expression, a single string literal. */
    static final Pattern PRE_AUTHORIZE = Pattern.compile("@PreAuthorize\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*\\)");

    /** Any {@code @PreAuthorize(} at all -- a literal the pattern above cannot read is still counted. */
    static final Pattern ANY_PRE_AUTHORIZE = Pattern.compile("@PreAuthorize\\(");

    private static final Pattern AUTHORITY_CALL = Pattern.compile("has(?:Any)?Authority\\(([^)]*)\\)");
    private static final Pattern LITERAL = Pattern.compile("'([^']*)'");
    private static final Pattern ROLE_CALL = Pattern.compile("has(?:Any)?Role\\(");

    private static final Set<String> CATALOGUE = Arrays.stream(Permission.values()).map(Enum::name)
            .collect(Collectors.toSet());

    /** Every expression of the main sources, each with where it is. */
    record Expression(String where, String text) {
    }

    static List<Expression> expressions(List<MainClassSource> sources, List<String> unreadable) {
        List<Expression> found = new ArrayList<>();
        for (MainClassSource source : sources) {
            Matcher literal = PRE_AUTHORIZE.matcher(source.code());
            int read = 0;
            while (literal.find()) {
                found.add(new Expression(source.name(), literal.group(1)));
                read++;
            }
            Matcher any = ANY_PRE_AUTHORIZE.matcher(source.code());
            int all = 0;
            while (any.find()) {
                all++;
            }
            if (all != read) {
                unreadable.add(source.name() + " has " + (all - read) + " @PreAuthorize whose expression is not one "
                        + "string literal");
            }
        }
        return found;
    }

    /** What is wrong with an expression, or {@code null}. */
    static String wrongWith(String expression) {
        if (ROLE_CALL.matcher(expression).find()) {
            return "names a role -- this system grants authorities (permissions), never roles";
        }
        List<String> named = new ArrayList<>();
        Matcher call = AUTHORITY_CALL.matcher(expression);
        while (call.find()) {
            Matcher literal = LITERAL.matcher(call.group(1));
            while (literal.find()) {
                named.add(literal.group(1));
            }
        }
        List<String> unknown = named.stream().filter(name -> !CATALOGUE.contains(name)).toList();
        if (!unknown.isEmpty()) {
            return "names " + unknown + ", which security.Permission does not have";
        }
        if (named.isEmpty() && !expression.trim().equals("isAuthenticated()")) {
            return "names no permission -- write hasAuthority('<PERMISSION>'), or isAuthenticated() alone";
        }
        return null;
    }

    @Test
    @DisplayName("🔒 every expression names permissions the catalogue has, and no role")
    void everyExpressionNamesRealPermissions() {
        List<MainClassSource> sources = MainSourceInventory.all();
        assertNonVacuousScan(sources, 100, "main sources");
        List<String> unreadable = new ArrayList<>();
        List<Expression> expressions = expressions(sources, unreadable);
        assertNonVacuousScan(expressions, 10, "@PreAuthorize expressions");
        List<String> wrong = new ArrayList<>(unreadable);
        for (Expression expression : expressions) {
            String why = wrongWith(expression.text());
            if (why != null) {
                wrong.add(expression.where() + ": \"" + expression.text() + "\" " + why);
            }
        }
        assertThat(wrong).as("a @PreAuthorize no role can satisfy refuses every caller for ever").isEmpty();
    }

    @Test
    @DisplayName("canary: a misspelt permission, an unknown one beside a real one, a role, nothing at all -- each caught")
    void thePredicateCanFail() {
        assertThat(wrongWith("hasAuthority('MEMBER_VIEW')")).contains("MEMBER_VIEW");
        assertThat(wrongWith("hasAnyAuthority('USERS_ADMIN', 'USER_ADMIN')")).contains("USER_ADMIN");
        assertThat(wrongWith("hasRole('ADMIN')")).contains("role");
        assertThat(wrongWith("permitAll()")).contains("names no permission");
        assertThat(wrongWith("hasAuthority('MONTH_END_TAKE')")).isNull();
        assertThat(wrongWith("isAnonymous() or hasAuthority('MEMBERS_VIEW')")).isNull();
        assertThat(wrongWith("isAuthenticated()")).isNull();
    }

    @Test
    @DisplayName("canary: the reader finds an annotation, skips one in a comment, and counts one it cannot read")
    void theReaderCanFail() {
        String source = MainSourceInventory.blankComments("""
                class X {
                    // @PreAuthorize("hasAuthority('IN_A_COMMENT')")
                    @PreAuthorize("hasAuthority('USERS_ADMIN')")
                    void a() { }
                    @PreAuthorize("hasAuthority('" + NAME + "')")
                    void b() { }
                }
                """);
        List<String> unreadable = new ArrayList<>();
        List<Expression> found = expressions(List.of(new MainClassSource(java.nio.file.Path.of("X.java"), "X",
                MainSourceInventory.Layer.OTHER, source)), unreadable);
        assertThat(found).extracting(Expression::text).containsExactly("hasAuthority('USERS_ADMIN')");
        assertThat(unreadable).singleElement().asString().contains("not one string literal");
    }
}
