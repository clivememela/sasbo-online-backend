package za.co.titandynamix.sasbo_online_backend.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.core.io.ClassPathResource;
import za.co.titandynamix.sasbo_online_backend.entity.bank.Bank;
import za.co.titandynamix.sasbo_online_backend.repository.BankRepository;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * {@code data/job_grades.json}, read once, for the tests that need it as a fixture.
 *
 * <h2>Why this is shared rather than copied</h2>
 * Two integration tests need the same two things — the seed rows, and the banks those
 * rows name, because {@link za.co.titandynamix.sasbo_online_backend.utils.JobGradeSeeder}
 * resolves a grade's bank BY NAME and the banks are themselves seeded at startup by a
 * runner the integration lane mocks. {@code JobGradeSeederIT} is about the upsert;
 * {@code ReferenceDataIT} needs the table populated to exercise a repository query
 * against it. The second consumer is what earned the extraction.
 *
 * <p>🔑 <b>And no count lives here.</b> Every caller derives its expectation from
 * {@link #rows()}. A literal row count in a test goes stale the first time a grade is
 * added, and the test is then edited to match instead of read — which is how
 * {@code ReferenceDataIT} came to assert a number that was never true in its own lane.
 */
public final class JobGradeSeedFile {

    private JobGradeSeedFile() {
    }

    /** Every row of the seed file, in file order. */
    public static List<Map<String, Object>> rows() throws IOException {
        JsonMapper mapper = JsonMapper.builder()
                .findAndAddModules()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        try (InputStream stream = new ClassPathResource("data/job_grades.json").getInputStream()) {
            List<LinkedHashMap<String, Object>> read =
                    mapper.readValue(stream, new TypeReference<>() {});
            return List.copyOf(read);
        }
    }

    /** The distinct bank names the seed rows refer to, in first-seen order. */
    public static Set<String> bankNames(List<Map<String, Object>> rows) {
        Set<String> names = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            names.add(String.valueOf(row.get("bankName")));
        }
        return names;
    }

    /**
     * Saves a bank per name the seed refers to, so the seeder can resolve them.
     *
     * <p>Branch codes are distinct per bank: a shared code is some other test's
     * subject, and reusing one here would make a failure in this fixture look like a
     * failure in the thing under test.
     *
     * @return the saved banks, by lower-cased name
     */
    public static Map<String, Bank> createBanksFor(BankRepository banks,
                                                   List<Map<String, Object>> rows) {
        Map<String, Bank> saved = new LinkedHashMap<>();
        int branchCode = 630000;
        for (String name : bankNames(rows)) {
            Bank bank = new Bank();
            bank.setName(name);
            bank.setUniversalBranchCode(String.valueOf(branchCode++));
            bank.setBranches(new ArrayList<>());
            saved.put(name.toLowerCase(Locale.ROOT), banks.save(bank));
        }
        return saved;
    }
}
