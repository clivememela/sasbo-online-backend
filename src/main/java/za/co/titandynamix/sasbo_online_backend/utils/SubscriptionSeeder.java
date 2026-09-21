package za.co.titandynamix.sasbo_online_backend.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;
import za.co.titandynamix.sasbo_online_backend.dto.subscription.SubscriptionsDto;
import za.co.titandynamix.sasbo_online_backend.entity.bank.Bank;
import za.co.titandynamix.sasbo_online_backend.entity.subscription.Subscriptions;
import za.co.titandynamix.sasbo_online_backend.enums.SubscriptionsStatus;
import za.co.titandynamix.sasbo_online_backend.repository.BankRepository;
import za.co.titandynamix.sasbo_online_backend.repository.SubscriptionsRepository;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Brings {@code subscriptions} up to date with {@value #SEED_FILE}: PER ROW, keyed by bank.
 *
 * <h2>🔴 What it replaces (F54)</h2>
 * {@code DataSeeder} seeded this table BY COUNT, all at once: {@code if (existing < desired)} then
 * every row of the file. So a database that already held the fifteen rows never received a
 * corrected one -- and a sixteenth row in the file would have re-inserted all sixteen beside the
 * fifteen: two ACTIVE rows per bank, and {@code BANK_SUBSCRIPTION_AMBIGUOUS} refusing every run. And a
 * row whose bank name matched nothing was written with NO bank, silently -- which is how Standard
 * Bank's row, SASBO's own collecting bank and half its file, became an orphan (register A5's
 * instance: the file said "Standard Bank of South Africa Limited", the bank on record is "The
 * Standard Bank of South Africa Limited").
 *
 * <h2>Now</h2>
 * A file row is seeded only when its bank has NO subscription row at all -- so a row a tester has
 * edited is never fought, and never doubled. A row whose bank name matches no bank on record is not
 * written, and says so. Absence is one query ({@link SubscriptionsRepository#findAllWithBank()}); the
 * banks are read only if something is missing.
 *
 * <h2>The orphan repair -- narrow, logged, never in production (SD4-G)</h2>
 * The first seeder code that CHANGES an existing row, so it acts only on certainty: exactly ONE row
 * has no bank, and exactly ONE absent row of the file matches it on every other field. Then that row
 * is given the file row's bank, in this transaction, and its id is logged. Anything else -- two
 * orphans, or an orphan that matches nothing -- is a {@code WARN}, and the orphan is left alone; a file
 * row the orphan might be is then not seeded either, because a person has to decide which it is.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionSeeder {

    static final String SEED_FILE = "data/subscriptions.json";

    private final SubscriptionsRepository subscriptions;
    private final BankRepository banks;
    private final SasboEnvironment environment;

    /** What one call did. */
    public record Outcome(int inserted, int repaired, int leftAlone) {
    }

    @Transactional
    public Outcome seed() throws IOException {
        List<SubscriptionsDto> file = readSeedFile();
        List<Subscriptions> rows = subscriptions.findAllWithBank();

        Set<String> banksWithARow = new HashSet<>();
        List<Subscriptions> orphans = new ArrayList<>();
        for (Subscriptions row : rows) {
            if (row.getBank() == null) {
                orphans.add(row);
            } else {
                banksWithARow.add(keyOf(row.getBank().getName()));
            }
        }
        List<SubscriptionsDto> absent = file.stream()
                .filter(dto -> dto.bankName() != null && !banksWithARow.contains(keyOf(dto.bankName())))
                .toList();
        if (absent.isEmpty()) {
            warnAboutOrphans(orphans, file);
            return new Outcome(0, 0, orphans.size());
        }

        Map<String, Bank> bankByName = new HashMap<>();
        for (Bank bank : banks.findAll()) {
            bankByName.putIfAbsent(keyOf(bank.getName()), bank);
        }

        List<Subscriptions> toInsert = new ArrayList<>();
        int repaired = 0;
        List<Subscriptions> stillOrphans = new ArrayList<>(orphans);
        for (SubscriptionsDto dto : absent) {
            Bank bank = bankByName.get(keyOf(dto.bankName()));
            if (bank == null) {
                log.warn("The subscription seed row for '{}' names no bank on record, so it is not seeded", dto.bankName());
                continue;
            }
            List<Subscriptions> candidates = orphans.stream().filter(orphan -> sameTerms(orphan, dto)).toList();
            if (candidates.isEmpty()) {
                toInsert.add(newRow(dto, bank));
                continue;
            }
            Subscriptions orphan = candidates.get(0);
            boolean certain = orphans.size() == 1
                    && absent.stream().filter(other -> sameTerms(orphan, other)).count() == 1;
            if (!certain) {
                log.warn("{} has no subscription row, and {} row(s) with no bank could be it -- none is changed "
                        + "and none is added; a person decides which row is {}'s", bank.getName(),
                        candidates.size(), bank.getName());
                continue;
            }
            if (environment.isProduction()) {
                log.warn("Subscription row {} has no bank and matches the seed row for {} -- left alone, because "
                        + "this is production; a person sets its bank", orphan.getId(), bank.getName());
                continue;
            }
            orphan.setBank(bank);
            stillOrphans.remove(orphan);
            repaired++;
            log.info("Subscription row {} had no bank; it is given {}, whose seed row it matches on every other "
                    + "field (A5's instance)", orphan.getId(), bank.getName());
        }
        if (!toInsert.isEmpty()) {
            subscriptions.saveAll(toInsert);
            log.info("Seeded {} subscription row(s) for banks that had none: {}", toInsert.size(),
                    toInsert.stream().map(s -> s.getBank().getName()).toList());
        }
        warnAboutOrphans(stillOrphans, file);
        return new Outcome(toInsert.size(), repaired, stillOrphans.size());
    }

    /** An orphan left behind is said once per start, with what would identify it. */
    private static void warnAboutOrphans(List<Subscriptions> orphans, List<SubscriptionsDto> file) {
        for (Subscriptions orphan : orphans) {
            long matching = file.stream().filter(dto -> sameTerms(orphan, dto)).count();
            log.warn("Subscription row {} has no bank ({} seed row(s) share its terms) -- left alone; its bank "
                    + "must be set by a person before its members can be debited", orphan.getId(), matching);
        }
    }

    /** The same terms as the file row: day, amount, core banking and status -- everything but the bank. */
    static boolean sameTerms(Subscriptions row, SubscriptionsDto dto) {
        SubscriptionsStatus status = dto.status() == null ? SubscriptionsStatus.PENDING : dto.status();
        return row.getAcbDebitDay() == dto.acbDebitDay()
                && row.getSubscriptionAmount() != null && dto.subscriptionAmount() != null
                && row.getSubscriptionAmount().compareTo(dto.subscriptionAmount()) == 0
                && Objects.equals(row.getIsCoreBanking(), dto.isCoreBanking())
                && row.getStatus() == status;
    }

    private static Subscriptions newRow(SubscriptionsDto dto, Bank bank) {
        Subscriptions row = new Subscriptions();
        row.setBank(bank);
        row.setAcbDebitDay(dto.acbDebitDay());
        row.setSubscriptionAmount(dto.subscriptionAmount());
        row.setIsCoreBanking(dto.isCoreBanking());
        row.setStatus(dto.status() == null ? SubscriptionsStatus.PENDING : dto.status());
        row.setRegistrationDate(dto.registrationDate());
        return row;
    }

    private static String keyOf(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    static List<SubscriptionsDto> readSeedFile() throws IOException {
        JsonMapper mapper = JsonMapper.builder()
                .findAndAddModules()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        try (InputStream stream = new ClassPathResource(SEED_FILE).getInputStream()) {
            return mapper.readValue(stream, new TypeReference<>() {});
        }
    }
}
