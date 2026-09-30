package za.co.titandynamix.sasbo_online_backend.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import za.co.titandynamix.sasbo_online_backend.dto.reference.ReferenceRows;
import za.co.titandynamix.sasbo_online_backend.support.AbstractWritingIT;
import za.co.titandynamix.sasbo_online_backend.utils.ReferenceListSeeder;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SASBO's lists against the real migration chain (month end 01.1): V53's constraints BY NAME, in the catalogue --
 * the fast lane builds its schema from entities and has none of them, and {@code validate} reads tables and columns
 * and never a constraint (MR-ROB-21, BR88) -- and the lists seeded and read on PostgreSQL.
 */
@DisplayName("SASBO's lists on PostgreSQL")
class SasboReferenceRepositoryIT extends AbstractWritingIT {

    @Autowired
    private SasboReferenceRepository repository;

    @Autowired
    private ReferenceListSeeder seeder;

    @Test
    @DisplayName("🔒 MR-ROB-21: every key, reference and check of V53 and V54, by name, in the catalogue")
    void everyConstraintByName() {
        List<String> constraints = jdbc.queryForList("SELECT conname FROM pg_constraint c JOIN pg_class t "
                + "ON t.oid = c.conrelid WHERE t.relname IN ('reference_lists', 'sasbo_regions', 'sasbo_areas', "
                + "'employer_groups', 'employers', 'table_codes', 'committee_provinces', 'committees', "
                + "'suspension_codes', 'workplaces')", String.class);
        assertThat(constraints).contains(
                "reference_lists_key_unique", "reference_lists_outcome_known", "reference_lists_failure_known",
                "sasbo_regions_code_unique", "sasbo_areas_letter_unique", "sasbo_areas_region_fk",
                "employer_groups_name_unique", "employers_ordinal_unique", "employers_group_fk",
                "table_codes_code_unique", "table_codes_employer_fk", "table_codes_part_known",
                "committee_provinces_letter_unique", "committees_code_unique", "committees_province_fk",
                "suspension_codes_code_unique",
                "workplaces_ra_number_unique", "workplaces_table_code_fk", "workplaces_area_fk",
                "workplaces_committee_fk", "workplaces_table_code_by_known", "workplaces_table_code_proved",
                "workplaces_complement_not_negative");
        assertThat(jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE tablename = 'deletion_codes'",
                String.class)).contains("deletion_codes_sasbo_code_unique");
    }

    @Test
    @DisplayName("the database refuses what the checks name: a part that is not one, a proof without a code")
    void theChecksRefuse() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO table_codes (code, name, part) VALUES (990, 'X', 'BRANCHES')"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO reference_lists (list_key, report, outcome) "
                + "VALUES ('x', 'x', 'MAYBE')")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO deletion_codes (name, sasbo_code) VALUES ('a', 77), "
                + "('b', 77)")).as("SASBO's number is one code's").isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("the lists seeded on PostgreSQL, as many as the resource states, read in the server's order")
    void theListsSeededAndRead() throws IOException {
        seeder.seed();

        JsonNode resource;
        try (InputStream stream = new ClassPathResource(ReferenceListSeeder.RESOURCE).getInputStream()) {
            resource = new ObjectMapper().readTree(stream);
        }
        Map<String, Long> counts = repository.counts();
        for (JsonNode list : resource.path("lists")) {
            assertThat(counts.get(list.path("list").asText())).as(list.path("list").asText())
                    .isEqualTo(list.path("count").asLong());
        }
        List<ReferenceRows.Area> areas = repository.areas();
        assertThat(areas.get(areas.size() - 1).letter()).as("XX after Z: by length, then by letter").isEqualTo("XX");
        assertThat(repository.employers()).allSatisfy(e -> assertThat(e.group()).isNotBlank());
        assertThat(repository.employers()).anySatisfy(e -> assertThat(e.tableCodes()).hasSize(2));
        assertThat(repository.recorded()).containsKeys("regions", "deletionCodes");
    }

    @Test
    @DisplayName("🔒 BR64: the lane's reset still empties the lists and their record")
    void theResetEmptiesThem() {
        seeder.seed();

        za.co.titandynamix.sasbo_online_backend.support.DatabaseReset.reset(jdbc);

        assertThat(repository.counts().values()).allSatisfy(n -> assertThat(n).isZero());
        assertThat(repository.recorded()).isEmpty();
    }
}
