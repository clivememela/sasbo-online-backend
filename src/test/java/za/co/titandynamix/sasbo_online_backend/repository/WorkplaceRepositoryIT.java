package za.co.titandynamix.sasbo_online_backend.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.WorkplaceDto;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.WorkplacePageDto;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.services.WorkplaceService;
import za.co.titandynamix.sasbo_online_backend.support.AbstractWritingIT;
import za.co.titandynamix.sasbo_online_backend.utils.ReferenceListSeeder;
import za.co.titandynamix.sasbo_online_backend.utils.WorkplaceSeeder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SASBO's workplaces on PostgreSQL, with the switch at {@code all} (month end 01.2): seeded by R/A number, paged by
 * the server in its own order, every filter, a page that does not exist refused, and a search that is literal --
 * the collation, the {@code LIKE} and its escape are the database's, so this is where they are proved.
 */
@TestPropertySource(properties = "sasbo.workplaces=all")
@DisplayName("SASBO's workplaces on PostgreSQL")
class WorkplaceRepositoryIT extends AbstractWritingIT {

    @Autowired
    private WorkplaceRepository repository;

    @Autowired
    private WorkplaceService service;

    @Autowired
    private ReferenceListSeeder lists;

    @Autowired
    private WorkplaceSeeder workplaces;

    @BeforeEach
    void seeded() {
        lists.seed();
        workplaces.seed();
    }

    @Test
    @DisplayName("MV-61: every page in the server's order -- no two pages share a row, none skips one, a name's rows by R/A number")
    void everyPageInOrder() {
        long total = repository.count();
        assertThat(total).isPositive();
        List<WorkplaceDto> all = new ArrayList<>();
        WorkplacePageDto page = service.page(0, 200, null, null, null, null, null);
        int pages = page.totalPages();
        all.addAll(page.content());
        for (int n = 1; n < pages; n++) {
            all.addAll(service.page(n, 200, null, null, null, null, null).content());
        }
        Set<Integer> seen = new HashSet<>();
        all.forEach(w -> assertThat(seen.add(w.raNumber())).as("R/A %s on two pages", w.raNumber()).isTrue());
        assertThat((long) all.size()).as("every workplace, once").isEqualTo(total);
        for (int i = 1; i < all.size(); i++) {
            if (all.get(i).name().equals(all.get(i - 1).name())) {
                assertThat(all.get(i).raNumber()).as("the tie-breaker").isGreaterThan(all.get(i - 1).raNumber());
            }
        }
        assertThatThrownBy(() -> service.page(pages, 200, null, null, null, null, null))
                .isInstanceOfSatisfying(MonthEndRefusal.class,
                        r -> assertThat(r.code()).isEqualTo(MonthEndRefusalCode.WORKPLACE_PAGE_INVALID));
    }

    @Test
    @DisplayName("each filter narrows to what it names: employer, table code, area, committee")
    void eachFilter() {
        WorkplaceDto any = service.page(0, 200, null, null, null, null, null).content().stream()
                .filter(w -> w.employerId() != null && w.committeeName() != null).findFirst().orElseThrow();

        assertThat(service.page(0, 200, any.employerId(), null, null, null, null).content())
                .allSatisfy(w -> assertThat(w.employerId()).isEqualTo(any.employerId()));
        assertThat(service.page(0, 200, null, any.tableCode(), null, null, null).content())
                .allSatisfy(w -> assertThat(w.tableCode()).isEqualTo(any.tableCode()));
        assertThat(service.page(0, 200, null, null, any.area(), null, null).content())
                .allSatisfy(w -> assertThat(w.area()).isEqualTo(any.area()));
        assertThat(service.page(0, 200, null, null, null, any.committee(), null).content())
                .isNotEmpty()
                .allSatisfy(w -> assertThat(w.committee()).isEqualTo(any.committee()));
    }

    @Test
    @DisplayName("🔒 MR-PERF-12: a search for % finds a workplace with a % in its name and no other; a number finds its R/A")
    void aSearchIsLiteral() {
        jdbc.update("INSERT INTO workplaces (ra_number, name, employer_printed, area_id, committee_code, "
                + "staff_complement, cl_code) SELECT 999998, 'INVENTED 100% BRANCH', 'INVENTED BANK', id, 'Q99', 1, "
                + "'00000000' FROM sasbo_areas WHERE letter = 'A'");
        Long withAPercent = jdbc.queryForObject("SELECT count(*) FROM workplaces WHERE name LIKE '%!%%' ESCAPE '!'",
                Long.class);

        WorkplacePageDto found = service.page(0, 200, null, null, null, null, "%");

        assertThat(found.content()).extracting(WorkplaceDto::name).contains("INVENTED 100% BRANCH");
        assertThat((long) found.content().size()).isEqualTo(withAPercent);
        assertThat(service.page(0, 200, null, null, null, null, "999998").content())
                .extracting(WorkplaceDto::raNumber).contains(999998);
    }

    @Test
    @DisplayName("🔒 MR-ROB-24: a workplace is closed, never deleted -- and a second start on PostgreSQL adds nothing")
    void aSecondStartAddsNothing() {
        long before = repository.count();

        workplaces.seed();

        assertThat(repository.count()).isEqualTo(before);
    }
}
