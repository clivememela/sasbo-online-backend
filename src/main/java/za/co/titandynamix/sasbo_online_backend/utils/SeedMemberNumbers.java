package za.co.titandynamix.sasbo_online_backend.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * Every membership number the seed resources list -- read from the classpath once, at start-up.
 *
 * <h2>🔒 An exact list, never a numeric block</h2>
 * It tells a seeded (synthetic) member from a real one, for the one decision that must never be
 * wrong: whether a bureau file may carry them where the production marker is set
 * ({@code FILE_HAS_SEED_MEMBERS}). The seed's numbers happen to sit in one block, M-099851 to
 * M-100160 -- but SASBO's own numbers are seven digits and nobody has decided how a migrated member's
 * number is stored, so a range test could refuse a REAL member whose number falls inside it, on the
 * file that collects the union's income (SD4-E). A number is seeded only if a seed file names it.
 */
@Component
public class SeedMemberNumbers {

    /** The seed files that put members on record. */
    static final String[] SEED_FILES = {"data/members.json", DebitOrderMemberSeeder.SEED_FILE,
            DemonstrationMemberSeeder.SEED_FILE};

    private final Set<String> numbers;

    public SeedMemberNumbers() {
        Set<String> found = new TreeSet<>();
        ObjectMapper json = new ObjectMapper();
        for (String file : SEED_FILES) {
            try (InputStream stream = new ClassPathResource(file).getInputStream()) {
                JsonNode root = json.readTree(stream);
                JsonNode members = root.isArray() ? root : root.path("members");
                for (JsonNode member : members) {
                    String number = member.path("membershipNumber").asText("");
                    if (!number.isBlank()) {
                        found.add(number.trim());
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException("could not read the seed file " + file, e);
            }
        }
        if (found.isEmpty()) {
            throw new IllegalStateException("the seed files list no membership numbers -- the guard would see nothing");
        }
        this.numbers = Collections.unmodifiableSet(found);
    }

    /** The numbers, sorted. */
    public Set<String> all() {
        return numbers;
    }

    /** Whether {@code membershipNumber} is one a seed file lists -- exactly, after trimming. */
    public boolean isSeeded(String membershipNumber) {
        return membershipNumber != null && numbers.contains(membershipNumber.trim());
    }
}
