package za.co.titandynamix.sasbo_online_backend.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import za.co.titandynamix.sasbo_online_backend.entity.bank.Bank;
import za.co.titandynamix.sasbo_online_backend.repository.BankRepository;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Brings {@code banks} up to date with {@code data/banks_master.json}.
 * See {@code BankSeederIT}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BankSeeder {

    static final String SEED_FILE = "data/banks_master.json";

    private final BankRepository bankRepository;

    /**
     * Inserts every seed bank not already present, matched by name.
     *
     * <p>🔴 This was {@code if (bankRepository.count() == 0)}: a table-empty guard on a
     * per-row question (R2-F4, the job-grade defect one table over). A database that
     * already held banks never received the ones the seed file gained later.
     *
     * <p>{@code banks.name} is UNIQUE, so the name is the identity; it is compared
     * trimmed and case-folded so a differently-typed spelling is not a second bank.
     * Existing rows are never modified.
     *
     * @return how many banks were inserted
     */
    public int seed() throws IOException {
        Set<String> present = this.bankRepository.findAll().stream()
                .map(b -> keyOf(b.getName()))
                .collect(Collectors.toCollection(HashSet::new)); // mutable: add() also de-duplicates the seed file itself
        List<Bank> missing = readSeedFile().stream()
                .filter(b -> b.getName() != null && !b.getName().isBlank())
                .filter(b -> present.add(keyOf(b.getName())))
                .peek(b -> b.setId(null))
                .toList();
        if (!missing.isEmpty()) {
            this.bankRepository.saveAll(missing);
            log.info("Seeded {} bank(s) that were missing: {}", missing.size(),
                    missing.stream().map(Bank::getName).toList());
        }
        return missing.size();
    }

    private static String keyOf(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    static List<Bank> readSeedFile() throws IOException {
        JsonMapper mapper = JsonMapper.builder()
                .findAndAddModules()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        try (InputStream stream = new ClassPathResource(SEED_FILE).getInputStream()) {
            return mapper.readValue(stream, new TypeReference<>() {});
        }
    }
}
