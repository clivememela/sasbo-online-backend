package za.co.titandynamix.sasbo_online_backend.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import za.co.titandynamix.sasbo_online_backend.dto.reference.CoreTiersDto;
import za.co.titandynamix.sasbo_online_backend.dto.reference.ReferenceRows;
import za.co.titandynamix.sasbo_online_backend.dto.reference.TableCodeTierDto;
import za.co.titandynamix.sasbo_online_backend.dto.summary.MemberSummaryDto;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.security.Permission;
import za.co.titandynamix.sasbo_online_backend.security.SignedInUser;
import za.co.titandynamix.sasbo_online_backend.support.AbstractSwitchedOnIT;
import za.co.titandynamix.sasbo_online_backend.support.SignInTestUsers;
import za.co.titandynamix.sasbo_online_backend.support.SummaryFixture;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Core A, B and C on PostgreSQL (Stephne, MEN-31: "Decisions are made by Senior Secretariat. | Core A - Absa, FNB,
 * Nedbank, Standard, Capitec, oldmutual. | ... | All other are captured under individual institutions"): the tiers V79
 * writes into every database, a table code set in one and out of it again, the refusals, the figures grouped by tier,
 * and the constraints by their names. The fixture's table codes 901 and 902, invented.
 */
@DisplayName("Core A, B and C, on PostgreSQL")
class CoreTiersIT extends AbstractSwitchedOnIT {

    private static final SignedInUser MAINTAINER = SignInTestUsers.user("invented.maintainer", CallerScope.all(),
            Permission.REFERENCE_MAINTAIN, Permission.MONTH_END_VIEW);

    @Autowired
    private CoreTiers tiers;

    @Autowired
    private MemberRepository members;

    @Autowired
    private MemberSummaryService summary;

    @BeforeEach
    void theFixturesTableCodes() {
        SummaryFixture.insert(jdbc, members);
    }

    private TableCodeTierDto set(int code, String tier) throws Exception {
        return SignInTestUsers.actingAs(MAINTAINER, () -> tiers.setTier(code, tier));
    }

    @Test
    @DisplayName("🔒 MEN-31: V79 writes the three tiers into every database, provisional until confirmed, with no code in any -- every code is all other institutions until one is set")
    void theThreeTiers() {
        CoreTiersDto list = tiers.list();

        assertThat(list.tiers()).extracting(ReferenceRows.CoreTier::code, ReferenceRows.CoreTier::name,
                        ReferenceRows.CoreTier::ordinal, ReferenceRows.CoreTier::provisional)
                .containsExactly(tuple("A", "Core A", 1, true), tuple("B", "Core B", 2, true),
                        tuple("C", "Core C", 3, true));
        assertThat(list.tiers()).allSatisfy(tier -> assertThat(tier.tableCodes()).isEmpty());
        assertThat(list.allOther()).isEqualTo("All other institutions");
    }

    @Test
    @DisplayName("MEN-31: a code set in a tier -- the letter in either case -- stands in it, says who set it and when, and is taken out again for all other institutions")
    void aCodeInATierAndOutAgain() throws Exception {
        TableCodeTierDto inA = set(901, " a ");

        assertThat(inA.coreTier()).isEqualTo("A");
        assertThat(inA.name()).isEqualTo("INVENTED BANK");
        assertThat(inA.setBy()).isEqualTo("Invented invented.maintainer (invented.maintainer)");
        assertThat(inA.setAt()).isEqualTo(SummaryFixture.NOW.toString());
        set(902, "B");
        assertThat(tiers.list().tiers()).extracting(ReferenceRows.CoreTier::tableCodes)
                .containsExactly(java.util.List.of(901), java.util.List.of(902), java.util.List.of());

        assertThat(set(901, null).coreTier()).as("none: all other institutions").isNull();
        assertThat(set(902, "  ").coreTier()).as("blank is none").isNull();
        assertThat(tiers.list().tiers()).allSatisfy(tier -> assertThat(tier.tableCodes()).isEmpty());
    }

    @Test
    @DisplayName("🔒 a tier that is not A, B or C is CORE_TIER_UNKNOWN, a code not on record TABLE_CODE_UNKNOWN -- and nothing changes")
    void theRefusals() throws Exception {
        set(901, "A");

        assertThatThrownBy(() -> set(901, "D")).isInstanceOfSatisfying(MonthEndRefusal.class, refusal -> {
            assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.CORE_TIER_UNKNOWN);
            assertThat(refusal.details()).containsEntry("tier", "D").containsEntry("known", "A,B,C");
        });
        assertThatThrownBy(() -> set(999, "A")).isInstanceOfSatisfying(MonthEndRefusal.class, refusal -> {
            assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.TABLE_CODE_UNKNOWN);
            assertThat(refusal.details()).containsEntry("code", "999");
        });
        assertThat(jdbc.queryForObject("SELECT ct.code FROM table_codes t JOIN core_tiers ct ON ct.id = t.core_tier_id "
                + "WHERE t.code = 901", String.class)).isEqualTo("A");
    }

    @Test
    @DisplayName("🔒 MEN-31: the figures grouped by tier -- the codes in Core A together, the rest all other institutions, signed in to see them exactly")
    void theFiguresByTier() throws Exception {
        set(901, "A");

        MemberSummaryDto byTier = SignInTestUsers.actingAs(MAINTAINER, () -> summary.summary("2026-09", "tier", null,
                null));

        Map<String, Long> onRecord = byTier.rows().stream().filter(row -> row.figures() != null)
                .collect(Collectors.toMap(MemberSummaryDto.Row::key, row -> row.figures().onRecord()));
        assertThat(onRecord).as("W1 and W3 under 901: nine and two on record").containsEntry("A", 11L)
                .as("W2 under 902, in no tier").containsEntry("~NO_TIER", 3L);
        assertThat(onRecord.keySet()).doesNotContain("B", "C");
    }

    @Test
    @DisplayName("🔒 the database holds the tiers where the service is not the writer -- each constraint by its name")
    void theConstraintsByName() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO core_tiers (code, name, ordinal) VALUES ('D', 'Core D', 4)"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("core_tiers_code_known");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO core_tiers (code, name, ordinal) VALUES ('A', 'Core A', 4)"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("core_tiers_code_unique");
        assertThatThrownBy(() -> jdbc.update("UPDATE table_codes SET core_tier_id = 999999 WHERE code = 901"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("table_codes_core_tier_fk");
        assertThat(jdbc.update("UPDATE table_codes SET core_tier_id = (SELECT id FROM core_tiers WHERE code = 'C') "
                + "WHERE code = 901")).as("must-pass: a tier on record is taken").isEqualTo(1);
    }
}
