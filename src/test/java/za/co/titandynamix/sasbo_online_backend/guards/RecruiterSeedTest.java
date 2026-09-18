package za.co.titandynamix.sasbo_online_backend.guards;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The recruiter reference data — and the duplicate names in it.
 *
 * <h2>🔴 What this exists for</h2>
 * {@code recruiters.name} carries no unique constraint, and the seed repeats:
 * <b>"Vacant" appears three times</b>. Those are area posts with no incumbent, and
 * they are offered in the capture form's Recruiter drop-down exactly like a person.
 *
 * <p>Before 03.10 that was invisible, because nothing read the table at all. The
 * moment it became the drop-down's source, a plain {@code findByNameIgnoreCase}
 * returning {@code Optional} threw {@code NonUniqueResultException} — so an
 * administrator choosing "Vacant" got a <b>500 on save, from a value the screen had
 * just offered them.</b> Found by {@code RecruiterRepositoryIT} on its first run
 * against a real database; no mocked repository could have shown it.
 *
 * <p>The lookup is total now ({@code findFirstByNameIgnoreCaseOrderByIdAsc}), so
 * nothing 500s. <b>This test is the other half:</b> the data is still ambiguous, and
 * a deterministic pick from three identical rows is a stable answer rather than a
 * correct one. It stays red-adjacent — visible, named, and counted — until somebody
 * who owns that file decides whether "Vacant" belongs in a list of people at all.
 *
 * <p>📌 <b>For the owner:</b> should unfilled posts be selectable as a recruiter? If
 * not, they come out of the seed or gain a flag, and this test becomes an assertion
 * that there are no duplicates rather than a record of which ones there are.
 */
@DisplayName("Recruiter seed — the duplicate names, recorded")
class RecruiterSeedTest {

    private static List<String> NAMES;

    @BeforeAll
    static void load() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<String> names = new ArrayList<>();
        try (InputStream in = RecruiterSeedTest.class.getResourceAsStream("/data/recruiters.json")) {
            assertThat(in).as("the recruiter seed is on the classpath").isNotNull();
            for (JsonNode n : mapper.readTree(in)) {
                names.add(n.path("name").asText(null));
            }
        }
        NAMES = List.copyOf(names);
        assertThat(NAMES).as("recruiter names were actually read").isNotEmpty();
    }

    /** Case-insensitively duplicated names, and how many times each appears. */
    private static Map<String, Long> duplicates() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String name : NAMES) {
            if (name != null) {
                counts.merge(name.toLowerCase(Locale.ROOT), 1L, Long::sum);
            }
        }
        counts.values().removeIf(count -> count == 1);
        return counts;
    }

    @Test
    @DisplayName("🔴 the only duplicated name is the placeholder for an unfilled post")
    void theOnlyDuplicateIsVacant() {
        // Narrow on purpose. "There are duplicates" would stay green if a real
        // recruiter were duplicated tomorrow -- and two rows for one PERSON is a
        // different problem from three rows for "no incumbent": it makes the record
        // of who recruited a member genuinely ambiguous.
        assertThat(duplicates().keySet())
                .as("a real recruiter appearing twice makes 'who recruited this member' "
                        + "unanswerable. Only the vacancy placeholder may repeat.")
                .containsExactly("vacant");
    }

    @Test
    @DisplayName("every recruiter offered to the capture form has a usable name")
    void everyNameIsUsable() {
        // The drop-down renders `name` and submits it back, and the server looks that
        // string up. A blank or null name is an option that cannot be chosen and
        // cannot be resolved -- it would simply drop the recruiter, silently, which
        // is the defect 03.10 spent its whole existence on.
        assertThat(NAMES)
                .doesNotContainNull()
                .allSatisfy(name -> assertThat(name.trim()).isNotEmpty());
    }
}
