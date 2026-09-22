package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.springframework.stereotype.Component;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbRun;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.AcbRunLineRepository;
import za.co.titandynamix.sasbo_online_backend.utils.SeedMemberNumbers;

import java.util.Map;

/**
 * 🔒 No bureau file is made from a seeded member where the production marker is set (SD3-A).
 *
 * <p>The seed's members are synthetic people at real banks, and a bureau file is an instruction to
 * take money: a synthetic account number that happens to be somebody's real one is a debit against a
 * stranger. The marker keeps the seed OUT of production; this is the second lock, for the system that
 * started as a demo and became production -- which is how a small one goes live. So a run that
 * carries any seeded member cannot be approved, nor its file rendered, where the marker is set.
 *
 * <p>A seeded member is one a seed file LISTS -- an exact match, never a numeric block (SD4-E). In a
 * demo this is never consulted: a demonstration's runs are made of seeded members by design.
 */
@Component
public class SeededMembersGuard {

    private final SasboEnvironment environment;
    private final SeedMemberNumbers seedMemberNumbers;
    private final AcbRunLineRepository lines;

    public SeededMembersGuard(SasboEnvironment environment, SeedMemberNumbers seedMemberNumbers,
                              AcbRunLineRepository lines) {
        this.environment = environment;
        this.seedMemberNumbers = seedMemberNumbers;
        this.lines = lines;
    }

    /** @throws AcbRefusal {@code FILE_HAS_SEED_MEMBERS} where the marker is set and the run holds one */
    void refuseSeededMembers(AcbRun run) {
        if (!environment.isProduction()) {
            return;
        }
        long seeded = lines.countMembershipNumbersIn(run.getId(), seedMemberNumbers.all());
        if (seeded > 0) {
            Map<String, String> facts = AcbRuns.facts(run);
            facts.put("count", String.valueOf(seeded));
            throw new AcbRefusal(AcbRefusalCode.FILE_HAS_SEED_MEMBERS,
                    seeded + " line(s) in this run debit members from the demonstration seed data, and this is "
                            + "the production system. No bureau file is made from synthetic members: remove them "
                            + "from the register, then prepare the month again.",
                    facts);
        }
    }
}
