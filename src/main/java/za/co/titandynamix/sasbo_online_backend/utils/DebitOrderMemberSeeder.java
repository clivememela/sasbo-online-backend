package za.co.titandynamix.sasbo_online_backend.utils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.repository.SeedMemberRepository;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The seed's debit-order population: puts the members of {@value #SEED_FILE} on record WHEN ABSENT,
 * row by row, keyed by membership number (D33 -- the owner, 2026-09-21: "they must just be there").
 *
 * <h2>When absent -- not behind a table-empty guard</h2>
 * {@code members.json}'s 160 are seeded only into an empty table. That guard would never let these
 * reach an environment started long ago -- {@code dev.sasbo.online} is exactly that, and it is not
 * production (the owner, 2026-09-21) -- so these are compared by number: whichever are missing are
 * saved, whatever else the table holds. A tester's own members are never touched.
 *
 * <h2>One query, then nothing</h2>
 * Absence is ONE query for the whole file's numbers, never an {@code exists} per member: once
 * seeded, a start asks it and stops (SD4-F).
 *
 * <h2>Never where the production marker is set</h2>
 * Synthetic people on debit order are exactly what production must never hold (SD3-A). And two
 * instances starting together on one database -- a deploy overlap -- race here: membership numbers
 * carry no unique index, but identity numbers do (V26), every generated one is distinct, and so the
 * loser's insert is refused -- which means somebody else seeded it, said at INFO (SD4-H).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DebitOrderMemberSeeder {

    /** The generated population: 150 synthetic members, M-099851 to M-100000. */
    public static final String SEED_FILE = "data/debit_order_members.json";

    private final SeedMemberRepository seedMembers;
    private final MemberRepository memberRepository;
    private final SasboEnvironment environment;

    /** @return how many members were put on record by this call */
    public int seed() throws IOException {
        if (environment.isProduction()) {
            return 0;
        }
        List<SeedDebitOrderMember> rows = readSeedFile().members();
        Set<String> present = new HashSet<>(seedMembers.findMembershipNumbersIn(
                rows.stream().map(SeedDebitOrderMember::membershipNumber).toList()));
        List<Member> missing = rows.stream()
                .filter(row -> !present.contains(row.membershipNumber()))
                .map(SeedDebitOrderMember::toMember)
                .toList();
        if (missing.isEmpty()) {
            return 0;
        }
        try {
            memberRepository.saveAll(missing);
        } catch (DataIntegrityViolationException seededMeanwhile) {
            log.info("The debit-order seed members were put on record by another instance starting at the same time");
            return 0;
        }
        log.info("Seeded {} debit-order member(s) from {} that were missing", missing.size(), SEED_FILE);
        return missing.size();
    }

    /**
     * The file, read STRICTLY: a key {@link SeedDebitOrderMember} does not declare fails the read,
     * because a key the reader drops is a field no seeded member stores (F65).
     */
    public static SeedDebitOrderMember.SeedFile readSeedFile() throws IOException {
        JsonMapper strict = JsonMapper.builder()
                .findAndAddModules()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        try (InputStream stream = new ClassPathResource(SEED_FILE).getInputStream()) {
            return strict.readValue(stream, SeedDebitOrderMember.SeedFile.class);
        }
    }
}
