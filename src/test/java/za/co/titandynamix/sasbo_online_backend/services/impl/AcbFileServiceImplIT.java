package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunDto;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusalCode;
import za.co.titandynamix.sasbo_online_backend.fixtures.AcbRunFixture;
import za.co.titandynamix.sasbo_online_backend.services.AcbFileService;
import za.co.titandynamix.sasbo_online_backend.services.AcbFileService.RenderedFile;
import za.co.titandynamix.sasbo_online_backend.services.AcbRunService;
import za.co.titandynamix.sasbo_online_backend.support.AbstractAcbIT;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * The bureau file, rendered from a real run on real PostgreSQL, kept, and served again unchanged.
 *
 * <p>No run can be approved through the application while the API is anonymous, so the run is
 * approved here through the service -- by an approver who is not its preparer -- which is exactly
 * what the route will do the day it knows who is calling. Every member in the fixture is given an
 * account type first, as the capture arc's field will: until then a file refuses to render, which the
 * first test proves.
 *
 * <p>The union's identifiers are synthetic, set for this class only.
 */
@TestPropertySource(properties = {
        "acb.file.user-code=9876",
        "acb.file.user-branch=123456",
        "acb.file.nominated-account=10020030040",
        "acb.file.bank-identifiers=1111122222"
})
@DisplayName("The bureau file, on PostgreSQL")
class AcbFileServiceImplIT extends AbstractAcbIT {

    @Autowired
    private AcbRunService runs;
    @Autowired
    private AcbFileService files;

    /** A prepared October run from the fixture, approved by someone other than its preparer. */
    private AcbRunDto approvedRun(boolean withAccountTypes) {
        AcbRunFixture.insert(jdbc, 15, 2).assertSize();
        AcbRunDto prepared = runs.get(runs.prepare("2026-10", "system").id());
        if (withAccountTypes) {
            jdbc.update("UPDATE acb_run_lines SET account_type = 1 WHERE run_id = ?", prepared.id());
        }
        return runs.approve(prepared.id(), prepared.version(), "approver", "totals checked");
    }

    @Test
    @DisplayName("a run whose lines carry no account type refuses to render, and says how many")
    void noAccountTypeNoFile() {
        AcbRunDto run = approvedRun(false);
        AcbRefusal refused = refusal(() -> files.render(run.id(), "approver"));
        assertThat(refused.code()).isEqualTo(AcbRefusalCode.FILE_LINES_INCOMPLETE);
        assertThat(refused.details()).containsEntry("no account type", String.valueOf(run.lineCount()));
        assertThat(files.hasRendered(run.id())).isFalse();
    }

    @Test
    @DisplayName("an approved run renders: its trailer is the run's totals, and each line learns its sequence")
    void anApprovedRunRenders() {
        AcbRunDto run = approvedRun(true);

        RenderedFile file = files.render(run.id(), "approver");

        assertThat(file.fileName()).isEqualTo("sasbosub.oct");
        assertThat(file.debitCount()).isEqualTo(run.lineCount());
        assertThat(file.debitValue()).isEqualByComparingTo(run.totalAmount());
        assertThat(file.sha256()).matches("[0-9a-f]{64}");
        String text = new String(file.bytes(), StandardCharsets.US_ASCII);
        List<String> records = List.of(text.split("\r\n"));
        assertThat(records).allSatisfy(r -> assertThat(r).hasSize(180));
        assertThat(records.get(0)).startsWith("02");
        assertThat(records.get(records.size() - 1)).startsWith("94");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acb_run_lines WHERE run_id = ? AND file_sequence IS NULL",
                Integer.class, run.id())).as("every line knows its sequence").isZero();
    }

    @Test
    @DisplayName("🔒 R2-5, D31: the header's creation date is the day the file was MADE -- the frozen clock's, not three days before the first debit")
    void theCreationDateIsTheRenderDay() {
        AcbRunDto run = approvedRun(true);
        // Before the file is made the run says when it must be made by, and that it has not been.
        assertThat(run.creationDate()).isNull();
        assertThat(run.makeBy()).isEqualTo(LocalDate.parse(run.firstDueDate()).minusDays(3).toString());
        assertThat(run.makeByPassed()).as("18 September is well before the October file's day").isFalse();

        RenderedFile file = files.render(run.id(), "approver");

        String made = "260918";
        String purge = LocalDate.parse(runs.get(run.id()).purgeDate()).format(java.time.format.DateTimeFormatter.ofPattern("yyMMdd"));
        List<String> records = List.of(new String(file.bytes(), StandardCharsets.US_ASCII).split("\r\n"));
        assertThat(records.get(0)).as("the 02 header").contains(made + purge);
        assertThat(records.get(1)).as("the 04 header").startsWith("04").contains(made + purge);
        String computedAhead = LocalDate.parse(run.firstDueDate()).minusDays(3)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyMMdd"));
        assertThat(records.get(0)).doesNotContain(computedAhead + purge);
        AcbRunDto after = runs.get(run.id());
        assertThat(after.creationDate()).isEqualTo("2026-09-18");
        assertThat(after.version()).as("making the file is not a transition").isEqualTo(run.version());
    }

    @Test
    @DisplayName("rendering the same run again gives the same bytes -- the stored file, proved by its hash")
    void renderingAgainIsIdempotent() {
        AcbRunDto run = approvedRun(true);
        RenderedFile first = files.render(run.id(), "approver");
        RenderedFile again = files.render(run.id(), "approver");
        assertThat(again.sha256()).isEqualTo(first.sha256());
        assertThat(again.bytes()).isEqualTo(first.bytes());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acb_run_files", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("a run whose lines changed after its file was made refuses to render a different one")
    void aChangedRunRefuses() {
        AcbRunDto run = approvedRun(true);
        files.render(run.id(), "approver");
        jdbc.update("UPDATE acb_run_lines SET surname = 'Changed' WHERE id = "
                + "(SELECT min(id) FROM acb_run_lines WHERE run_id = ?)", run.id());

        assertThat(refusal(() -> files.render(run.id(), "approver")).code())
                .isEqualTo(AcbRefusalCode.FILE_CHANGED_SINCE);
    }

    @Test
    @DisplayName("a prepared run cannot produce a file")
    void aPreparedRunHasNoFile() {
        AcbRunFixture.insert(jdbc, 15, 2);
        AcbRunDto prepared = runs.get(runs.prepare("2026-10", "system").id());
        assertThat(prepared.status()).isEqualTo(AcbRunStatus.PREPARED);
        assertThat(refusal(() -> files.render(prepared.id(), "system")).code())
                .isEqualTo(AcbRefusalCode.FILE_NOT_APPROVED);
    }

    @Test
    @DisplayName("submission is recorded only once there is a file, and a submitted run serves the file it sent")
    void submissionNeedsAFile() {
        AcbRunDto run = approvedRun(true);
        assertThat(refusal(() -> runs.recordSubmitted(run.id(), run.version(), "cfo", "uploaded")).code())
                .isEqualTo(AcbRefusalCode.FILE_NOT_RENDERED);

        RenderedFile rendered = files.render(run.id(), "approver");
        AcbRunDto submitted = runs.recordSubmitted(run.id(), run.version(), "cfo", "uploaded at the bank");
        assertThat(submitted.status()).isEqualTo(AcbRunStatus.SUBMITTED);
        assertThat(files.render(submitted.id(), "cfo").sha256()).isEqualTo(rendered.sha256());
    }

    @Test
    @DisplayName("past its purge date a file's bytes go, its hash stays, and serving it says why")
    void purging() {
        AcbRunDto run = approvedRun(true);
        RenderedFile rendered = files.render(run.id(), "approver");
        runs.recordSubmitted(run.id(), run.version(), "cfo", "uploaded");
        jdbc.update("UPDATE acb_run_files SET keep_until = DATE '2026-09-01' WHERE run_id = ?", run.id());

        assertThat(files.purgeExpired()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT sha256 FROM acb_run_files WHERE run_id = ?", String.class, run.id()))
                .isEqualTo(rendered.sha256());
        assertThat(refusal(() -> files.render(run.id(), "cfo")).code()).isEqualTo(AcbRefusalCode.FILE_PURGED);
    }

    private static AcbRefusal refusal(Runnable call) {
        try {
            call.run();
        } catch (AcbRefusal refusal) {
            return refusal;
        }
        return fail("expected an AcbRefusal and the call returned normally");
    }
}
