package za.co.titandynamix.sasbo_online_backend.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import za.co.titandynamix.sasbo_online_backend.exception.SignInRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.SignInRefusalCode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sign-in's rules that need nothing but themselves, each named for the rule it holds (contracts/signin.md).
 */
@DisplayName("Sign-in's rules")
class SignInRulesTest {

    @Nested
    @DisplayName("the mode, SASBO_SIGNIN")
    class Mode {

        @Test
        @DisplayName("unset is open: the link keeps working while SASBO has no accounts")
        void unsetIsOpen() {
            assertThat(SignInMode.parse(null)).isEqualTo(SignInMode.OPEN);
            assertThat(SignInMode.parse("  ")).isEqualTo(SignInMode.OPEN);
        }

        @Test
        @DisplayName("the two words, trimmed and in any case, mean what they say")
        void theTwoWords() {
            assertThat(SignInMode.parse("open")).isEqualTo(SignInMode.OPEN);
            assertThat(SignInMode.parse(" Required ")).isEqualTo(SignInMode.REQUIRED);
            assertThat(SignInMode.REQUIRED.word()).isEqualTo("required");
        }

        @ParameterizedTest
        @ValueSource(strings = {"requird", "closed", "true", "on", "required."})
        @DisplayName("🔴 anything else stops the start -- a misspelt mode would leave every route open while believed closed")
        void aTypoStopsTheStart(String typo) {
            assertThatThrownBy(() -> SignInMode.parse(typo))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("'open', 'required'");
            assertThatThrownBy(() -> new SignInProperties(typo, false, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("session tokens")
    class Tokens {

        @Test
        @DisplayName("256 random bits, base64url without padding: 43 characters, never the same twice")
        void aTokenIs256RandomBits() {
            SecureRandom random = new SecureRandom();
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < 200; i++) {
                String token = SessionTokens.newToken(random);
                assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]{43}");
                assertThat(SessionTokens.wellFormed(token)).isTrue();
                seen.add(token);
            }
            assertThat(seen).hasSize(200);
        }

        @Test
        @DisplayName("the server keeps the token's SHA-256 as 64 hex digits -- never the token")
        void theHashIsSha256() throws Exception {
            String token = SessionTokens.newToken(new SecureRandom());
            String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII)));
            assertThat(SessionTokens.hash(token)).isEqualTo(expected).matches("[0-9a-f]{64}").isNotEqualTo(token);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "short", "has spaces in it and is long enough to be forty three",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
        @DisplayName("anything not of the shape is never looked up")
        void aMalformedTokenIsNotWellFormed(String token) {
            assertThat(SessionTokens.wellFormed(token)).isFalse();
            assertThat(SessionTokens.wellFormed(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("password rules")
    class Passwords {

        @Test
        @DisplayName("at least ten characters")
        void atLeastTen() {
            assertThat(PasswordRules.broken("invented.user", "nine char")).isEqualTo(PasswordRules.Rule.TOO_SHORT);
            assertThat(PasswordRules.broken("invented.user", "ten chars!")).isNull();
            assertThat(PasswordRules.broken("invented.user", null)).isEqualTo(PasswordRules.Rule.TOO_SHORT);
        }

        @Test
        @DisplayName("not the username, in any case or with spaces around it")
        void notTheUsername() {
            assertThat(PasswordRules.broken("invented.user", "Invented.User")).isEqualTo(PasswordRules.Rule.SAME_AS_USERNAME);
            assertThat(PasswordRules.broken("invented.user", " invented.user ")).isEqualTo(PasswordRules.Rule.SAME_AS_USERNAME);
        }

        @Test
        @DisplayName("not one of the most common -- SASBO's own name among them, in any case")
        void notACommonOne() {
            assertThat(PasswordRules.broken("invented.user", "Password123")).isEqualTo(PasswordRules.Rule.TOO_COMMON);
            assertThat(PasswordRules.broken("invented.user", "SASBO12345")).isEqualTo(PasswordRules.Rule.TOO_COMMON);
            assertThat(PasswordRules.COMMON).allSatisfy(common -> assertThat(common).hasSizeGreaterThanOrEqualTo(10)
                    .isEqualTo(common.toLowerCase(java.util.Locale.ROOT)));
        }

        @Test
        @DisplayName("at most 72 bytes, where BCrypt stops reading -- counted in bytes, not characters")
        void atMost72Bytes() {
            assertThat(PasswordRules.broken("invented.user", "a".repeat(72))).isNull();
            assertThat(PasswordRules.broken("invented.user", "a".repeat(73))).isEqualTo(PasswordRules.Rule.TOO_LONG);
            assertThat(PasswordRules.broken("invented.user", "é".repeat(37))).isEqualTo(PasswordRules.Rule.TOO_LONG);
        }

        @Test
        @DisplayName("a broken rule is PASSWORD_RULES naming the rule, and never the password")
        void theRefusalNamesTheRule() {
            assertThatThrownBy(() -> PasswordRules.check("invented.user", "letmein123"))
                    .isInstanceOfSatisfying(SignInRefusal.class, refusal -> {
                        assertThat(refusal.code()).isEqualTo(SignInRefusalCode.PASSWORD_RULES);
                        assertThat(refusal.details()).containsEntry("rule", "TOO_COMMON");
                        assertThat(refusal.getMessage()).doesNotContain("letmein123");
                        assertThat(refusal.details().values()).doesNotContain("letmein123");
                    });
        }
    }

    @Nested
    @DisplayName("temporary passwords")
    class Temporary {

        @Test
        @DisplayName("four groups of four, no look-alikes, never the same twice -- and every one keeps the rules")
        void temporaryPasswordsKeepTheRules() {
            SecureRandom random = new SecureRandom();
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < 500; i++) {
                String temporary = TemporaryPasswords.generate(random);
                assertThat(temporary).matches("([A-Za-z2-9]{4}-){3}[A-Za-z2-9]{4}");
                assertThat(temporary).doesNotContain("0", "O", "o", "1", "l", "I");
                assertThat(PasswordRules.broken("invented.user", temporary)).isNull();
                seen.add(temporary);
            }
            assertThat(seen).hasSize(500);
            assertThat(TemporaryPasswords.ALPHABET).hasSize(56).doesNotContain("0", "O", "o", "1", "l", "I");
        }
    }

    @Nested
    @DisplayName("grants -- down the hierarchy, never up (5 Oct)")
    class Within {

        private final Map<String, String> areaRegions = Map.of("L", "DBN", "W", "DBN", "A", "JHB");

        @Test
        @DisplayName("everything gives anything")
        void allGivesAnything() {
            assertThat(Grants.within(CallerScope.all(), CallerScope.all(), areaRegions)).isTrue();
            assertThat(Grants.within(CallerScope.areas(Set.of("A")), CallerScope.all(), areaRegions)).isTrue();
        }

        @Test
        @DisplayName("a region gives itself and its own areas, never everything nor another region's area")
        void aRegionGivesItsOwn() {
            CallerScope durban = CallerScope.regions(Set.of("DBN"));
            assertThat(Grants.within(CallerScope.regions(Set.of("DBN")), durban, areaRegions)).isTrue();
            assertThat(Grants.within(CallerScope.areas(Set.of("L", "W")), durban, areaRegions)).isTrue();
            assertThat(Grants.within(CallerScope.areas(Set.of("A")), durban, areaRegions)).isFalse();
            assertThat(Grants.within(CallerScope.regions(Set.of("JHB")), durban, areaRegions)).isFalse();
            assertThat(Grants.within(CallerScope.all(), durban, areaRegions)).isFalse();
            assertThat(Grants.within(CallerScope.areas(Set.of("Y")), durban, areaRegions)).as("an area no region claims")
                    .isFalse();
        }

        @Test
        @DisplayName("an area gives its own areas only")
        void anAreaGivesItsOwn() {
            CallerScope l = CallerScope.areas(Set.of("L"));
            assertThat(Grants.within(CallerScope.areas(Set.of("L")), l, areaRegions)).isTrue();
            assertThat(Grants.within(CallerScope.areas(Set.of("L", "W")), l, areaRegions)).isFalse();
            assertThat(Grants.within(CallerScope.regions(Set.of("DBN")), l, areaRegions)).isFalse();
        }
    }

    @Nested
    @DisplayName("the challenge reads the permission off the refused expression")
    class Challenge {

        @Test
        @DisplayName("one permission named is the one; two, or none, name nothing")
        void oneIsNamedTwoAreNot() {
            assertThat(SignInChallenge.permissionsIn("hasAuthority('MONTH_END_TAKE')")).containsExactly("MONTH_END_TAKE");
            assertThat(SignInChallenge.permissionsIn("isAnonymous() or hasAuthority('MEMBERS_VIEW')"))
                    .containsExactly("MEMBERS_VIEW");
            assertThat(SignInChallenge.permissionsIn("hasAnyAuthority('DOD_FILE', 'DOD_PREPARE')"))
                    .containsExactly("DOD_FILE", "DOD_PREPARE");
            assertThat(SignInChallenge.permissionsIn("isAuthenticated()")).isEmpty();
            assertThat(SignInChallenge.permissionsIn("hasAuthority('NOT_IN_THE_CATALOGUE')")).isEmpty();
            assertThat(SignInChallenge.permissionsIn(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("the settings print no secret")
    class Settings {

        @Test
        @DisplayName("🔒 neither password appears when the settings are printed")
        void noPasswordIsPrinted() {
            SignInProperties properties = new SignInProperties("open", true, "an-invented-demo-phrase",
                    new SignInProperties.BootstrapAdmin("invented.first", "an-invented-first-phrase"));
            assertThat(properties.toString()).doesNotContain("an-invented-demo-phrase", "an-invented-first-phrase",
                    "invented.first").contains("demoPassword=set");
            assertThat(properties.bootstrapAdmin().configured()).isTrue();
        }

        @Test
        @DisplayName("half a bootstrap is said to be half; none is neither")
        void halfABootstrap() {
            assertThat(new SignInProperties.BootstrapAdmin("invented.first", null).halfConfigured()).isTrue();
            assertThat(new SignInProperties.BootstrapAdmin(null, "a-phrase").halfConfigured()).isTrue();
            assertThat(new SignInProperties.BootstrapAdmin(null, null).halfConfigured()).isFalse();
            assertThat(new SignInProperties(null, false, null, null).bootstrapAdmin().configured()).isFalse();
        }
    }
}
