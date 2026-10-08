package za.co.titandynamix.sasbo_online_backend.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollKeys;
import za.co.titandynamix.sasbo_online_backend.support.ApiSurface;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The payroll seed file is what its generator writes, byte for byte -- the generator and the checker are one class
 * run two ways, as the debit-order seed's are ({@code ./gradlew generateApiSurface} writes it).
 */
@DisplayName("The payroll seed file")
class PayrollSeedGeneratorTest {

    private static final Path FILE = Paths.get("src", "main", "resources", "data", "payroll_members.json");

    @Test
    @DisplayName("the committed file is what the generator writes")
    void theCommittedFileIsWhatTheGeneratorWrites() throws IOException {
        String generated = PayrollSeedGenerator.render();
        if (ApiSurface.writeRequested()) {
            Files.writeString(FILE, generated, StandardCharsets.UTF_8);
        }
        assertThat(Files.readString(FILE, StandardCharsets.UTF_8).replace("\r\n", "\n"))
                .as("%s is stale: regenerate with ./gradlew generateApiSurface", FILE)
                .isEqualTo(generated);
    }

    @Test
    @DisplayName("every member sits in payroll's block, M-095001 to M-095400, and is on payroll deduction")
    void theNumbersSitInPayrollsBlock() throws IOException {
        List<PayrollSeedMember> members = PayrollSeeder.readSeedFile().members();
        assertThat(members).isNotEmpty().allSatisfy(m -> {
            assertThat(m.membershipNumber()).isBetween("M-095001", "M-095400");
            assertThat(m.paymentType()).isEqualTo("Payroll Deduction");
        });
    }

    @Test
    @DisplayName("every identity number is a valid South African one, and none is another seed file's or a stranger's")
    void identityNumbersAreValidAndDistinct() throws IOException {
        PayrollSeedMember.SeedFile file = PayrollSeeder.readSeedFile();
        Set<String> seen = new HashSet<>();
        for (PayrollSeedMember member : file.members()) {
            assertThat(SaIdParser.isValidIdFormat(member.identityNumber())).as(member.membershipNumber()).isTrue();
            assertThat(seen.add(member.identityNumber())).isTrue();
        }
        for (PayrollSeedMember.Stranger stranger : file.strangers()) {
            assertThat(PayrollKeys.isSaId(stranger.identityNumber())).isTrue();
            assertThat(seen.add(stranger.identityNumber())).as("a stranger shares a member's ID number").isTrue();
        }
        for (String other : List.of("data/members.json", "data/debit_order_members.json",
                "data/demonstration_members.json")) {
            Set<String> theirs = new HashSet<>(new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(getClass().getClassLoader().getResourceAsStream(other)).findValuesAsText("identityNumber"));
            assertThat(seen).as("an identity number of %s", other).doesNotContainAnyElementsOf(theirs);
        }
    }

    @Test
    @DisplayName("every role the demonstration plants is there, and each employer's surnames are distinct")
    void everyRoleIsThere() throws IOException {
        List<PayrollSeedMember> members = PayrollSeeder.readSeedFile().members();
        assertThat(members.stream().map(PayrollSeedMember::role).collect(Collectors.toSet())).contains("RETRO",
                "MISSED_THEN_CAUGHT_UP", "DUPLICATE", "OLD_RATE", "INITIALS", "STAFF_NUMBER", "RESIGNED", "MISSING",
                "DUPLICATE_OWED", "CATCH_UP_ARREARS", "OVER_BY_MONTHS", "STAFF_BY_ID", "ID_DIFFERS", "NAME_BY_ID",
                "ADJUSTMENT_CATCH_UP", "ZERO", "ORDINARY");
        assertThat(members.stream().map(PayrollSeedMember::surname).distinct().count()).isEqualTo(members.size());
        assertThat(members.stream().map(PayrollSeedMember::employer).collect(Collectors.toSet()))
                .containsExactlyInAnyOrderElementsOf(PayrollSeeder.DEMONSTRATED);
    }
}
