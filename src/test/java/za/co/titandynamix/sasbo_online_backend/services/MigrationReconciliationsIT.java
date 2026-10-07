package za.co.titandynamix.sasbo_online_backend.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import za.co.titandynamix.sasbo_online_backend.dto.migration.MigrationMonthTotalsDto;
import za.co.titandynamix.sasbo_online_backend.dto.migration.MigrationMonthTotalsDto.Figure;
import za.co.titandynamix.sasbo_online_backend.dto.migration.MigrationReconciliationDto;
import za.co.titandynamix.sasbo_online_backend.exception.MigrationRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MigrationRefusalCode;
import za.co.titandynamix.sasbo_online_backend.legacy.LegacyFixtures;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MigrationTotalsRepository;
import za.co.titandynamix.sasbo_online_backend.support.AbstractSwitchedOnIT;
import za.co.titandynamix.sasbo_online_backend.support.MigrationFixture;
import za.co.titandynamix.sasbo_online_backend.utils.ReferenceListSeeder;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proving a batch against the old system's figures, on PostgreSQL: a month's totals entered whole and checked figure
 * by figure; the batch set beside them by every dimension SASBO's month end cuts by -- through the workplace to the
 * table code, the employer, the group, the area and the region (rule G) -- each line MATCH, DIFFERENT, LEGACY_ONLY or
 * NO_LEGACY_FIGURE, with the rows held back beside it.
 */
@DisplayName("Reconciling a batch with the old system's month end, on PostgreSQL")
class MigrationReconciliationsIT extends AbstractSwitchedOnIT {

    @Autowired
    private MigrationBatches batches;

    @Autowired
    private MigrationValidation validation;

    @Autowired
    private MigrationReconciliations reconciliations;

    @Autowired
    private MigrationTotalsRepository totals;

    @Autowired
    private ReferenceListSeeder lists;

    @Autowired
    private MemberRepository members;

    @BeforeEach
    void theLists() {
        MigrationFixture.prepare(jdbc, lists, members);
    }

    private long checked() {
        long id = batches.register("member-export-faults.csv", LegacyFixtures.CSV_MAPPING, "2026-08-31", null,
                MigrationFixture.csv()).batch().id();
        validation.validate(id, null);
        return id;
    }

    private static MigrationMonthTotalsDto.Request figures(Figure... figures) {
        return new MigrationMonthTotalsDto.Request(List.of(figures));
    }

    private static MigrationReconciliationDto.Line line(MigrationReconciliationDto reconciled, String dimension,
                                                        String key, String measure) {
        return reconciled.lines().stream().filter(line -> line.dimension().equals(dimension) && line.key().equals(key)
                && line.measure().equals(measure)).findFirst().orElseThrow(() -> new AssertionError(
                "no line " + dimension + " " + key + " " + measure));
    }

    @Test
    @DisplayName("the batch beside its month's figures, by every dimension: through the workplace to table code, employer, group, area, region")
    void reconciles() {
        long id = checked();
        reconciliations.replace("2026-08", figures(
                new Figure("TOTAL", "", "ON_RECORD", 20L, "bank_master_summ"),
                new Figure("TOTAL", null, "SUSPENDED", 2L, "mem_count"),
                new Figure("TOTAL", "", "MEMBERS", 50L, "grlist"),
                new Figure("TABLE_CODE", "013", "MEMBERS", 16L, "mem_count"),
                new Figure("EMPLOYER", "Nedbank", "MEMBERS", 16L, "grlist"),
                new Figure("EMPLOYER_GROUP", "nedbank group", "MEMBERS", 16L, "grlist"),
                new Figure("AREA", "d", "MEMBERS", 16L, "bank_master_summ"),
                new Figure("AREA", "X", "MEMBERS", 5L, "bank_master_summ"),
                new Figure("REGION", "PTA", "MEMBERS", 16L, "prt_mem_area"),
                new Figure("SUSPENSION_CODE", "11", "SUSPENDED", 1L, "mem_count"),
                new Figure("SUSPENSION_CODE", "13", "SUSPENDED", 1L, "mem_count")));

        MigrationReconciliationDto reconciled = reconciliations.reconcile(id);

        assertThat(reconciled.month()).isEqualTo("2026-08");
        assertThat(reconciled.figuresEntered()).isEqualTo(11);
        assertThat(reconciled.rowsHeldBack()).isEqualTo(MigrationFixture.CSV_INVALID);
        assertThat(line(reconciled, "TOTAL", "", "ON_RECORD").status()).isEqualTo("MATCH");
        assertThat(line(reconciled, "TOTAL", "", "SUSPENDED").status()).isEqualTo("MATCH");
        MigrationReconciliationDto.Line members = line(reconciled, "TOTAL", "", "MEMBERS");
        assertThat(members.status()).isEqualTo("DIFFERENT");
        assertThat(members.batch()).isEqualTo(18);
        assertThat(members.difference()).isEqualTo(-32L);
        assertThat(members.heldBack()).as("the difference is the rows held back").isEqualTo(32);
        assertThat(line(reconciled, "TABLE_CODE", "13", "MEMBERS").status()).isEqualTo("MATCH");
        assertThat(line(reconciled, "EMPLOYER", "NEDBANK", "MEMBERS").status()).isEqualTo("MATCH");
        assertThat(line(reconciled, "EMPLOYER_GROUP", "NEDBANK GROUP", "MEMBERS").status()).isEqualTo("MATCH");
        assertThat(line(reconciled, "AREA", "D", "MEMBERS").status()).isEqualTo("MATCH");
        assertThat(line(reconciled, "AREA", "X", "MEMBERS").status()).isEqualTo("LEGACY_ONLY");
        assertThat(line(reconciled, "AREA", "L", "MEMBERS").status()).as("a key the pack printed no figure for")
                .isEqualTo("NO_LEGACY_FIGURE");
        assertThat(line(reconciled, "REGION", "PTA", "MEMBERS").status()).isEqualTo("MATCH");
        assertThat(line(reconciled, "SUSPENSION_CODE", "11", "SUSPENDED").status()).isEqualTo("MATCH");
        assertThat(reconciled.proved()).isFalse();
        assertThat(reconciled.summary()).containsEntry("DIFFERENT", 1L).containsEntry("LEGACY_ONLY", 1L);
    }

    @Test
    @DisplayName("every figure given exactly is proved; a month entered again replaces the month whole")
    void proved() {
        long id = checked();
        reconciliations.replace("2026-08", figures(new Figure("TOTAL", "", "MEMBERS", 99L, null)));
        reconciliations.replace("2026-08", figures(new Figure("TOTAL", "", "ON_RECORD", 20L, null),
                new Figure("TOTAL", "", "MEMBERS", 18L, null)));

        assertThat(totals.month(LocalDate.of(2026, 8, 1))).hasSize(2);
        assertThat(reconciliations.totals("2026-08").enteredBy()).isEqualTo("system");
        assertThat(reconciliations.reconcile(id).proved()).isTrue();
    }

    @Test
    @DisplayName("TOTALS_INVALID: a dimension or measure nobody knows, a measure the dimension does not count, no key, a code not a number, a figure twice")
    void refusedFigures() {
        assertThatThrownBy(() -> reconciliations.replace("2026-08", figures(new Figure("PROVINCE", "G", "MEMBERS", 1L, null))))
                .extracting(r -> ((MigrationRefusal) r).code()).isEqualTo(MigrationRefusalCode.TOTALS_INVALID);
        assertThatThrownBy(() -> reconciliations.replace("2026-08", figures(new Figure("TOTAL", "", "PAYING", 1L, null))))
                .extracting(r -> ((MigrationRefusal) r).code()).isEqualTo(MigrationRefusalCode.TOTALS_INVALID);
        assertThatThrownBy(() -> reconciliations.replace("2026-08",
                figures(new Figure("SUSPENSION_CODE", "11", "MEMBERS", 1L, null))))
                .extracting(r -> ((MigrationRefusal) r).details().get("index")).isEqualTo("0");
        assertThatThrownBy(() -> reconciliations.replace("2026-08", figures(new Figure("AREA", " ", "MEMBERS", 1L, null))))
                .extracting(r -> ((MigrationRefusal) r).code()).isEqualTo(MigrationRefusalCode.TOTALS_INVALID);
        assertThatThrownBy(() -> reconciliations.replace("2026-08", figures(new Figure("TABLE_CODE", "ABSA", "MEMBERS", 1L, null))))
                .extracting(r -> ((MigrationRefusal) r).code()).isEqualTo(MigrationRefusalCode.TOTALS_INVALID);
        assertThatThrownBy(() -> reconciliations.replace("2026-08", figures(new Figure("AREA", "D", "MEMBERS", 1L, null),
                new Figure("AREA", "d", "MEMBERS", 2L, null))))
                .extracting(r -> ((MigrationRefusal) r).details().get("index")).isEqualTo("1");
        assertThat(totals.month(LocalDate.of(2026, 8, 1))).as("nothing entered by a refused month").isEmpty();
    }

    @Test
    @DisplayName("MONTH_INVALID: a month that cannot be read, or one not begun; a batch not yet checked cannot be reconciled")
    void refusedMonthsAndBatches() {
        assertThatThrownBy(() -> reconciliations.totals("2026-13")).extracting(r -> ((MigrationRefusal) r).code())
                .isEqualTo(MigrationRefusalCode.MONTH_INVALID);
        assertThatThrownBy(() -> reconciliations.totals("2027-01")).extracting(r -> ((MigrationRefusal) r).code())
                .isEqualTo(MigrationRefusalCode.MONTH_INVALID);
        long draft = batches.register("member-export-faults.csv", LegacyFixtures.CSV_MAPPING, "2026-08-31", null,
                MigrationFixture.csv()).batch().id();
        assertThatThrownBy(() -> reconciliations.reconcile(draft)).extracting(r -> ((MigrationRefusal) r).code())
                .isEqualTo(MigrationRefusalCode.BATCH_NOT_IN_STATE);
    }
}
