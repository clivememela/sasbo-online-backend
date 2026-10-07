package za.co.titandynamix.sasbo_online_backend.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import za.co.titandynamix.sasbo_online_backend.dto.reference.NoDeductionReasonDto;
import za.co.titandynamix.sasbo_online_backend.dto.reference.NoDeductionReasonRequest;
import za.co.titandynamix.sasbo_online_backend.dto.reference.NoDeductionReasonsDto;
import za.co.titandynamix.sasbo_online_backend.entity.monthend.NoDeductionReason;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.NoDeductionReasonRepository;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.security.Permission;
import za.co.titandynamix.sasbo_online_backend.security.SignedInUser;
import za.co.titandynamix.sasbo_online_backend.support.AbstractSwitchedOnIT;
import za.co.titandynamix.sasbo_online_backend.support.SignInTestUsers;
import za.co.titandynamix.sasbo_online_backend.support.SummaryFixture;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * The reasons for no payroll deduction on PostgreSQL (Stephne, MEN-17: "Can we have a dropdown sayin a reason for no
 * payroll deduction. Also have the opporenity to type a reason"): V79's list as written into every database, the
 * rules of adding and editing one, and the constraints that hold them when the service is not the writer -- each by
 * its name. The fixture's clock, mid-September 2026; every row invented.
 */
@DisplayName("The reasons for no payroll deduction, on PostgreSQL")
class NoDeductionReasonsIT extends AbstractSwitchedOnIT {

    private static final SignedInUser MAINTAINER = SignInTestUsers.user("invented.maintainer", CallerScope.all(),
            Permission.REFERENCE_MAINTAIN);
    private static final String STAMP = "Invented invented.maintainer (invented.maintainer)";

    @Autowired
    private NoDeductionReasons reasons;

    @Autowired
    private NoDeductionReasonRepository repository;

    @BeforeEach
    void theCodesTheyStandUnder() {
        // SASBO's suspension codes are a list the seed keeps, not a migration: the three her reasons stand under.
        jdbc.update("INSERT INTO suspension_codes (code, name) VALUES (11, 'NOT ON PAYROLL'), (12, 'MATERNITY LEAVE'), "
                + "(21, 'SICK LEAVE')");
    }

    private NoDeductionReasonDto added(NoDeductionReasonRequest request) throws Exception {
        return SignInTestUsers.actingAs(MAINTAINER, () -> reasons.create(request));
    }

    private NoDeductionReasonDto edited(long id, NoDeductionReasonRequest request) throws Exception {
        return SignInTestUsers.actingAs(MAINTAINER, () -> reasons.update(id, request));
    }

    private long idOf(String name) {
        return repository.findByNameInAnyCase(name).getFirst().getId();
    }

    @Test
    @DisplayName("🔒 MEN-17: V79 writes her list into every database -- in her order, maternity under 12 and sick leave under 21, the three we placed under 11 provisional")
    void herListIsOnRecord() {
        NoDeductionReasonsDto list = reasons.list();

        assertThat(list.rows()).extracting(NoDeductionReasonDto::name, NoDeductionReasonDto::suspensionCode,
                        NoDeductionReasonDto::provisional, NoDeductionReasonDto::active)
                .containsExactly(tuple("Unpaid leave", 11, true, true), tuple("Maternity leave", 12, false, true),
                        tuple("Paternity leave", 11, true, true), tuple("Insufficient funds", 11, true, true),
                        tuple("Sick leave", 21, false, true));
        assertThat(list.rows()).extracting(NoDeductionReasonDto::suspensionName).containsExactly("NOT ON PAYROLL",
                "MATERNITY LEAVE", "NOT ON PAYROLL", "NOT ON PAYROLL", "SICK LEAVE");
        assertThat(list.rows()).extracting(NoDeductionReasonDto::updatedBy).containsOnly("migration");
        assertThat(list.ceiling()).isEqualTo(NoDeductionReasons.CEILING).isEqualTo(50);
        assertThat(list.nameLimit()).isEqualTo(60);
        assertThat(list.noteLimit()).isEqualTo(500);
    }

    @Test
    @DisplayName("the drop-down's order is the ordinal and then the name, inactive reasons kept in it -- as the repository reads it")
    void theOrderIsTheOrdinalsThenTheNames() throws Exception {
        added(new NoDeductionReasonRequest("Bereavement leave", 12, 2, null, null));
        added(new NoDeductionReasonRequest("Annual leave", 11, 2, false, null));

        assertThat(repository.findAllByOrderByOrdinalAscNameAsc()).extracting(NoDeductionReason::getName)
                .containsExactly("Unpaid leave", "Annual leave", "Bereavement leave", "Maternity leave",
                        "Paternity leave", "Insufficient funds", "Sick leave");
        assertThat(reasons.list().rows()).filteredOn(row -> row.name().equals("Annual leave"))
                .extracting(NoDeductionReasonDto::active).containsExactly(false);
    }

    @Test
    @DisplayName("MEN-17: an administrator's reason goes last unless given a place, is active unless said otherwise, is never provisional, and says who added it")
    void anAdministratorsReason() throws Exception {
        assertThat(repository.lastOrdinal()).isEqualTo(5);

        NoDeductionReasonDto study = added(new NoDeductionReasonRequest("  Study leave  ", 11, null, null, null));

        assertThat(study.name()).as("trimmed").isEqualTo("Study leave");
        assertThat(study.ordinal()).as("last").isEqualTo(6);
        assertThat(study.active()).isTrue();
        assertThat(study.provisional()).isFalse();
        assertThat(study.suspensionName()).isEqualTo("NOT ON PAYROLL");
        assertThat(study.updatedBy()).isEqualTo(STAMP);
        assertThat(study.updatedAt()).isEqualTo(SummaryFixture.NOW.toString());
        assertThat(repository.lastOrdinal()).isEqualTo(6);
    }

    @Test
    @DisplayName("🔒 one reason by one name, whatever its case or spaces: NO_DEDUCTION_REASON_TAKEN names the other, on adding and on editing -- and nothing is written")
    void aNameIsTakenWhateverItsCase() throws Exception {
        long sick = idOf("Sick leave");
        assertThat(repository.findByNameInAnyCase("  SICK leave ")).extracting(NoDeductionReason::getId)
                .containsExactly(sick);

        assertThatThrownBy(() -> added(new NoDeductionReasonRequest(" sick LEAVE", 21, null, null, null)))
                .isInstanceOfSatisfying(MonthEndRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.NO_DEDUCTION_REASON_TAKEN);
                    assertThat(refusal.details()).containsExactly(Map.entry("id", String.valueOf(sick)));
                });
        long unpaid = idOf("Unpaid leave");
        assertThatThrownBy(() -> edited(unpaid, new NoDeductionReasonRequest("Sick Leave", 11, null, null, 0)))
                .isInstanceOfSatisfying(MonthEndRefusal.class, refusal ->
                        assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.NO_DEDUCTION_REASON_TAKEN));
        assertThat(repository.count()).isEqualTo(5);
        assertThat(repository.findById(unpaid).orElseThrow().getName()).isEqualTo("Unpaid leave");
        assertThat(edited(unpaid, new NoDeductionReasonRequest("UNPAID leave", 11, null, null, 0)).name())
                .as("must-pass: a reason may take its own name in another case").isEqualTo("UNPAID leave");
    }

    @Test
    @DisplayName("🔒 the name, the code and the place are held to the rules -- NO_DEDUCTION_REASON_INVALID naming the field and its limit, or the codes on record")
    void theFieldsAreHeldToTheRules() {
        assertThat(refusedOn(new NoDeductionReasonRequest("   ", 11, null, null, null)))
                .containsEntry("field", "name").containsEntry("limit", "60");
        assertThat(refusedOn(new NoDeductionReasonRequest("x".repeat(61), 11, null, null, null)))
                .containsEntry("field", "name");
        assertThat(refusedOn(new NoDeductionReasonRequest("Study" + (char) 10 + "leave", 11, null, null, null)))
                .as("a name is one line").containsEntry("field", "name");
        assertThat(refusedOn(new NoDeductionReasonRequest("Study leave", 99, null, null, null)))
                .containsEntry("field", "suspensionCode").containsEntry("known", "11,12,21");
        assertThat(refusedOn(new NoDeductionReasonRequest("Study leave", null, null, null, null)))
                .containsEntry("field", "suspensionCode");
        assertThat(refusedOn(new NoDeductionReasonRequest("Study leave", 11, 1000, null, null)))
                .containsEntry("field", "ordinal").containsEntry("limit", "999");
        assertThat(refusedOn(new NoDeductionReasonRequest("Study leave", 11, 0, null, null)))
                .containsEntry("field", "ordinal");
        assertThat(repository.count()).isEqualTo(5);
    }

    private Map<String, String> refusedOn(NoDeductionReasonRequest request) {
        try {
            added(request);
        } catch (MonthEndRefusal refusal) {
            assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.NO_DEDUCTION_REASON_INVALID);
            assertThat(refusal.getMessage()).endsWith("Nothing was changed.");
            return refusal.details();
        } catch (Exception other) {
            throw new AssertionError(other);
        }
        throw new AssertionError("added, and should have been refused: " + request.name());
    }

    @Test
    @DisplayName("🔒 the list holds at most fifty: the fifty-first is NO_DEDUCTION_REASON_INVALID on the count")
    void theListHoldsFifty() throws Exception {
        for (int n = 6; n <= NoDeductionReasons.CEILING; n++) {
            added(new NoDeductionReasonRequest("Invented reason " + n, 11, null, null, null));
        }
        assertThat(repository.count()).isEqualTo(50);

        assertThat(refusedOn(new NoDeductionReasonRequest("One reason too many", 11, null, null, null)))
                .containsEntry("field", "count").containsEntry("limit", "50");
        assertThat(repository.count()).isEqualTo(50);
    }

    @Test
    @DisplayName("🔒 an edit is made on the version read: a stale one is NO_DEDUCTION_REASON_CHANGED_SINCE saying whose, and none at all REQUEST_MALFORMED -- and nothing changes")
    void anEditOnTheVersionRead() throws Exception {
        long unpaid = idOf("Unpaid leave");
        NoDeductionReasonDto first = edited(unpaid, new NoDeductionReasonRequest("Unpaid leave", 11, null, null, 0));
        assertThat(first.version()).isEqualTo(1);
        assertThat(first.provisional()).as("MEN-17: an edit settles the code -- it is ours no longer").isFalse();

        assertThatThrownBy(() -> edited(unpaid, new NoDeductionReasonRequest("Unpaid leave (agreed)", 11, null, null,
                0))).isInstanceOfSatisfying(MonthEndRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.NO_DEDUCTION_REASON_CHANGED_SINCE);
                    assertThat(refusal.details()).containsEntry("version", "1").containsEntry("updatedBy", STAMP);
                });
        assertThatThrownBy(() -> edited(unpaid, new NoDeductionReasonRequest("Unpaid leave (agreed)", 11, null, null,
                null))).isInstanceOfSatisfying(MonthEndRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.REQUEST_MALFORMED);
                    assertThat(refusal.details()).containsEntry("field", "version");
                });
        assertThatThrownBy(() -> edited(999_999L, new NoDeductionReasonRequest("Unpaid leave", 11, null, null, 0)))
                .isInstanceOfSatisfying(MonthEndRefusal.class, refusal ->
                        assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.NO_DEDUCTION_REASON_NOT_FOUND));
        assertThat(repository.findById(unpaid).orElseThrow().getName()).isEqualTo("Unpaid leave");
    }

    @Test
    @DisplayName("MEN-17: there is no deleting -- a reason is set inactive, keeps its place, and what an edit leaves out stays as it was")
    void aReasonIsSetInactive() throws Exception {
        long paternity = idOf("Paternity leave");

        NoDeductionReasonDto inactive = edited(paternity, new NoDeductionReasonRequest("Paternity leave", 11, null,
                false, 0));

        assertThat(inactive.active()).isFalse();
        assertThat(inactive.ordinal()).as("left out: as it was").isEqualTo(3);
        assertThat(reasons.list().rows()).hasSize(5);
    }

    @Test
    @DisplayName("🔒 the database holds the list where the service is not the writer -- each constraint by its name")
    void theConstraintsByName() {
        String insert = "INSERT INTO no_deduction_reasons (name, suspension_code, ordinal, active, provisional, version, "
                + "updated_by, updated_at) VALUES (?, ?, ?, TRUE, FALSE, 0, 'invented', now())";

        assertThatThrownBy(() -> jdbc.update(insert, "  SICK LEAVE ", 21, 9))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("no_deduction_reasons_name_unique");
        assertThatThrownBy(() -> jdbc.update(insert, "   ", 11, 9))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("no_deduction_reasons_name_not_blank");
        assertThatThrownBy(() -> jdbc.update(insert, "Invented: no code", 0, 9))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("no_deduction_reasons_code_is_a_code");
        assertThatThrownBy(() -> jdbc.update(insert, "Invented: no place", 11, 0))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("no_deduction_reasons_ordinal_positive");
        assertThat(jdbc.update(insert, "Invented: a reason the rules allow", 11, 9))
                .as("must-pass: a row the rules allow is written").isEqualTo(1);
    }
}
