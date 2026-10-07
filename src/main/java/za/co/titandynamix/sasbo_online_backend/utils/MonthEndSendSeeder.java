package za.co.titandynamix.sasbo_online_backend.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;
import za.co.titandynamix.sasbo_online_backend.config.WorkplaceSwitch;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndFactsRepository.TakenMonth;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndSendSettingsRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndSendSettingsRepository.BankValues;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndSendSettingsRepository.PackSetValues;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndSentRepository;
import za.co.titandynamix.sasbo_online_backend.services.MonthEndSendSeeding;
import za.co.titandynamix.sasbo_online_backend.services.MonthEndTakes;

import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The demonstration's files that leave SASBO (October build): who gets what -- the seven pack sets of Stephne's region
 * folders (MEN-02: BLM, CPT, DBN, JHB, JHB2, PE, PTA), the banks' files with MEN-33's provisional lists, the providers of
 * MEN-35 -- and, for the newest month taken, the regions' packs made, downloaded and e-mailed, and the banks' and
 * providers' files made, so a fresh stack shows every screen with something on it.
 *
 * <ul>
 *   <li>NEVER WHERE THE PRODUCTION MARKER IS SET: the addresses are invented, at example.org, and SASBO's own settings
 *       are SASBO's to enter (as SASBO's lists are, MD28);</li>
 *   <li>WHEN ABSENT, a row at a time by its code: a recipient, a bank or a provider an administrator changed is left as
 *       they left it;</li>
 *   <li>the packs and files only where the workplaces switch says {@code all} -- nothing is placed in a region below it --
 *       and only for a take that has none: a second start makes nothing again;</li>
 *   <li>Johannesburg's split -- A, F, K and X in the first set, H, N and P in the second -- is PROVISIONAL: her example
 *       set covers A, F, K and X, and how the two divide the region is MEN-02's clarification;</li>
 *   <li>the e-mails a seeded pack shows were never sent -- nothing is sent from a seed. The record says so: its actor is
 *       {@value DemonstrationMemberSeeder#SEED_ACTOR}.</li>
 * </ul>
 */
@Slf4j
@Component
public class MonthEndSendSeeder {

    /** The position every region's pack goes to (MEN-02: "Regional Secretaries'"). */
    static final String POSITION = "Regional Secretary";

    /** A pack set of the demonstration: Stephne's region folders, by the cities she wrote them as. */
    record SeedSet(String code, String label, String region, List<String> areas) {
    }

    static final List<SeedSet> SETS = List.of(
            new SeedSet("BLM", "Bloemfontein", "BLM", List.of()),
            new SeedSet("CPT", "Cape Town", "CPT", List.of()),
            new SeedSet("DBN", "Durban", "DBN", List.of()),
            new SeedSet("JHB", "Johannesburg (first set)", "JHB", List.of("A", "F", "K", "X")),
            new SeedSet("JHB2", "Johannesburg (second set)", "JHB", List.of("H", "N", "P")),
            new SeedSet("PE", "Port Elizabeth", "PE", List.of()),
            new SeedSet("PTA", "Pretoria", "PTA", List.of()));

    /** A bank's file of the demonstration: MEN-33's lists, the table codes of SASBO's own list and procedure. */
    record SeedBank(String code, String name, List<Integer> tableCodes, boolean fullList, String layout) {
    }

    static final List<SeedBank> BANKS = List.of(
            new SeedBank("ABSA", "Absa", List.of(24, 78, 90), true, "ABSA_FIXED_WIDTH"),
            new SeedBank("STANDARD_BANK", "Standard Bank", List.of(5, 6), true, "SHEET"),
            new SeedBank("ACCESS", "Access Bank", List.of(11, 88), true, "SHEET"),
            new SeedBank("NATIONAL_HOUSING", "National Housing Finance Corporation", List.of(157), true, "SHEET"),
            new SeedBank("SBV", "SBV Services", List.of(10), true, "SHEET"),
            new SeedBank("NEDBANK", "Nedbank", List.of(13, 14, 86, 114), false, "SHEET"),
            new SeedBank("FNB", "FirstRand (FNB)", List.of(1, 2, 4, 134), false, "SHEET"),
            new SeedBank("CAPITEC", "Capitec", List.of(8, 9, 43), false, "SHEET"),
            new SeedBank("OLD_MUTUAL", "Old Mutual", List.of(50, 51), false, "SHEET"),
            new SeedBank("AFRICAN_BANK", "African Bank", List.of(44), false, "SHEET"),
            new SeedBank("SA_RESERVE_BANK", "SA Reserve Bank", List.of(52), false, "SHEET"),
            new SeedBank("LAND_BANK", "Land Bank", List.of(93), false, "SHEET"),
            new SeedBank("SANTAM", "Santam", List.of(69), false, "SHEET"),
            new SeedBank("UBANK", "Ubank", List.of(107), false, "SHEET"),
            new SeedBank("BANKSERV", "Bankserv", List.of(32), false, "SHEET"));

    /** A provider of the demonstration: MEN-35's two live lists and two suspended benefits. */
    record SeedProvider(String code, String name, String layout, String delivery, boolean active) {
    }

    static final List<SeedProvider> PROVIDERS = List.of(
            new SeedProvider("OM_FUNERAL", "Old Mutual funeral benefit", "OLD_MUTUAL_FUNERAL", "DOWNLOAD", true),
            new SeedProvider("LAW_CALL", "Law Call (Azoza)", "PROVISIONAL_MEMBER_LIST", "SFTP", true),
            new SeedProvider("CRITICARE", "Criticare", "PROVISIONAL_MEMBER_LIST", "DOWNLOAD", false),
            new SeedProvider("HOLIDAY", "Holiday benefit", "PROVISIONAL_MEMBER_LIST", "DOWNLOAD", false));

    /** The seeded banks whose files the demonstration makes, and the recipient whose e-mail is refused. */
    static final Set<String> FILES_MADE = Set.of("ABSA", "STANDARD_BANK", "SBV", "NEDBANK", "CAPITEC");
    static final String MAIL_REFUSED_FOR = "JHB2";

    private final SasboEnvironment environment;
    private final WorkplaceSwitch workplaceSwitch;
    private final MonthEndSendSettingsRepository settings;
    private final MonthEndSentRepository sent;
    private final MonthEndTakes takes;
    private final MonthEndSendSeeding seeding;
    private final TransactionTemplate tx;
    private final Clock clock;

    public MonthEndSendSeeder(SasboEnvironment environment, WorkplaceSwitch workplaceSwitch,
                              MonthEndSendSettingsRepository settings, MonthEndSentRepository sent, MonthEndTakes takes,
                              MonthEndSendSeeding seeding, PlatformTransactionManager transactions, Clock clock) {
        this.environment = environment;
        this.workplaceSwitch = workplaceSwitch;
        this.settings = settings;
        this.sent = sent;
        this.takes = takes;
        this.seeding = seeding;
        this.tx = new TransactionTemplate(transactions);
        this.clock = clock;
    }

    /** @return how many rows of settings, packs and files this start wrote */
    public int seed() {
        if (environment.isProduction()) {
            return 0;
        }
        int wrote = settings();
        if (!workplaceSwitch.staffNumbersServed()) {
            return wrote;
        }
        Optional<TakenMonth> newest = takes.takenMonths().stream().findFirst();
        if (newest.isEmpty()) {
            return wrote;
        }
        String month = YearMonth.from(newest.get().monthStart()).toString();
        if (sent.regionPacksOf(newest.get().id()).isEmpty()) {
            wrote += seeding.packs(month, newest.get().takenAt(), MAIL_REFUSED_FOR);
        }
        if (sent.filesOf(newest.get().id()).isEmpty()) {
            wrote += seeding.files(month, newest.get().takenAt(), FILES_MADE);
        }
        if (wrote > 0) {
            log.info("Seeded {} row(s) of the month's recipients, packs and files", wrote);
        }
        return wrote;
    }

    /** The recipients, banks and providers, each when absent by its code. */
    private int settings() {
        int[] wrote = {0};
        for (SeedSet set : SETS) {
            if (settings.packSet(set.code()).isPresent() || !settings.regionOnRecord(set.region())) {
                continue;
            }
            List<String> inRegion = settings.areasOf(set.region());
            List<String> areas = set.areas().stream().filter(inRegion::contains).toList();
            if (!set.areas().isEmpty() && areas.isEmpty()) {
                continue;
            }
            try {
                settings.insertPackSet(set.code(), new PackSetValues(set.label(), set.region(), areas, POSITION,
                        "regional.secretary." + set.code().toLowerCase(java.util.Locale.ROOT) + "@example.org", true),
                        DemonstrationMemberSeeder.SEED_ACTOR, clock.instant());
                wrote[0]++;
            } catch (RuntimeException another) {
                // Another start seeded it between the look and the write: the unique code decides.
                log.debug("A pack set was seeded meanwhile");
            }
        }
        for (SeedBank bank : BANKS) {
            if (settings.bank(bank.code()).isPresent()) {
                continue;
            }
            List<Integer> codes = bank.tableCodes().stream().filter(settings.tableCodesOnRecord(bank.tableCodes())
                    ::contains).toList();
            if (codes.isEmpty() || !settings.heldElsewhere(codes, bank.code()).isEmpty()) {
                continue;
            }
            try {
                tx.executeWithoutResult(status -> {
                    long id = settings.insertBank(bank.code(), new BankValues(bank.name(), codes, bank.fullList(),
                            bank.layout(), true), DemonstrationMemberSeeder.SEED_ACTOR, clock.instant());
                    settings.replaceTableCodes(id, codes);
                });
                wrote[0]++;
            } catch (RuntimeException another) {
                log.debug("A bank's file was seeded meanwhile");
            }
        }
        for (SeedProvider provider : PROVIDERS) {
            if (settings.provider(provider.code()).isPresent()) {
                continue;
            }
            try {
                settings.insertProvider(provider.code(), provider.name(), provider.layout(), provider.delivery(),
                        provider.active(), DemonstrationMemberSeeder.SEED_ACTOR, clock.instant());
                wrote[0]++;
            } catch (RuntimeException another) {
                log.debug("A provider was seeded meanwhile");
            }
        }
        return wrote[0];
    }
}
