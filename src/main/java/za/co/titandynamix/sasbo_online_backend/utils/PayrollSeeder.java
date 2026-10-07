package za.co.titandynamix.sasbo_online_backend.utils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollFileSummaryDto;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollPaymentRequest;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollTypedEntryRequest;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.entity.payroll.PayrollDiscrepancy;
import za.co.titandynamix.sasbo_online_backend.entity.payroll.PayrollEmployer;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollDelivery;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollDiscrepancyType;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollOutcome;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollKeys;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollLayout;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollLayouts;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollMatcher;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollMoney;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollSampleWriter;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollDiscrepancyRepository;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollEmployerRepository;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollStore;
import za.co.titandynamix.sasbo_online_backend.repository.SeedMemberRepository;
import za.co.titandynamix.sasbo_online_backend.services.PayrollDiscrepancies;
import za.co.titandynamix.sasbo_online_backend.services.PayrollFiles;
import za.co.titandynamix.sasbo_online_backend.services.PayrollPayments;
import za.co.titandynamix.sasbo_online_backend.services.PayrollQueries;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Payroll's seed, in two parts.
 *
 * <h2>The employers -- everywhere</h2>
 * The sixteen employers Stephne listed (PAY-01) with their layouts and fees ({@value #EMPLOYERS_RESOURCE}) are
 * reference data, seeded on every database, production included, as the banks are: when absent by code, a layout
 * brought up to date when the resource's is newer (an administrator's fee and column mapping are never touched), and
 * each employer linked to SASBO's employers of V53 where those are on record -- again at every start until they are.
 *
 * <h2>The demonstration -- never under the production marker</h2>
 * Invented members of the seed's own block (M-095001 to M-095400, {@value #SEED_FILE}) on payroll deduction at Absa,
 * Capitec, FNB &amp; Wesbank, Standard Bank and GBS -- whatever the workplace switch says, as the debit-order seed is.
 * Then, for each of those employers with no file of the seed's yet, two months of files in the employer's own layout
 * (GBS's typed in, PAY-03), written from WHOEVER IS ON RECORD at that employer at the time -- the seed's members and any
 * invented population the demonstration has -- each loaded and compared through the same service as an upload, with
 * every kind of discrepancy Stephne named planted once (the roles of {@value #SEED_FILE}). The month before last is
 * then worked as an administrator would: some discrepancies resolved with notes, a query raised, the payments recorded
 * -- Absa's short by its 3% fee, which the payment check expects (PAY-27). Last month is left for the demonstration.
 */
@Slf4j
@Component
public class PayrollSeeder {

    /** The seed's invented payroll members and the people on the demonstration's files who are no members. */
    public static final String SEED_FILE = "data/payroll_members.json";

    /** The employers and their profiles. */
    public static final String EMPLOYERS_RESOURCE = "payroll/employers.json";

    /** Who loaded the demonstration's files and worked them. */
    public static final String SEED_ACTOR = "seed";

    /** The employers the demonstration loads files for, in the order they are loaded. */
    static final List<String> DEMONSTRATED = List.of("ABSA", "CAPITEC", "FNB_WESBANK", "STANDARD_BANK", "GBS");

    private static final ObjectMapper JSON = new ObjectMapper();

    private final PayrollEmployerRepository employers;
    private final SeedMemberRepository seedMembers;
    private final MemberRepository memberRepository;
    private final PayrollDiscrepancyRepository discrepancyRepository;
    private final PayrollStore store;
    private final PayrollFiles files;
    private final PayrollDiscrepancies discrepancies;
    private final PayrollQueries queries;
    private final PayrollPayments payments;
    private final NamedParameterJdbcTemplate jdbc;
    private final SasboEnvironment environment;
    private final za.co.titandynamix.sasbo_online_backend.config.WorkplaceSwitch workplaceSwitch;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public PayrollSeeder(PayrollEmployerRepository employers, SeedMemberRepository seedMembers,
                         MemberRepository memberRepository, PayrollDiscrepancyRepository discrepancyRepository,
                         PayrollStore store, PayrollFiles files, PayrollDiscrepancies discrepancies,
                         PayrollQueries queries, PayrollPayments payments, NamedParameterJdbcTemplate jdbc,
                         SasboEnvironment environment,
                         za.co.titandynamix.sasbo_online_backend.config.WorkplaceSwitch workplaceSwitch, Clock clock,
                         PlatformTransactionManager transactionManager) {
        this.employers = employers;
        this.seedMembers = seedMembers;
        this.memberRepository = memberRepository;
        this.discrepancyRepository = discrepancyRepository;
        this.store = store;
        this.files = files;
        this.discrepancies = discrepancies;
        this.queries = queries;
        this.payments = payments;
        this.jdbc = jdbc;
        this.environment = environment;
        this.workplaceSwitch = workplaceSwitch;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    // ---------------------------------------------------------------------------------------------- the employers

    /** @return how many employers were put on record by this call */
    public int seedEmployers() {
        JsonNode root;
        try (InputStream stream = new ClassPathResource(EMPLOYERS_RESOURCE).getInputStream()) {
            root = JSON.readTree(stream);
        } catch (IOException unreadable) {
            throw new java.io.UncheckedIOException("the payroll employers could not be read", unreadable);
        }
        int created = 0;
        int ordinal = 0;
        for (JsonNode node : root.path("employers")) {
            ordinal++;
            String code = node.path("code").asText();
            PayrollLayout layout = PayrollLayouts.byKey(node.path("layout").asText()).orElseThrow(() ->
                    new IllegalStateException(EMPLOYERS_RESOURCE + " names a layout that " + PayrollLayouts.RESOURCE
                            + " does not have"));
            Optional<PayrollEmployer> existing = employers.findByCode(code);
            PayrollEmployer employer;
            if (existing.isEmpty()) {
                employer = new PayrollEmployer();
                employer.setCode(code);
                employer.setName(node.path("name").asText());
                employer.setOrdinal(ordinal);
                employer.setDelivery(PayrollDelivery.valueOf(node.path("delivery").asText()));
                employer.setReadable(node.path("readable").asBoolean());
                employer.setLayoutKey(layout.key());
                employer.setLayoutVersion(layout.version());
                employer.setLayout(layout.toJson());
                employer.setAdminFeePercent(new BigDecimal(node.path("adminFeePercent").asText()));
                try {
                    employer = employers.saveAndFlush(employer);
                    created++;
                } catch (DataIntegrityViolationException seededMeanwhile) {
                    log.info("A payroll employer was put on record by another instance starting at the same time");
                    continue;
                }
            } else {
                employer = existing.get();
                if (employer.getLayoutKey().equals(layout.key()) && employer.getLayoutVersion() < layout.version()) {
                    // A refined layout reaches a database holding an older one; the fee and the mapping stay as set.
                    employer.setLayoutVersion(layout.version());
                    employer.setLayout(layout.toJson());
                    employers.saveAndFlush(employer);
                }
            }
            link(employer.getId(), node.path("sasboEmployers"));
            names(employer.getId(), node.path("names"));
        }
        if (created > 0) {
            log.info("Seeded {} payroll employer(s) from {}", created, EMPLOYERS_RESOURCE);
        }
        return created;
    }

    /** Links to SASBO's employers of V53, by name, where they are on record and not linked yet. */
    private void link(long payrollEmployerId, JsonNode names) {
        for (JsonNode name : names) {
            List<Long> ids = jdbc.queryForList("SELECT id FROM employers WHERE name = :name",
                    new MapSqlParameterSource("name", name.asText()), Long.class);
            for (Long id : ids) {
                Integer linked = jdbc.queryForObject("SELECT count(*) FROM payroll_employer_employers "
                        + "WHERE payroll_employer_id = :payroll AND employer_id = :employer",
                        new MapSqlParameterSource("payroll", payrollEmployerId).addValue("employer", id), Integer.class);
                if (linked != null && linked == 0) {
                    jdbc.update("INSERT INTO payroll_employer_employers (payroll_employer_id, employer_id) "
                            + "VALUES (:payroll, :employer)",
                            new MapSqlParameterSource("payroll", payrollEmployerId).addValue("employer", id));
                }
            }
        }
    }

    /** The employer texts a member's record may carry, upper-cased and trimmed, where not on record yet. */
    private void names(long payrollEmployerId, JsonNode names) {
        for (JsonNode name : names) {
            String key = name.asText().trim().toUpperCase(Locale.ROOT);
            Integer present = jdbc.queryForObject("SELECT count(*) FROM payroll_employer_names WHERE name_key = :key",
                    new MapSqlParameterSource("key", key), Integer.class);
            if (present != null && present == 0) {
                jdbc.update("INSERT INTO payroll_employer_names (payroll_employer_id, name_key) VALUES (:payroll, :key)",
                        new MapSqlParameterSource("payroll", payrollEmployerId).addValue("key", key));
            }
        }
    }

    // -------------------------------------------------------------------------------------------- the demonstration

    /**
     * The demonstration's members, then its files -- as {@code DataSeeder} calls the two, around Month end's takes.
     *
     * @return how many files the demonstration loaded by this call
     */
    public int seedDemonstration() throws IOException {
        seedMembers();
        return seedFiles();
    }

    /**
     * The seed's invented payroll members, when absent by membership number -- and, where SASBO's workplaces are served
     * (the switch at all), each placed at a workplace of their employer where they have none, so Month end counts them
     * where they work. Called before Month end takes the seed's past months, so the months taken hold them.
     *
     * @return how many members this call put on record or placed
     */
    public int seedMembers() throws IOException {
        if (environment.isProduction()) {
            return 0;
        }
        List<PayrollSeedMember> rows = readSeedFile().members();
        int written = putOnRecord(rows);
        if (workplaceSwitch.staffNumbersServed()) {
            written += place(rows);
        }
        return written;
    }

    /**
     * The demonstration's files, for each demonstrated employer with no file of the seed's yet.
     *
     * @return how many files this call loaded
     */
    public int seedFiles() throws IOException {
        if (environment.isProduction()) {
            return 0;
        }
        PayrollSeedMember.SeedFile file = readSeedFile();
        int loaded = 0;
        for (String code : DEMONSTRATED) {
            Optional<PayrollEmployer> employer = employers.findByCode(code);
            if (employer.isEmpty() || seededAlready(employer.get().getId())) {
                continue;
            }
            loaded += demonstrate(employer.get(), file);
        }
        if (loaded > 0) {
            log.info("Seeded {} demonstration payroll file(s)", loaded);
        }
        return loaded;
    }

    /** The seed's members, when absent by membership number: one query, then one batch. */
    private int putOnRecord(List<PayrollSeedMember> rows) {
        Set<String> present = new HashSet<>(seedMembers.findMembershipNumbersIn(
                rows.stream().map(PayrollSeedMember::membershipNumber).toList()));
        List<Member> missing = rows.stream().filter(row -> !present.contains(row.membershipNumber()))
                .map(PayrollSeedMember::toMember).toList();
        if (missing.isEmpty()) {
            return 0;
        }
        try {
            transactions.executeWithoutResult(status -> memberRepository.saveAll(missing));
            log.info("Seeded {} payroll demonstration member(s) from {}", missing.size(), SEED_FILE);
            return missing.size();
        } catch (DataIntegrityViolationException seededMeanwhile) {
            log.info("The payroll demonstration members were put on record by another instance at the same time");
            return 0;
        }
    }

    /**
     * Each seed member with no workplace given the one the file names, where it is on record: one read of who has
     * none, then one batch. A workplace a person chose is never touched.
     */
    private int place(List<PayrollSeedMember> rows) {
        List<String> unplaced = jdbc.queryForList("SELECT membership_number FROM members WHERE membership_number IN "
                + "(:numbers) AND workplace_id IS NULL", new MapSqlParameterSource("numbers",
                rows.stream().map(PayrollSeedMember::membershipNumber).toList()), String.class);
        if (unplaced.isEmpty()) {
            return 0;
        }
        Set<String> todo = new HashSet<>(unplaced);
        List<MapSqlParameterSource> batch = rows.stream()
                .filter(r -> todo.contains(r.membershipNumber()) && r.workplaceRaNumber() != null)
                .map(r -> new MapSqlParameterSource("number", r.membershipNumber()).addValue("ra", r.workplaceRaNumber()))
                .toList();
        if (batch.isEmpty()) {
            return 0;
        }
        int[] placed = jdbc.batchUpdate("UPDATE members SET workplace_id = (SELECT w.id FROM workplaces w "
                + "WHERE w.ra_number = :ra) WHERE membership_number = :number AND workplace_id IS NULL "
                + "AND EXISTS (SELECT 1 FROM workplaces w WHERE w.ra_number = :ra)",
                batch.toArray(new MapSqlParameterSource[0]));
        return java.util.Arrays.stream(placed).map(n -> Math.max(n, 0)).sum();
    }

    private boolean seededAlready(long employerId) {
        Integer files = jdbc.queryForObject("SELECT count(*) FROM payroll_files WHERE payroll_employer_id = :employer "
                + "AND uploaded_by = :seed", new MapSqlParameterSource("employer", employerId)
                .addValue("seed", SEED_ACTOR), Integer.class);
        return files != null && files > 0;
    }

    /** The month before last, worked; then last month, left open. */
    private int demonstrate(PayrollEmployer employer, PayrollSeedMember.SeedFile seed) {
        YearMonth last = MonthRange.current(clock).minusMonths(1);
        YearMonth before = last.minusMonths(1);
        int loaded = 0;
        if (!employer.getCode().equals("GBS")) {
            PayrollFileSummaryDto older = load(employer, before, seed, 2);
            workTheMonthBefore(employer, older);
            loaded++;
        }
        PayrollFileSummaryDto recent = load(employer, last, seed, 1);
        recordLastMonthsPayment(employer, recent);
        return loaded + 1;
    }

    private PayrollFileSummaryDto load(PayrollEmployer employer, YearMonth period, PayrollSeedMember.SeedFile seed,
                                       int monthsBack) {
        BigDecimal rate = rateFor(period);
        List<PayrollSampleWriter.Line> lines = lines(employer, period, seed, monthsBack, rate);
        if (employer.getCode().equals("GBS")) {
            List<PayrollTypedEntryRequest.Row> rows = lines.stream().map(l -> new PayrollTypedEntryRequest.Row(null,
                    null, l.initials(), l.surname(), PayrollKeys.money(l.amount()))).toList();
            // GBS's letter states one amount -- here one subscription more than the members it lists (PAY-09).
            BigDecimal stated = rate.multiply(BigDecimal.valueOf(rows.size() + 1L));
            return files.typed(new PayrollTypedEntryRequest(employer.getId(), period.toString(),
                    PayrollKeys.money(stated), rows), SEED_ACTOR).summary();
        }
        byte[] bytes = PayrollSampleWriter.write(employer.getLayoutKey(), period, lines);
        String name = employer.getName() + " payroll " + PayrollMatcher.monthName(period) + ".xlsx";
        return files.upload(employer.getId(), period.toString(), name, bytes, false, null, SEED_ACTOR).summary();
    }

    /** The rate the employers deduct for the month: the dated rate in force, else the 2026 subscription. */
    private BigDecimal rateFor(YearMonth period) {
        Map.Entry<YearMonth, BigDecimal> entry = new java.util.TreeMap<>(store.rates()).floorEntry(period);
        return entry == null ? new BigDecimal("174.00") : entry.getValue();
    }

    /**
     * The file's lines: everyone on record at the employer who should be on it -- paying by payroll, not left, joined by
     * the month's end -- as they are on record, with the seed's planted differences, and the strangers.
     */
    List<PayrollSampleWriter.Line> lines(PayrollEmployer employer, YearMonth period, PayrollSeedMember.SeedFile seed,
                                         int monthsBack, BigDecimal rate) {
        Map<String, PayrollSeedMember> roles = new HashMap<>();
        for (PayrollSeedMember member : seed.members()) {
            if (member.employer().equals(employer.getCode())) {
                roles.put(member.membershipNumber(), member);
            }
        }
        List<PayrollSampleWriter.Line> lines = new ArrayList<>();
        for (Map<String, Object> m : population(employer.getId(), period)) {
            String number = (String) m.get("membership_number");
            PayrollSeedMember planted = roles.get(number);
            String role = planted == null ? "ORDINARY" : planted.role();
            lines.addAll(planted(role, line(m, rate), monthsBack, rate, seed, employer.getCode()));
        }
        // The resigned member: no longer expected on the file, yet still deducted (PAY-13).
        for (PayrollSeedMember member : roles.values()) {
            if (member.role().equals("RESIGNED")) {
                lines.add(new PayrollSampleWriter.Line(member.employeeNumber(), member.identityNumber(), member.title(),
                        member.initials(), member.firstNames(), member.surname(), rate, null, null, null));
            }
        }
        if (monthsBack == 1) {
            // A member who pays by debit order, on this month's payroll file (PAY-29).
            debitOrderMember(employer.getId()).ifPresent(m -> lines.add(line(m, rate)));
        }
        for (PayrollSeedMember.Stranger stranger : seed.strangers()) {
            if (!stranger.employer().equals(employer.getCode())) {
                continue;
            }
            boolean unreadable = stranger.role().equals("UNREADABLE");
            // Absa's stranger is on both months' files; everything else of the kind is last month's alone.
            boolean both = employer.getCode().equals("ABSA") && stranger.role().equals("STRANGER");
            if (stranger.role().equals("OTHER_ID") || (monthsBack == 2 && !both)) {
                continue;
            }
            lines.add(new PayrollSampleWriter.Line(stranger.employeeNumber(), stranger.identityNumber(),
                    stranger.title(), stranger.initials(), stranger.firstNames(), stranger.surname(),
                    unreadable ? null : rate, unreadable ? "n/a" : null, null, null));
        }
        Collections.shuffle(lines, new Random(20_261_007L + employer.getCode().hashCode() * 31L + monthsBack));
        return lines;
    }

    /** One member's lines on the file, as their role plants them. */
    private List<PayrollSampleWriter.Line> planted(String role, PayrollSampleWriter.Line line, int monthsBack,
                                                   BigDecimal rate, PayrollSeedMember.SeedFile seed, String employer) {
        if (role.equals("MISSING") || (role.equals("MISSED_THEN_CAUGHT_UP") && monthsBack == 2)) {
            return List.of();
        }
        if (monthsBack == 2) {
            return List.of(line);
        }
        BigDecimal twice = rate.multiply(BigDecimal.valueOf(2));
        return switch (role) {
            case "RETRO" -> List.of(line, with(line, null, null, null, null, rate, YearMonth.from(
                    MonthRange.current(clock).minusMonths(2).atDay(1)), null));
            case "DUPLICATE", "DUPLICATE_OWED" -> List.of(line, line);
            case "MISSED_THEN_CAUGHT_UP", "CATCH_UP_ARREARS", "OVER_BY_MONTHS" -> List.of(with(line, null, null, null,
                    null, twice, null, null));
            case "OLD_RATE" -> List.of(with(line, null, null, null, null, new BigDecimal("166.65"), null, null));
            case "INITIALS" -> List.of(with(line, null, null, "Exempla", null, null, null, null));
            case "STAFF_NUMBER" -> List.of(with(line, line.staffNumber().substring(0, line.staffNumber().length() - 4)
                    + "9999", null, null, null, null, null, null));
            case "STAFF_BY_ID" -> List.of(with(line, "0919999", null, null, null, null, null, null));
            case "ID_DIFFERS" -> List.of(with(line, null, otherId(seed, employer), null, null, null, null, null));
            case "NAME_BY_ID" -> List.of(new PayrollSampleWriter.Line(null, line.idNumber(), line.title(),
                    line.initials(), line.firstNames(), "Maidenvale", rate, null, null, null));
            case "ADJUSTMENT_CATCH_UP" -> List.of(with(line, null, null, null, null, null, null, rate));
            case "ZERO" -> List.of(with(line, null, null, null, null, BigDecimal.ZERO.setScale(2), null, null));
            default -> List.of(line);
        };
    }

    private static PayrollSampleWriter.Line with(PayrollSampleWriter.Line line, String staff, String id,
                                                 String firstNames, String surname, BigDecimal amount,
                                                 YearMonth forPeriod, BigDecimal adjustment) {
        return new PayrollSampleWriter.Line(staff == null ? line.staffNumber() : staff,
                id == null ? line.idNumber() : id, line.title(),
                firstNames == null ? line.initials() : PayrollNamesInitials.of(firstNames),
                firstNames == null ? line.firstNames() : firstNames, surname == null ? line.surname() : surname,
                amount == null ? line.amount() : amount, null, forPeriod == null ? line.forPeriod() : forPeriod,
                adjustment == null ? line.adjustment() : adjustment);
    }

    private static String otherId(PayrollSeedMember.SeedFile seed, String employer) {
        return seed.strangers().stream().filter(s -> s.employer().equals(employer) && s.role().equals("OTHER_ID"))
                .map(PayrollSeedMember.Stranger::identityNumber).findFirst().orElseThrow();
    }

    /** A member's line as their record has them. */
    private static PayrollSampleWriter.Line line(Map<String, Object> m, BigDecimal rate) {
        return new PayrollSampleWriter.Line((String) m.get("employee_number"), (String) m.get("identity_number"),
                (String) m.get("title"), (String) m.get("initials"), (String) m.get("first_names"),
                (String) m.get("surname"), rate, null, null, null);
    }

    /** Whoever the employer's file should list this month, in membership-number order. */
    private List<Map<String, Object>> population(long employerId, YearMonth period) {
        return jdbc.queryForList("SELECT m.membership_number, m.employee_number, m.identity_number, m.title, "
                        + "m.initials, m.first_names, m.surname FROM members m "
                        + "WHERE (m.workplace_id IN (SELECT w.id FROM workplaces w "
                        + "JOIN table_codes t ON t.id = w.table_code_id "
                        + "JOIN payroll_employer_employers pe ON pe.employer_id = t.employer_id "
                        + "WHERE pe.payroll_employer_id = :employer) "
                        + "OR upper(trim(m.bank_name)) IN (SELECT n.name_key FROM payroll_employer_names n "
                        + "WHERE n.payroll_employer_id = :employer)) "
                        + "AND m.payment_type IN ('Payroll Deduction', 'Payroll', 'PAYROLL') "
                        + "AND (m.status IS NULL OR m.status NOT IN ('Resigned', 'Retired', 'Terminated')) "
                        + "AND m.surname IS NOT NULL AND m.surname <> '' "
                        + "AND (m.join_date IS NULL OR m.join_date <= :end) ORDER BY m.membership_number",
                new MapSqlParameterSource("employer", employerId).addValue("end",
                        java.sql.Date.valueOf(period.atEndOfMonth())));
    }

    /**
     * A member who pays by debit order and whose record names this employer -- found on its payroll file (PAY-29). One
     * of the debit-order population already on record, so no member is added that a debit order run would read.
     */
    private Optional<Map<String, Object>> debitOrderMember(long employerId) {
        List<Map<String, Object>> found = jdbc.queryForList("SELECT m.membership_number, m.employee_number, "
                        + "m.identity_number, m.title, m.initials, m.first_names, m.surname FROM members m "
                        + "WHERE upper(trim(m.bank_name)) IN (SELECT n.name_key FROM payroll_employer_names n "
                        + "WHERE n.payroll_employer_id = :employer) AND m.payment_type = 'Debit Order' "
                        + "AND m.status = 'Active' AND m.employee_number IS NOT NULL AND m.surname IS NOT NULL "
                        + "AND m.surname <> '' ORDER BY m.membership_number",
                new MapSqlParameterSource("employer", employerId));
        return found.stream().findFirst();
    }

    // ------------------------------------------------------------------------------------- working the month before

    /** As an administrator would: one discrepancy resolved, a query raised, a note, the money recorded. */
    private void workTheMonthBefore(PayrollEmployer employer, PayrollFileSummaryDto file) {
        long fileId = file.file().id();
        if (employer.getCode().equals("ABSA")) {
            memberIdOf(employer, "MISSED_THEN_CAUGHT_UP").flatMap(id -> discrepancy(fileId,
                    PayrollDiscrepancyType.MISSING_FROM_FILE, "M" + id)).ifPresent(d -> discrepancies.resolve(d.getId(),
                    PayrollOutcome.DEDUCTION_RESTARTED.name(), "Absa's payroll office confirmed the deduction restarts "
                            + "next month, and the missed month is caught up then.", d.getVersion(), SEED_ACTOR));
            queries.raise(fileId, SEED_ACTOR);
            discrepancyRepository.findAllByFileId(fileId).stream()
                    .filter(d -> d.getType() == PayrollDiscrepancyType.NOT_A_MEMBER).findFirst()
                    .ifPresent(d -> discrepancies.addNote(d.getId(), "Queried with Absa's payroll office by e-mail.",
                            SEED_ACTOR));
        }
        if (employer.getCode().equals("STANDARD_BANK")) {
            discrepancyRepository.findAllByFileId(fileId).stream()
                    .filter(d -> d.getType() == PayrollDiscrepancyType.MEMBERSHIP_ENDED).findFirst()
                    .ifPresent(d -> discrepancies.addNote(d.getId(), "Asked Standard Bank to stop the deduction.",
                            SEED_ACTOR));
        }
        pay(employer, file, BigDecimal.ZERO);
    }

    /**
     * Last month: each employer pays one subscription short (PAY-28) -- Absa less its 3% fee as well, which the check
     * expects (PAY-27); GBS the amount its letter states, which is more than its list comes to.
     */
    private void recordLastMonthsPayment(PayrollEmployer employer, PayrollFileSummaryDto file) {
        if (employer.getCode().equals("GBS")) {
            YearMonth period = YearMonth.parse(file.file().period());
            payments.record(new PayrollPaymentRequest(employer.getId(), period.toString(),
                    file.totals().statedTotal(), period.atEndOfMonth().toString(), "EFT GBS " + period), SEED_ACTOR);
            return;
        }
        pay(employer, file, rateFor(YearMonth.parse(file.file().period())));
    }

    private void pay(PayrollEmployer employer, PayrollFileSummaryDto file, BigDecimal shortBy) {
        BigDecimal total = new BigDecimal(file.file().fileTotal());
        BigDecimal amount = PayrollMoney.expectedPayment(total, employer.getAdminFeePercent()).subtract(shortBy);
        YearMonth period = YearMonth.parse(file.file().period());
        payments.record(new PayrollPaymentRequest(employer.getId(), period.toString(), PayrollKeys.money(amount),
                period.atEndOfMonth().toString(), "EFT " + employer.getCode() + " " + period), SEED_ACTOR);
    }

    private Optional<Integer> memberIdOf(PayrollEmployer employer, String role) {
        try {
            return readSeedFile().members().stream()
                    .filter(m -> m.employer().equals(employer.getCode()) && m.role().equals(role))
                    .findFirst()
                    .flatMap(m -> jdbc.queryForList("SELECT id FROM members WHERE membership_number = :number",
                            new MapSqlParameterSource("number", m.membershipNumber()), Integer.class).stream()
                            .findFirst());
        } catch (IOException unreadable) {
            return Optional.empty();
        }
    }

    private Optional<PayrollDiscrepancy> discrepancy(long fileId, PayrollDiscrepancyType type, String subject) {
        return discrepancyRepository.findByFileIdAndTypeAndSubjectKey(fileId, type, subject);
    }

    /** The seed file, read strictly: a key the record does not declare fails the read (F65). */
    public static PayrollSeedMember.SeedFile readSeedFile() throws IOException {
        JsonMapper strict = JsonMapper.builder()
                .findAndAddModules()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        try (InputStream stream = new ClassPathResource(SEED_FILE).getInputStream()) {
            return strict.readValue(stream, PayrollSeedMember.SeedFile.class);
        }
    }

    /** Initials from first names -- the same rule the comparison reads them by. */
    private static final class PayrollNamesInitials {
        static String of(String firstNames) {
            return za.co.titandynamix.sasbo_online_backend.payroll.PayrollNames.initialsOf(firstNames);
        }
    }
}
