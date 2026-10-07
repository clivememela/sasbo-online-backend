package za.co.titandynamix.sasbo_online_backend.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.support.AbstractWritingIT;

import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RC15 on Postgres: the Recruiters screen's figures are one grouped statement, and V69's index serves it.
 *
 * <p>With sequential scans switched off the planner must reach the members through an index or not at all, so the plan
 * naming {@code members_recruitment_channel} proves the index fits the statement's shape (its {@code IS NOT NULL} and
 * its three grouped columns). Which plan the planner CHOOSES depends on the table's size; that is the index's to earn
 * on SASBO's numbers, not this test's to fake with a few rows. The statement is the repository's own, read through
 * {@link RecruitmentChannelMembersRepository#summaryStatement}, never re-typed here.
 */
@DisplayName("RC15: the Recruiters screen's figures on Postgres -- one statement, served by V69's index")
class RecruitmentChannelSummaryPlanIT extends AbstractWritingIT {

    @Test
    @DisplayName("the figures' statement can be answered from members_recruitment_channel")
    void theFiguresAreServedByTheIndex() {
        jdbc.update("INSERT INTO recruitment_channel (name, is_enabled) VALUES ('BEC', TRUE), ('RO', TRUE)");
        jdbc.update("INSERT INTO members (membership_number, surname, bank_name, branch, recruitment_channel_id,"
                + " created_at, updated_at) SELECT 'P-' || g, 'Planperson', 'Synthetic Bank', 'Hill',"
                + " (SELECT id FROM recruitment_channel WHERE name = CASE WHEN g % 2 = 0 THEN 'BEC' ELSE 'RO' END),"
                + " now(), now() FROM generate_series(1, 200) AS g");
        jdbc.execute("ANALYZE members");

        String statement = RecruitmentChannelMembersRepository.summaryStatement(CallerScope.all(),
                new MapSqlParameterSource());
        String plan = jdbc.execute((ConnectionCallback<String>) connection -> {
            try (Statement st = connection.createStatement()) {
                st.execute("SET enable_seqscan = off");
                try (ResultSet rs = st.executeQuery("EXPLAIN " + statement)) {
                    StringBuilder text = new StringBuilder();
                    while (rs.next()) {
                        text.append(rs.getString(1)).append('\n');
                    }
                    return text.toString();
                } finally {
                    st.execute("RESET enable_seqscan");
                }
            }
        });

        assertThat(plan).as("the plan:%n%s", plan).contains("members_recruitment_channel");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE indexname = 'members_recruitment_channel'",
                Integer.class)).as("V69 is applied").isEqualTo(1);
    }
}
