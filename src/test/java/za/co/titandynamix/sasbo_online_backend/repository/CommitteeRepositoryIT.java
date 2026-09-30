package za.co.titandynamix.sasbo_online_backend.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import za.co.titandynamix.sasbo_online_backend.entity.reference.Committee;
import za.co.titandynamix.sasbo_online_backend.support.AbstractWritingIT;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CommitteeRepository#findByCode} against PostgreSQL: how a member's BEC seat (V55) finds the
 * committee the client named. What the code matches is the database's decision, so it is asserted here
 * rather than in the fast lane.
 *
 * <p>The integration lane mocks the reference-list seeder and {@code DatabaseReset} clears SASBO's lists,
 * so each test writes the committee it needs, as the seeder would (V53's columns).
 */
@DisplayName("CommitteeRepository -- a BEC seat's committee, by SASBO's code")
class CommitteeRepositoryIT extends AbstractWritingIT {

    @Autowired
    private CommitteeRepository committees;

    @BeforeEach
    void johannesburg() {
        Long province = this.jdbc.queryForObject(
                "INSERT INTO committee_provinces (letter, name) VALUES ('G', NULL) RETURNING id", Long.class);
        this.jdbc.update("INSERT INTO committees (code, province_id, name, recruiting_number) "
                + "VALUES ('G01', ?, 'JOHANNESBURG', 1)", province);
    }

    @Test
    @DisplayName("SASBO's code finds its committee")
    void theCodeFindsTheCommittee() {
        assertThat(this.committees.findByCode("G01")).map(Committee::getName).contains("JOHANNESBURG");
    }

    @Test
    @DisplayName("must-pass: a code SASBO does not have finds nothing, and a code is matched exactly as SASBO prints it")
    void anyOtherCodeFindsNothing() {
        // The service refuses what finds nothing ("No Branch Executive Committee has that code"), so these
        // are the cases that must come back empty rather than as a near match.
        assertThat(this.committees.findByCode("ZZZ")).isEmpty();
        assertThat(this.committees.findByCode("g01")).isEmpty();
    }
}
