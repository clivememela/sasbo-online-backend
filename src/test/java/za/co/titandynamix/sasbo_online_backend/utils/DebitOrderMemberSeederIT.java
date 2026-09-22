package za.co.titandynamix.sasbo_online_backend.utils;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.repository.SeedMemberRepository;
import za.co.titandynamix.sasbo_online_backend.support.AbstractWritingIT;
import za.co.titandynamix.sasbo_online_backend.support.QueryCount;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The debit-order population is seeded WHEN ABSENT -- on an empty database and on one that already
 * holds the original 160 alike -- once, however many times the application starts (R0-8, D33 as
 * amended); and what the database STORED is what the file says (R0-10, F65).
 *
 * <p>{@code AbstractPostgresIT} replaces {@code DataSeeder} with a mock, so the seeder bean is called
 * directly -- twice, to prove "once", against one real PostgreSQL (the pattern {@code JobGradeSeederIT}
 * set). No count is typed: every expectation is read from the seed file.
 */
@DisplayName("DebitOrderMemberSeeder -- the debit-order population, when absent, once")
class DebitOrderMemberSeederIT extends AbstractWritingIT {

    @Autowired
    private DebitOrderMemberSeeder seeder;

    @Autowired
    private MemberRepository members;

    @Autowired
    private SeedMemberRepository seedMembers;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private static List<SeedDebitOrderMember> file() throws Exception {
        List<SeedDebitOrderMember> rows = DebitOrderMemberSeeder.readSeedFile().members();
        assertThat(rows).as("the seed file was read").isNotEmpty();
        return rows;
    }

    @Test
    @DisplayName("an empty database: every member is seeded, and a second start adds nobody")
    void anEmptyDatabaseIsSeededOnce() throws Exception {
        int expected = file().size();
        assertThat(seeder.seed()).isEqualTo(expected);
        assertThat(seeder.seed()).as("the second start").isZero();
        assertThat(members.count()).isEqualTo(expected);
    }

    @Test
    @DisplayName("a database that already holds the original 160 -- dev.sasbo.online's case -- still receives them, once")
    void aDatabaseStartedLongAgoStillReceivesThem() throws Exception {
        List<Member> original;
        try (InputStream stream = new ClassPathResource("data/members.json").getInputStream()) {
            original = DataSeeder.readSeedMembers(stream);
        }
        original.forEach(m -> {
            m.setId(null);
            m.setJobGrade(null);
        });
        members.saveAll(original);
        long before = members.count();
        assertThat(before).as("the original seed is there first").isEqualTo(original.size());

        int expected = file().size();
        assertThat(seeder.seed()).isEqualTo(expected);
        assertThat(seeder.seed()).as("the second start").isZero();
        assertThat(members.count()).isEqualTo(before + expected);
    }

    @Test
    @DisplayName("🔴 F65: every field the file states is the field the database stored, member by member")
    void whatWasStoredIsWhatTheFileSays() throws Exception {
        seeder.seed();
        Map<String, Member> stored = members.findAll().stream()
                .collect(Collectors.toMap(Member::getMembershipNumber, Function.identity()));
        int compared = 0;
        for (SeedDebitOrderMember row : file()) {
            Member member = stored.get(row.membershipNumber());
            assertThat(member).as("%s on record", row.membershipNumber()).isNotNull();
            for (RecordComponent component : SeedDebitOrderMember.class.getRecordComponents()) {
                if (component.getName().equals("expectedOutcome")) {
                    continue;
                }
                Object written = component.getAccessor().invoke(row);
                Object read = getter(component.getName()).invoke(member);
                if (written instanceof BigDecimal w && read instanceof BigDecimal r) {
                    assertThat(r).as("%s.%s", row.membershipNumber(), component.getName()).isEqualByComparingTo(w);
                } else {
                    assertThat(read).as("%s.%s", row.membershipNumber(), component.getName()).isEqualTo(written);
                }
                compared++;
            }
        }
        assertThat(compared).as("fields compared").isGreaterThan(file().size() * 25);
        assertThat(stored.values()).filteredOn(m -> m.getPersonalEmail() != null)
                .as("addresses really stored -- none of the original 160 stores one (F65)").isNotEmpty();
    }

    @Test
    @DisplayName("once seeded, a start costs ONE query -- never an exists per member (SD4-F)")
    void aSeededStartCostsOneQuery() throws Exception {
        seeder.seed();
        long statements = QueryCount.during(entityManagerFactory, () -> {
            try {
                assertThat(seeder.seed()).isZero();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        assertThat(statements).isEqualTo(1);
    }

    @Test
    @DisplayName("🔒 where the production marker is set, nobody synthetic is seeded (SD3-A)")
    void productionIsNeverSeeded() throws Exception {
        DebitOrderMemberSeeder inProduction =
                new DebitOrderMemberSeeder(seedMembers, members, new SasboEnvironment("production"));
        assertThat(inProduction.seed()).isZero();
        assertThat(members.count()).isZero();
        // The must-pass twin: the same database, no marker.
        assertThat(seeder.seed()).isEqualTo(file().size());
    }

    private static Method getter(String property) throws NoSuchMethodException {
        return Member.class.getMethod("get" + Character.toUpperCase(property.charAt(0)) + property.substring(1));
    }
}
