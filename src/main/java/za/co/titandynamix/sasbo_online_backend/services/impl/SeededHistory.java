package za.co.titandynamix.sasbo_online_backend.services.impl;

import za.co.titandynamix.sasbo_online_backend.utils.DebitOrderMemberSeeder;
import za.co.titandynamix.sasbo_online_backend.utils.SeedDebitOrderMember;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * What the seeded debit-order history and the Debit Order Deductions demonstration share: the seed's actor, the
 * members a run debits, and the day a seeded return is noted. One copy, so the two seeders name the same members and
 * date their returns by the same rule.
 */
final class SeededHistory {

    /** The actor on every audit row and status change the seeds write. */
    static final String ACTOR = "seed";

    private SeededHistory() {
    }

    /**
     * The generated population's members a run debits, by membership number and sorted: those with an address and
     * those without.
     */
    record Population(List<String> withAddress, List<String> withoutAddress) {

        static Population of(List<SeedDebitOrderMember> members) {
            List<String> withAddress = new ArrayList<>();
            List<String> withoutAddress = new ArrayList<>();
            for (SeedDebitOrderMember m : members) {
                if (!"QUALIFIES".equals(m.expectedOutcome())) {
                    continue;
                }
                (m.personalEmail() == null ? withoutAddress : withAddress).add(m.membershipNumber());
            }
            withAddress.sort(null);
            withoutAddress.sort(null);
            return new Population(List.copyOf(withAddress), List.copyOf(withoutAddress));
        }

        /** The seed's members, read from the generated file. */
        static Population read() {
            return of(seedMembers());
        }
    }

    /** The generated debit-order population (D33). */
    static List<SeedDebitOrderMember> seedMembers() {
        try {
            return DebitOrderMemberSeeder.readSeedFile().members();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * When the seed says the bank's report showed a return: two days after the debit, as the report arrives -- but
     * never after its own month ends (F62), so a seeded return is never dated in the clock's own month.
     */
    static LocalDate notedOn(LocalDate actionDate, YearMonth month) {
        LocalDate twoDaysLater = actionDate.plusDays(2);
        return twoDaysLater.isAfter(month.atEndOfMonth()) ? month.atEndOfMonth() : twoDaysLater;
    }
}
