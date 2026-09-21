package za.co.titandynamix.sasbo_online_backend.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.enums.AcbExclusionReason;
import za.co.titandynamix.sasbo_online_backend.support.ApiSurface;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code data/debit_order_members.json} is exactly what its generator writes, and is the population it
 * says it is (R0-10, T2-D).
 *
 * <p>The generator and the checker are one class run two ways, like the API surface and the window
 * fixture: {@code ./gradlew generateApiSurface} writes the file, the fast lane asserts the committed
 * bytes. Nobody hand-edits one of 150 members; a hand edit fails here, which is the point.
 */
@DisplayName("data/debit_order_members.json -- the seed's debit-order population, generated")
class DebitOrderSeedGeneratorTest {

    @Test
    @DisplayName("the committed file is exactly what the generator writes")
    void theCommittedFileIsWhatTheGeneratorWrites() {
        String live = DebitOrderSeedGenerator.render();
        if (ApiSurface.writeRequested()) {
            ApiSurface.write(DebitOrderSeedGenerator.FILE, live);
        }
        assertThat(ApiSurface.readCommittedNormalised(DebitOrderSeedGenerator.FILE))
                .as("%s is STALE or hand-edited: it is generated. Regenerate with %s and commit the diff",
                        DebitOrderSeedGenerator.FILE, ApiSurface.regenerateCommand())
                .isEqualTo(live);
    }

    @Test
    @DisplayName("it carries LF endings on every platform -- pinned by name in .gitattributes (F58)")
    void theFileHasUnixEndings() {
        assertThat(Files.isRegularFile(DebitOrderSeedGenerator.FILE))
                .as("%s does not exist. Run %s", DebitOrderSeedGenerator.FILE, ApiSurface.regenerateCommand())
                .isTrue();
        assertThat(ApiSurface.readCommitted(DebitOrderSeedGenerator.FILE)).doesNotContain("\r");
    }

    @Test
    @DisplayName("150 members, numbered M-099851 to M-100000 -- all below where the capture sequence starts (F48)")
    void theNumbersSitBelowTheSeedsBlock() throws IOException {
        List<String> numbers = read().stream().map(SeedDebitOrderMember::membershipNumber).toList();
        assertThat(numbers).hasSize(DebitOrderSeedGenerator.LAST_NUMBER - DebitOrderSeedGenerator.FIRST_NUMBER + 1)
                .doesNotHaveDuplicates()
                .allSatisfy(n -> assertThat(n).matches("M-\\d{6}"));
        List<Integer> values = numbers.stream().map(n -> Integer.parseInt(n.substring(2))).toList();
        assertThat(values).allSatisfy(v -> assertThat(v)
                .as("a seeded number at or above 100161 collides with the first real capture (V22, F48)")
                .isBetween(DebitOrderSeedGenerator.FIRST_NUMBER, DebitOrderSeedGenerator.LAST_NUMBER)
                .isLessThan(100_161));
    }

    @Test
    @DisplayName("every address is on a reserved domain, or absent -- nothing seeded can reach a real inbox (F62)")
    void everyAddressIsReservedOrAbsent() throws IOException {
        List<SeedDebitOrderMember> members = read();
        assertThat(members).filteredOn(m -> m.personalEmail() != null)
                .allSatisfy(m -> assertThat(m.personalEmail()).endsWith("@" + DebitOrderSeedGenerator.MAIL_DOMAIN));
        assertThat(members).filteredOn(m -> m.personalEmail() == null)
                .as("some members have no address, so a held letter (NO_EMAIL) has somebody real behind it")
                .isNotEmpty();
    }

    @Test
    @DisplayName("every account number carries the documented prefix, and every identity number passes the capture screen's check")
    void accountsAreMarkedAndIdentityNumbersAreValid() throws IOException {
        List<SeedDebitOrderMember> members = read();
        assertThat(members).allSatisfy(m -> assertThat(m.bankAccountNumber()).startsWith(DebitOrderSeedGenerator.ACCOUNT_PREFIX));
        Set<String> identityNumbers = new HashSet<>();
        for (SeedDebitOrderMember m : members) {
            String id = m.identityNumber();
            assertThat(id).as("%s's identity number", m.membershipNumber()).matches("\\d{13}");
            assertThat(DebitOrderSeedGenerator.luhnCheckDigit(id.substring(0, 12)))
                    .as("%s's check digit, as validateSAID computes it", m.membershipNumber())
                    .isEqualTo(id.charAt(12) - '0');
            boolean female = Integer.parseInt(id.substring(6, 10)) < 5000;
            assertThat(m.gender()).isEqualTo(female ? "Female" : "Male");
            assertThat(identityNumbers.add(id)).as("identity numbers are unique (V26)").isTrue();
        }
    }

    @Test
    @DisplayName("SASBO's shape: at least six banks, Standard Bank about half, and every one of the six due dates carries lines")
    void thePopulationLooksLikeSasbos() throws IOException {
        List<SeedDebitOrderMember> qualifying = read().stream()
                .filter(m -> m.expectedOutcome().equals(DebitOrderSeedGenerator.QUALIFIES)).toList();
        Map<String, Long> byBank = qualifying.stream()
                .collect(Collectors.groupingBy(SeedDebitOrderMember::bankName, Collectors.counting()));
        assertThat(byBank).hasSizeGreaterThanOrEqualTo(6);
        assertThat(byBank.get("The Standard Bank of South Africa Limited") * 100 / qualifying.size())
                .as("Standard Bank's share, in per cent").isBetween(40L, 60L);
        assertThat(qualifying.stream().map(SeedDebitOrderMember::debitDay).collect(Collectors.toSet()))
                .containsExactlyInAnyOrderElementsOf(DebitOrderSeedGenerator.DUE_DATES);
    }

    @Test
    @DisplayName("every listed exclusion reason has a member behind it, and Nedbank's are the ones SASBO does not collect")
    void everyListedReasonIsSeeded() throws IOException {
        List<SeedDebitOrderMember> members = read();
        Set<String> outcomes = members.stream().map(SeedDebitOrderMember::expectedOutcome).collect(Collectors.toSet());
        Set<String> listed = Arrays.stream(AcbExclusionReason.values()).filter(AcbExclusionReason::listed)
                .map(Enum::name).collect(Collectors.toSet());
        assertThat(listed).isNotEmpty();
        assertThat(outcomes).containsAll(listed);
        assertThat(members).filteredOn(m -> m.bankName().equals("Nedbank Limited")).isNotEmpty()
                .allSatisfy(m -> assertThat(m.expectedOutcome()).isEqualTo(AcbExclusionReason.NO_SUBSCRIPTION_ROW.name()));
        assertThat(members).allSatisfy(m -> {
            assertThat(m.status()).isEqualTo("Active");
            assertThat(m.paymentType()).isEqualTo("Debit Order");
        });
    }

    @Test
    @DisplayName("🔴 F65: every key in the file is a property Member stores -- a misnamed key would be a field no member keeps")
    void everyKeyIsAMemberProperty() {
        Set<String> memberFields = Arrays.stream(Member.class.getDeclaredFields()).map(f -> f.getName())
                .collect(Collectors.toSet());
        List<String> notStored = Arrays.stream(SeedDebitOrderMember.class.getRecordComponents())
                .map(RecordComponent::getName)
                .filter(name -> !name.equals("expectedOutcome"))
                .filter(name -> !memberFields.contains(name))
                .toList();
        assertThat(notStored).as("keys the member entity has no field for -- they would be dropped").isEmpty();
        // Non-vacuity: the comparison saw the whole record.
        assertThat(SeedDebitOrderMember.class.getRecordComponents()).hasSizeGreaterThan(25);
    }

    /** Read as the application reads it: strictly, so a key the DTO does not know fails here first. */
    private static List<SeedDebitOrderMember> read() throws IOException {
        List<SeedDebitOrderMember> members = DebitOrderMemberSeeder.readSeedFile().members();
        assertThat(members).as("the seed file was actually read").isNotEmpty();
        return members;
    }
}
