package za.co.titandynamix.sasbo_online_backend.acb;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

/**
 * The banks on record and how many ACTIVE subscription rows each has, loaded once per run.
 *
 * <h2>Why this exists</h2>
 * The capture path resolves a member's bank with two queries per member. A run over the real
 * membership would issue a hundred thousand of them. So the run loads the banks and their ACTIVE
 * rows into maps before it reads a single member, and every lookup after that is a map read.
 *
 * <h2>The member's bank is a NAME, and names drift</h2>
 * {@code Member.bankName} is free text. It is matched here trimmed and case-insensitively -- the
 * same rule the planning pack measures the seed with -- and a name that matches no bank is not
 * guessed at: the member is excluded and the report prints the name as it was captured, which is
 * what an administrator needs to correct it. The durable fix is a foreign key on the member, which
 * belongs to the capture arc.
 */
public final class BankIndex {

    /** Sentinel for a normalised name two banks share -- matching either would be a guess. */
    private static final BankRow SHARED_NAME = new BankRow(null, null, null);

    private final Map<String, BankRow> byName;
    private final Map<Integer, Integer> activeRows;
    private final Map<Integer, BankRow> byId;

    private BankIndex(Map<String, BankRow> byName, Map<Integer, Integer> activeRows,
                      Map<Integer, BankRow> byId) {
        this.byName = byName;
        this.activeRows = activeRows;
        this.byId = byId;
    }

    /**
     * @param banks                      every bank on record
     * @param activeSubscriptionBankIds  the bank id of every ACTIVE subscription row, one entry per
     *                                   row -- so a bank with two ACTIVE rows appears twice
     */
    public static BankIndex of(Collection<BankRow> banks, Collection<Integer> activeSubscriptionBankIds) {
        Map<String, BankRow> byName = new HashMap<>();
        Map<Integer, BankRow> byId = new HashMap<>();
        for (BankRow bank : banks) {
            byId.put(bank.id(), bank);
            byName.merge(normalise(bank.name()), bank, (first, second) -> SHARED_NAME);
        }
        Map<Integer, Integer> activeRows = new HashMap<>();
        for (Integer bankId : activeSubscriptionBankIds) {
            if (bankId != null) {
                activeRows.merge(bankId, 1, Integer::sum);
            }
        }
        return new BankIndex(Map.copyOf(byName), Map.copyOf(activeRows), Map.copyOf(byId));
    }

    /** The one comparison rule for bank names: trimmed, and case does not matter. */
    public static String normalise(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    /** The bank on record a member's captured bank name names, if exactly one does. */
    public Optional<BankRow> find(String capturedName) {
        BankRow found = byName.get(normalise(capturedName));
        return found == null || found == SHARED_NAME ? Optional.empty() : Optional.of(found);
    }

    /** Whether the captured name matches more than one bank -- said so in the report. */
    public boolean sharedByTwoBanks(String capturedName) {
        return byName.get(normalise(capturedName)) == SHARED_NAME;
    }

    /** How many ACTIVE subscription rows the bank has: one is collectable, none is not, two refuse. */
    public int activeRowsFor(BankRow bank) {
        return activeRows.getOrDefault(bank.id(), 0);
    }

    /**
     * The banks with more than one ACTIVE subscription row, by name and sorted. Two ACTIVE rows is
     * a contradiction no rule can resolve safely, so any bank here refuses the whole run.
     */
    public List<String> ambiguousBanks() {
        TreeSet<String> names = new TreeSet<>();
        activeRows.forEach((bankId, rows) -> {
            if (rows > 1) {
                BankRow bank = byId.get(bankId);
                names.add(bank == null ? "bank " + bankId : Objects.toString(bank.name()));
            }
        });
        return List.copyOf(names);
    }
}
