package za.co.titandynamix.sasbo_online_backend.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import za.co.titandynamix.sasbo_online_backend.exception.MigrationRefusal;
import za.co.titandynamix.sasbo_online_backend.legacy.LegacyFixtures;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.support.AbstractSwitchedOnIT;
import za.co.titandynamix.sasbo_online_backend.support.LogCapture;
import za.co.titandynamix.sasbo_online_backend.support.MigrationFixture;
import za.co.titandynamix.sasbo_online_backend.utils.ReferenceListSeeder;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 🔒 Nothing of a member reaches the log: a whole rehearsal -- a file registered, refused, checked, a check failing, a
 * move, an undo -- with the application's own package captured at TRACE, and every identity number, e-mail address,
 * telephone number and distinctive name in the fixture asserted ABSENT from everything logged. The canaries -- the
 * lines a rehearsal does log, by number and count -- prove the capture saw the rehearsal, so the absences mean something.
 */
// @Isolated: this class captures a Logback logger, which Spring Boot resets whenever a context starts.
@Isolated
@DisplayName("No member's value in the migration's logs, on PostgreSQL")
class MigrationLogsIT extends AbstractSwitchedOnIT {

    private static final Pattern IDENTITY = Pattern.compile("(?<![0-9])[0-9]{12,13}(?![0-9])");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._+-]+@[A-Za-z0-9.-]+\\.[a-z]+");

    @Autowired
    private MigrationBatches batches;

    @Autowired
    private MigrationValidation validation;

    @Autowired
    private MigrationCommits commits;

    @Autowired
    private ReferenceListSeeder lists;

    @Autowired
    private MemberRepository members;

    @BeforeEach
    void theLists() {
        MigrationFixture.prepare(jdbc, lists, members);
    }

    /** What the fixture holds that is a person's: identity numbers, e-mail addresses, telephone numbers, names. */
    private static Set<String> planted() {
        String file = new String(LegacyFixtures.resource(LegacyFixtures.CSV_FIXTURE), Charset.forName("windows-1252"));
        Set<String> values = new LinkedHashSet<>();
        Matcher ids = IDENTITY.matcher(file);
        while (ids.find()) {
            values.add(ids.group());
        }
        Matcher emails = EMAIL.matcher(file);
        while (emails.find()) {
            values.add(emails.group());
        }
        values.add("082 000 ");
        values.add("+2782000");
        values.add("Zoë");
        values.add("Testcase");
        values.add("Unclosed Example");
        values.add("Example, Junior");
        values.add(LegacyFixtures.ON_RECORD_ID);
        return values;
    }

    @Test
    @DisplayName("🔒 a whole rehearsal logs numbers, counts and codes -- never an identity number, an address, a telephone or a name")
    void nothingOfAMemberIsLogged() {
        Set<String> planted = planted();
        assertThat(planted).as("the scan found the fixture's values: it is not vacuous").hasSizeGreaterThan(50);

        try (LogCapture log = LogCapture.under("za.co.titandynamix.sasbo_online_backend")) {
            long id = batches.register("member-export-faults.csv", LegacyFixtures.CSV_MAPPING, "2026-08-31", null,
                    MigrationFixture.csv()).batch().id();
            assertThatThrownBy(() -> batches.register("empty.csv", LegacyFixtures.CSV_MAPPING, "2026-08-31", null,
                    new ByteArrayInputStream(new byte[0]))).isInstanceOf(MigrationRefusal.class);
            validation.validate(id, null);
            commits.commit(id, true);
            commits.rollback(id, "A rehearsal for the logs.");
            long second = batches.register("member-export-faults.csv", LegacyFixtures.CSV_MAPPING, "2026-08-31", null,
                    MigrationFixture.csv()).batch().id();
            jdbc.update("UPDATE migration_batches SET mapping_snapshot = 'not a mapping' WHERE id = ?", second);
            validation.validate(second, null);

            String logged = log.rendered();

            assertThat(logged).as("the canaries: the rehearsal was seen")
                    .contains("Migration batch " + id + " registered: " + MigrationFixture.CSV_ROWS + " rows")
                    .contains("Migration batch " + id + " checked")
                    .contains("Migration batch " + id + " moved")
                    .contains("Migration batch " + id + " undone")
                    .contains("Migration batch " + second + ": the check failed");
            for (String value : planted) {
                assertThat(logged).as("a member's value reached the log").doesNotContain(value);
            }
        }
    }
}
