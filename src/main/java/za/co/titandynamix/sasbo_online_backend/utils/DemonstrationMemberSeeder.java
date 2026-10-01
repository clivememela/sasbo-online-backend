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
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.repository.SeedMemberRepository;
import za.co.titandynamix.sasbo_online_backend.repository.WorkplaceEntityRepository;
import za.co.titandynamix.sasbo_online_backend.enums.MemberStatusReason;
import za.co.titandynamix.sasbo_online_backend.repository.SasboReferenceRepository;
import za.co.titandynamix.sasbo_online_backend.services.MemberService;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The invented members, and where the seed's other members stand (month end 01.4, MD30, MD33, MD35, MD53).
 *
 * <h2>Why they exist</h2>
 * So that everything beneath the first line of the first screen reads as SASBO's own does: members at SASBO's
 * workplaces, in whole areas, each workplace at its group's penetration. Generated -- never copied -- by
 * {@code DemonstrationMemberGenerator} in the test sources, from the workplaces' staff complement and the nine
 * group-level penetrations, and committed as {@value #SEED_FILE}. No count of members at a workplace or an employer
 * is taken from SASBO's files.
 *
 * <h2>Only where the switch says {@code all}</h2>
 * They are made from the staff numbers and they stand at workplaces: where no workplace is on record none of them
 * is, and at {@code names} nothing made from a staff number is (MD53, MD62). Never where the production marker is
 * set: synthetic people are exactly what production must never hold.
 *
 * <h2>When absent, by membership number -- and the seed's own members given a workplace</h2>
 * {@link #seed()} puts on record whichever of the file's members are missing, by ONE query for the file's numbers,
 * as the debit-order population is -- a tester's own members are never touched. {@link #place()} gives the seed's
 * other 310 members a workplace in the same areas, from the placements the generator wrote into the file
 * (MR-ROB-32): it fills ONLY what is empty, by one statement, touches no other column and writes no history -- so it
 * is right on a fresh database, on a second start, after a person has chosen a member's workplace, and on the day the
 * switch is set on a database that has held its members for a month (BR61).
 */
@Slf4j
@Component
public class DemonstrationMemberSeeder {

    /** The generated population, numbered downwards from M-099850 (BR38). */
    public static final String SEED_FILE = "data/demonstration_members.json";

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

    /** 03.7: who wrote the demonstration's past. */
    public static final String SEED_ACTOR = "seed";

    public DemonstrationMemberSeeder(SeedMemberRepository seedMembers, MemberRepository memberRepository,
                                     WorkplaceEntityRepository workplaces, NamedParameterJdbcTemplate jdbc,
                                     PlatformTransactionManager transactionManager, SasboEnvironment environment,
                                     WorkplaceSwitch workplaceSwitch, MemberService members,
                                     SasboReferenceRepository codes, Clock clock) {
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
     * 03.7 -- the demonstration's past: the file's leavers, suspended and reinstated, each in the month
     * {@code monthsBack} before the clock's, WRITTEN THROUGH THE REAL STATUS CHANGE with the actor {@value #SEED_ACTOR}
     * and the change's own instant -- so every figure, the history and ACB read them as any change. A member's past is
     * written once, at the first start that can: one who already has a change by the seed is left alone. None of these
     * is ACB's SUBS_IN_ARREARS, so ACB's count of terminations does not move.
     *
     * @return how many changes this call wrote
     */
    public int past() throws IOException {
        if (!allowed()) {
            return 0;
        }
        List<DemonstrationMember.PastChange> past = readSeedFile().past();
        if (past == null || past.isEmpty()) {
            return 0;
        }
        Map<String, Integer> ids = new HashMap<>();
        jdbc.query("SELECT id, membership_number FROM members WHERE membership_number IN (:numbers)",
                new MapSqlParameterSource("numbers", past.stream().map(DemonstrationMember.PastChange::membershipNumber)
                        .distinct().toList()),
                rs -> {
                    ids.put(rs.getString("membership_number"), rs.getInt("id"));
                });
        if (ids.isEmpty()) {
            return 0;
        }
        Set<Integer> written = new HashSet<>(jdbc.queryForList("SELECT DISTINCT member_id FROM member_status_changes "
                + "WHERE changed_by = :seed AND member_id IN (:ids)", new MapSqlParameterSource("seed", SEED_ACTOR)
                .addValue("ids", ids.values()), Integer.class));
        if (written.containsAll(ids.values())) {
            return 0;
        }
        YearMonth now = MonthRange.current(clock);
        Map<Integer, Long> deletionIds = new HashMap<>();
        Map<Integer, Long> suspensionIds = new HashMap<>();
        int changed = 0;
        for (DemonstrationMember.PastChange change : past) {
            Integer id = ids.get(change.membershipNumber());
            if (id == null || written.contains(id)) {
                continue;
            }
            Long deletion = change.deletionCode() == null ? null : deletionIds.computeIfAbsent(change.deletionCode(),
                    number -> codes.deletionCode(number).map(SasboReferenceRepository.Code::id).orElse(null));
            Long suspension = change.suspensionCode() == null ? null : suspensionIds.computeIfAbsent(
                    change.suspensionCode(),
                    number -> codes.suspensionCode(number).map(SasboReferenceRepository.Code::id).orElse(null));
            if ((change.deletionCode() != null && deletion == null) || (change.suspensionCode() != null && suspension == null)) {
                continue;
            }
            Instant at = now.minusMonths(change.monthsBack()).atDay(change.day()).atTime(10, 0)
                    .atZone(MonthRange.ZONE).toInstant();
            boolean wrote = members.changeStatus(id, new MemberService.StatusChange(change.toStatus(), null,
                    MemberStatusReason.valueOf(change.reason()), deletion, suspension, null, SEED_ACTOR, at))
                    .map(outcome -> outcome.written() != null).orElse(false);
            changed += wrote ? 1 : 0;
        }
        log.info("Wrote {} change(s) of the invented members' past from {}", changed, SEED_FILE);
        return changed;
    }

    /** Whether this deployment may hold them at all: not production, and the switch at {@code all}. */
    private boolean allowed() {
        return !environment.isProduction() && workplaceSwitch.staffNumbersServed();
    }

    /** @return how many invented members were put on record by this call */
    public int seed() throws IOException {
        if (!allowed()) {
            return 0;
        }
        List<DemonstrationMember> rows = readSeedFile().members();
        Set<String> present = new HashSet<>(seedMembers.findMembershipNumbersIn(
                rows.stream().map(DemonstrationMember::membershipNumber).toList()));
        List<DemonstrationMember> missing = rows.stream().filter(row -> !present.contains(row.membershipNumber()))
                .toList();
        if (missing.isEmpty()) {
            return 0;
        }
        Map<Integer, Long> onRecord = workplaceIds(missing.stream().map(DemonstrationMember::workplaceRaNumber)
                .distinct().toList());
        long started = System.nanoTime();
        List<Member> members = new ArrayList<>();
        int[] withoutWorkplace = {0};
        try {
            // One transaction: the workplaces are referenced in the same persistence context the members are saved in.
            transactions.executeWithoutResult(status -> {
                for (DemonstrationMember row : missing) {
                    Long workplace = onRecord.get(row.workplaceRaNumber());
                    if (workplace == null) {
                        withoutWorkplace[0]++;
                        continue;
                    }
                    Member member = row.toMember();
                    member.setWorkplace(workplaces.getReferenceById(workplace));
                    members.add(member);
                }
                memberRepository.saveAll(members);
            });
        } catch (DataIntegrityViolationException seededMeanwhile) {
            log.info("The invented members were put on record by another instance starting at the same time");
            return 0;
        }
        long elapsed = (System.nanoTime() - started) / 1_000_000;
        log.info("Seeded {} invented member(s) from {} in {} ms", members.size(), SEED_FILE, elapsed);
        if (withoutWorkplace[0] > 0) {
            log.warn("{} invented member(s) of {} stand at a workplace that is not on record, and were left out",
                    withoutWorkplace[0], SEED_FILE);
        }
        return members.size();
    }

    /**
     * Gives the seed's other members their workplace, where they have none.
     *
     * @return how many members were given one by this call
     */
    public int place() throws IOException {
        if (!allowed()) {
            return 0;
        }
        List<DemonstrationMember.Placement> placements = readSeedFile().placements();
        List<String> waiting = jdbc.queryForList("SELECT membership_number FROM members WHERE workplace_id IS NULL"
                        + " AND membership_number IN (:numbers)",
                new MapSqlParameterSource("numbers", placements.stream()
                        .map(DemonstrationMember.Placement::membershipNumber).toList()), String.class);
        if (waiting.isEmpty()) {
            return 0;
        }
        Set<String> unplaced = new HashSet<>(waiting);
        MapSqlParameterSource[] batch = placements.stream()
                .filter(placement -> unplaced.contains(placement.membershipNumber()))
                .map(placement -> new MapSqlParameterSource()
                        .addValue("number", placement.membershipNumber())
                        .addValue("ra", placement.workplaceRaNumber()))
                .toArray(MapSqlParameterSource[]::new);
        // A workplace not on record yet leaves the member as they are, and uncounted: the next start places them.
        int[] updated = jdbc.batchUpdate("UPDATE members SET workplace_id ="
                + " (SELECT w.id FROM workplaces w WHERE w.ra_number = :ra)"
                + " WHERE membership_number = :number AND workplace_id IS NULL"
                + " AND EXISTS (SELECT 1 FROM workplaces w WHERE w.ra_number = :ra)", batch);
        int placed = 0;
        for (int rows : updated) {
            placed += Math.max(rows, 0);
        }
        log.info("Gave {} of the seed's member(s) a workplace from {}", placed, SEED_FILE);
        return placed;
    }

    /** The workplaces' ids on record, by R/A number -- one query. */
    private Map<Integer, Long> workplaceIds(List<Integer> raNumbers) {
        Map<Integer, Long> ids = new HashMap<>();
        jdbc.query("SELECT id, ra_number FROM workplaces WHERE ra_number IN (:ra)",
                new MapSqlParameterSource("ra", raNumbers),
                rs -> {
                    ids.put(rs.getInt("ra_number"), rs.getLong("id"));
                });
        return ids;
    }

    /** The file, read STRICTLY: a key the record does not declare fails the read, instead of losing a field (F65). */
    public static DemonstrationMember.SeedFile readSeedFile() throws IOException {
        JsonMapper strict = JsonMapper.builder()
                .findAndAddModules()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        try (InputStream stream = new ClassPathResource(SEED_FILE).getInputStream()) {
            return strict.readValue(stream, DemonstrationMember.SeedFile.class);
        }
    }
}
