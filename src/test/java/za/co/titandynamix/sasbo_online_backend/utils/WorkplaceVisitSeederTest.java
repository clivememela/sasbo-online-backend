package za.co.titandynamix.sasbo_online_backend.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.transaction.PlatformTransactionManager;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;
import za.co.titandynamix.sasbo_online_backend.config.WorkplaceSwitch;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * A.1's seeder of SASBO's branch visits, where it must write nothing (MD45, MD53): the switch unset -- the link's, until
 * the owner's word of 1 Oct is carried out (MO26, MO28) -- and the production marker before SASBO has confirmed the
 * list. What it writes where it may is {@code WorkplaceVisitIT}'s.
 */
@DisplayName("A.1 -- where SASBO's branch visits are not seeded")
class WorkplaceVisitSeederTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-15T08:00:00Z"), ZoneOffset.UTC);

    private WorkplaceVisitSeeder seeder(String environment, String workplaces) {
        return new WorkplaceVisitSeeder(jdbc, transactions, new SasboEnvironment(environment),
                new WorkplaceSwitch(workplaces), clock);
    }

    @Test
    @DisplayName("🔒 MD45, MD53: the switch unset seeds nothing -- no statement, no transaction")
    void theSwitchUnsetSeedsNothing() {
        seeder("", "").seed();

        verifyNoInteractions(jdbc, transactions);
    }

    @Test
    @DisplayName("🔒 MD45: under the production marker nothing is seeded until SASBO confirms the list -- the committed file is not confirmed")
    void productionWaitsForSasbosWord() {
        seeder("production", "all").seed();

        verifyNoInteractions(jdbc, transactions);
    }

    @Test
    @DisplayName("the must-pass: the switch set, outside production, the visits are written -- in a transaction, after the workplaces are read")
    void theSwitchSetSeeds() {
        seeder("", "all").seed();

        verify(transactions).getTransaction(any());
        verify(jdbc).query(startsWith("SELECT id, ra_number FROM workplaces"), any(RowCallbackHandler.class));
    }
}
