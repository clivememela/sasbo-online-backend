package za.co.titandynamix.sasbo_online_backend.utils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;
import za.co.titandynamix.sasbo_online_backend.config.WorkplaceSwitch;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.enums.MemberStatusReason;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.repository.SasboReferenceRepository;
import za.co.titandynamix.sasbo_online_backend.repository.SeedMemberRepository;
import za.co.titandynamix.sasbo_online_backend.repository.WorkplaceEntityRepository;
import za.co.titandynamix.sasbo_online_backend.services.MemberService;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Month end's rules, seeded so a fresh stack shows them working (October build): invented members who stand at SASBO's
 * workplaces and are suspended within several banks under her codes -- 11, 12 and 21 with a reason for no payroll
 * deduction (MEN-17), the debit codes beside them -- some of them lapsed (MEN-18); a member who left and rejoined in
 * the same month and kept their membership (MEN-23), and one who rejoined a month later; a member who left, whose
 * capture again is refused (MEN-19); and a workplace with more members than its staff complement (MEN-26).
 *
 * <h2>From a committed file, written by a generator in the test sources</h2>
 * {@value #SEED_FILE}, as the invented members of 01.4 are (MD33): numbered downwards from M-096000 in the block the
 * parent gave this stream (M-095801 to M-096000), every number in {@code SeedMemberNumbers}. Invented people only.
 *
 * <h2>When absent, never in production, only where the switch says all</h2>
 * The members stand at workplaces, so only where SASBO's workplaces are served in full (MD53); never where the
 * production marker is set. A member is put on record when absent by number; their past is written once, at the first
 * start that can, through the real status change with the actor {@value DemonstrationMemberSeeder#SEED_ACTOR}, each
 * change dated in a month before the clock's -- the clock's own month is left for live captures.
 *
 * <h2>What they need on every non-production stack</h2>
 * The suspension codes the file uses, as her sheet words them (Stephne, DOD-01), where absent -- production loads her
 * sheet itself; her reasons for no payroll deduction and the three core tiers, where a database built from the
 * entities lacks what V79 writes; and the PROVISIONAL tier of each table code from her Core List (MEN-31), only where
 * nobody has set a code's tier. Each by key, never "table empty".
 */
@Slf4j
@Component
public class MonthEndRulesSeeder {

    /** The generated population. */
    public static final String SEED_FILE = "data/month_end_rules_members.json";

    /** Who set a table code's tier provisionally. */
    public static final String TIER_SETTER = "seed";

    /**
     * Stephne's suspension codes the file uses, in her sheet's words, upper case as SASBO's lists are printed (DOD-01;
     * 12 and 21 as the dod stream's migration writes them).
     */
    static final Map<Integer, String> CODES = codes();

    private static Map<Integer, String> codes() {
        Map<Integer, String> codes = new LinkedHashMap<>();
        codes.put(11, "NOT ON PAYROLL");
        codes.put(12, "MATERNITY LEAVE");
        codes.put(13, "DEBIT RETURNED");
        codes.put(15, "ACCOUNT CLOSED");
        codes.put(16, "PAYMENT STOPPED");
        codes.put(18, "INSUFFICIENT FUNDS");
        codes.put(19, "INVALID ACCOUNT");
        codes.put(21, "SICK LEAVE");
        codes.put(30, "NO AUTHORITY TO DEBIT ACCOUNT");
        return codes;
    }

    /** Her reasons for no payroll deduction (MEN-17), as V79 writes them: name, code, place, provisional. */
    static final List<Object[]> REASONS = List.of(
            new Object[] {"Unpaid leave", 11, 1, true},
            new Object[] {"Maternity leave", 12, 2, false},
            new Object[] {"Paternity leave", 11, 3, true},
            new Object[] {"Insufficient funds", 11, 4, true},
            new Object[] {"Sick leave", 21, 5, false});

    /** The three core tiers, as V79 writes them. */
    static final List<Object[]> TIERS = List.of(new Object[] {"A", "Core A", 1}, new Object[] {"B", "Core B", 2},
            new Object[] {"C", "Core C", 3});

    /**
     * The PROVISIONAL tier of each table code, from her Core List (MEN-31, {@code answers/MEN-attachments.md} section
     * 5): Core A -- Absa (her 25 read as 24, the code on record: Friday item 17), 78, 90; FNB 1, 2, 4, 134; Standard
     * Bank 5, 6; Nedbank 13, 14, 36, 114 (her 35 and 109 are no table code on record); Old Mutual 50; Capitec, which
     * her list shows with no code, by its employers' codes 43, 8, 9. Core B -- African Bank, shown with none, 44;
     * Landbank 93; SBV 10; SA Reserve Bank 52; Santam 69; Ubank 107. Core C -- Access 11, 88; Bankserv 32; National
     * Housing, shown with none, 157. Every other code -- Imperial Bank 86 and Old Mutual Finance 51 among them, on the
     * groups but not on her list -- is one of "all other", the individual institutions. The Senior Secretariat decides.
     */
    static final Map<Integer, String> PROVISIONAL_TIERS = provisionalTiers();

    private static Map<Integer, String> provisionalTiers() {
        Map<Integer, String> tiers = new LinkedHashMap<>();
        for (int code : new int[] {24, 78, 90, 1, 2, 4, 134, 5, 6, 13, 14, 36, 114, 50, 43, 8, 9}) {
            tiers.put(code, "A");
        }
        for (int code : new int[] {44, 93, 10, 52, 69, 107}) {
            tiers.put(code, "B");
        }
        for (int code : new int[] {11, 88, 32, 157}) {
            tiers.put(code, "C");
        }
        return tiers;
    }

    /** The file: who wrote it and from what, how many members it holds, the members, and their past. */
    public record SeedFile(String generator, long seed, int count, String holds, List<DemonstrationMember> members,
                           List<Change> changes) {
    }

    /**
     * One change of a member's past, in the month {@code monthsBack} before the clock's, on {@code day}: a suspension
     * with SASBO's code -- and, under 11, 12 or 21, the reason for no payroll deduction by its name, or a note alone --
     * a leaving with a deletion code and the reason under it, or a reinstatement, which UNDOES the leaving of its own
     * month where {@code undoesTheLeaving} (MEN-23).
     */
    public record Change(String membershipNumber, int monthsBack, int day, String toStatus, String reason,
                         Integer deletionCode, String reasonUnderCode, Integer suspensionCode, String noDeductionReason,
                         String note, boolean undoesTheLeaving) {
    }

    private final SeedMemberRepository seedMembers;
    private final MemberRepository memberRepository;
    private final WorkplaceEntityRepository workplaces;
    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final SasboEnvironment environment;
    private final WorkplaceSwitch workplaceSwitch;
    private final MemberService members;
    private final SasboReferenceRepository codes;
    private final Clock clock;

    public MonthEndRulesSeeder(SeedMemberRepository seedMembers, MemberRepository memberRepository,
                               WorkplaceEntityRepository workplaces, NamedParameterJdbcTemplate jdbc,
                               PlatformTransactionManager transactionManager, SasboEnvironment environment,
                               WorkplaceSwitch workplaceSwitch, MemberService members, SasboReferenceRepository codes,
                               Clock clock) {
        this.seedMembers = seedMembers;
        this.memberRepository = memberRepository;
        this.workplaces = workplaces;
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
        this.environment = environment;
        this.workplaceSwitch = workplaceSwitch;
        this.members = members;
        this.codes = codes;
        this.clock = clock;
    }

    /**
     * Everything above, in order: the lists, the provisional tiers, the members, their past.
     *
     * @return how many rows this call wrote -- members and changes; the lists' rows are not counted
     */
    public int seed() throws IOException {
        if (environment.isProduction()) {
            return 0;
        }
        lists();
        tiers();
        if (!workplaceSwitch.staffNumbersServed()) {
            return 0;
        }
        SeedFile file = readSeedFile();
        int written = put(file);
        written += past(file);
        return written;
    }

    // ------------------------------------------------------------------------------------------------ the lists

    /** The codes, the reasons and the tiers the members need, each where absent, by its key. */
    void lists() {
        Set<Long> codesOnRecord = new HashSet<>(jdbc.queryForList("SELECT code FROM suspension_codes",
                new MapSqlParameterSource(), Long.class));
        List<MapSqlParameterSource> missingCodes = new ArrayList<>();
        CODES.forEach((code, name) -> {
            if (!codesOnRecord.contains((long) code)) {
                missingCodes.add(new MapSqlParameterSource("code", code).addValue("name", name));
            }
        });
        insertEach("INSERT INTO suspension_codes (code, name) VALUES (:code, :name)", missingCodes);

        Set<String> reasonsOnRecord = new HashSet<>();
        jdbc.query("SELECT name FROM no_deduction_reasons", rs -> {
            reasonsOnRecord.add(rs.getString("name").trim().toLowerCase(Locale.ROOT));
        });
        List<MapSqlParameterSource> missingReasons = new ArrayList<>();
        for (Object[] reason : REASONS) {
            if (!reasonsOnRecord.contains(((String) reason[0]).toLowerCase(Locale.ROOT))) {
                missingReasons.add(new MapSqlParameterSource("name", reason[0]).addValue("code", reason[1])
                        .addValue("ordinal", reason[2]).addValue("provisional", reason[3])
                        .addValue("at", Timestamp.from(clock.instant())));
            }
        }
        insertEach("INSERT INTO no_deduction_reasons (name, suspension_code, ordinal, active, provisional, version, "
                + "updated_by, updated_at) VALUES (:name, :code, :ordinal, TRUE, :provisional, 0, 'seed', :at)",
                missingReasons);

        Set<String> tiersOnRecord = new HashSet<>(jdbc.queryForList("SELECT code FROM core_tiers",
                new MapSqlParameterSource(), String.class));
        List<MapSqlParameterSource> missingTiers = new ArrayList<>();
        for (Object[] tier : TIERS) {
            if (!tiersOnRecord.contains((String) tier[0])) {
                missingTiers.add(new MapSqlParameterSource("code", tier[0]).addValue("name", tier[1])
                        .addValue("ordinal", tier[2]));
            }
        }
        insertEach("INSERT INTO core_tiers (code, name, ordinal) VALUES (:code, :name, :ordinal)", missingTiers);
    }

    /** One insert a row, each in a transaction of its own: a row another start wrote meanwhile is left as it is. */
    private void insertEach(String statement, List<MapSqlParameterSource> rows) {
        for (MapSqlParameterSource row : rows) {
            try {
                transactions.executeWithoutResult(status -> jdbc.update(statement, row));
            } catch (DataIntegrityViolationException meanwhile) {
                log.info("A row of month end's rules was put on record by another start at the same moment");
            }
        }
    }

    /**
     * The provisional tier of each table code on record that nobody has set -- one statement a code, in one batch. A
     * code an administrator set, to a tier or to none, is never touched again.
     *
     * @return how many table codes were given their provisional tier
     */
    int tiers() {
        MapSqlParameterSource[] batch = PROVISIONAL_TIERS.entrySet().stream()
                .map(e -> new MapSqlParameterSource("code", e.getKey()).addValue("tier", e.getValue())
                        .addValue("by", TIER_SETTER).addValue("at", Timestamp.from(clock.instant())))
                .toArray(MapSqlParameterSource[]::new);
        int[] updated = jdbc.batchUpdate("UPDATE table_codes SET core_tier_id = (SELECT ct.id FROM core_tiers ct WHERE "
                + "ct.code = :tier), core_tier_by = :by, core_tier_at = :at WHERE code = :code AND core_tier_by IS NULL",
                batch);
        int set = 0;
        for (int rows : updated) {
            set += Math.max(rows, 0);
        }
        if (set > 0) {
            log.info("Gave {} table code(s) their provisional core tier", set);
        }
        return set;
    }

    // ------------------------------------------------------------------------------------------------ the members

    /** The file's members not on record, by number -- each at their workplace. */
    private int put(SeedFile file) {
        Set<String> present = new HashSet<>(seedMembers.findMembershipNumbersIn(
                file.members().stream().map(DemonstrationMember::membershipNumber).toList()));
        List<DemonstrationMember> missing = file.members().stream()
                .filter(row -> !present.contains(row.membershipNumber())).toList();
        if (missing.isEmpty()) {
            return 0;
        }
        Map<Integer, Long> onRecord = new HashMap<>();
        jdbc.query("SELECT id, ra_number FROM workplaces WHERE ra_number IN (:ra)", new MapSqlParameterSource("ra",
                missing.stream().map(DemonstrationMember::workplaceRaNumber).distinct().toList()), rs -> {
                    onRecord.put(rs.getInt("ra_number"), rs.getLong("id"));
                });
        List<Member> toSave = new ArrayList<>();
        try {
            transactions.executeWithoutResult(status -> {
                for (DemonstrationMember row : missing) {
                    Long workplace = onRecord.get(row.workplaceRaNumber());
                    if (workplace == null) {
                        continue;
                    }
                    Member member = row.toMember();
                    member.setWorkplace(workplaces.getReferenceById(workplace));
                    toSave.add(member);
                }
                memberRepository.saveAll(toSave);
            });
        } catch (DataIntegrityViolationException meanwhile) {
            log.info("Month end's rule members were put on record by another start at the same moment");
            return 0;
        }
        log.info("Seeded {} member(s) for month end's rules from {}", toSave.size(), SEED_FILE);
        return toSave.size();
    }

    /**
     * Each member's past, written once: through the real status change, dated in its month, by the seed. A member with
     * a change by the seed already is left alone -- the whole of their past was written by the start that wrote any.
     */
    private int past(SeedFile file) {
        Map<String, Integer> ids = new HashMap<>();
        jdbc.query("SELECT id, membership_number FROM members WHERE membership_number IN (:numbers)",
                new MapSqlParameterSource("numbers", file.changes().stream().map(Change::membershipNumber)
                        .distinct().toList()), rs -> {
                    ids.put(rs.getString("membership_number"), rs.getInt("id"));
                });
        if (ids.isEmpty()) {
            return 0;
        }
        Set<Integer> written = new HashSet<>(jdbc.queryForList("SELECT DISTINCT member_id FROM member_status_changes "
                + "WHERE changed_by = :seed AND member_id IN (:ids)", new MapSqlParameterSource("seed",
                DemonstrationMemberSeeder.SEED_ACTOR).addValue("ids", ids.values()), Integer.class));
        Map<String, Long> reasons = new HashMap<>();
        jdbc.query("SELECT id, name FROM no_deduction_reasons", rs -> {
            reasons.put(rs.getString("name").trim().toLowerCase(Locale.ROOT), rs.getLong("id"));
        });
        YearMonth now = MonthRange.current(clock);
        int changed = 0;
        for (Change change : file.changes()) {
            Integer id = ids.get(change.membershipNumber());
            if (id == null || written.contains(id)) {
                continue;
            }
            Long deletion = change.deletionCode() == null ? null
                    : codes.deletionCode(change.deletionCode()).map(SasboReferenceRepository.Code::id).orElse(null);
            if (deletion != null && change.reasonUnderCode() != null) {
                deletion = codes.reasonUnder(deletion, change.reasonUnderCode()).orElse(deletion);
            }
            Long suspension = change.suspensionCode() == null ? null
                    : codes.suspensionCode(change.suspensionCode()).map(SasboReferenceRepository.Code::id).orElse(null);
            if ((change.deletionCode() != null && deletion == null)
                    || (change.suspensionCode() != null && suspension == null)) {
                continue;
            }
            Long noDeduction = change.noDeductionReason() == null ? null
                    : reasons.get(change.noDeductionReason().toLowerCase(Locale.ROOT));
            YearMonth month = now.minusMonths(change.monthsBack());
            Instant at = month.atDay(change.day()).atTime(10, 0).atZone(MonthRange.ZONE).toInstant();
            Instant undoesSince = change.undoesTheLeaving() ? MonthRange.of(month).start() : null;
            boolean wrote = members.changeStatus(id, new MemberService.StatusChange(change.toStatus(), null,
                    MemberStatusReason.valueOf(change.reason()), deletion, suspension, change.note(),
                    DemonstrationMemberSeeder.SEED_ACTOR, at, noDeduction, undoesSince))
                    .map(outcome -> outcome.written() != null).orElse(false);
            changed += wrote ? 1 : 0;
        }
        log.info("Wrote {} change(s) of month end's rule members' past from {}", changed, SEED_FILE);
        return changed;
    }

    /** The file, read STRICTLY: a key the record does not declare fails the read, instead of losing a field (F65). */
    public static SeedFile readSeedFile() throws IOException {
        JsonMapper strict = JsonMapper.builder()
                .findAndAddModules()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        try (InputStream stream = new ClassPathResource(SEED_FILE).getInputStream()) {
            return strict.readValue(stream, SeedFile.class);
        }
    }
}
