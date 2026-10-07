package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.acb.BankIndex;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbBanksDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbDebitDayMoveDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.DebitDayMoveRequest;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunlessEvent;
import za.co.titandynamix.sasbo_online_backend.enums.PaymentType;
import za.co.titandynamix.sasbo_online_backend.enums.SubscriptionsStatus;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusalCode;
import za.co.titandynamix.sasbo_online_backend.services.AcbDebitDayService;
import za.co.titandynamix.sasbo_online_backend.utils.DebitDay;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Moving a bank's debit-order members to a new debit day in one step (DOD-19).
 *
 * <h2>Which members</h2>
 * Every member whose payment is a debit order and whose bank, as captured, is the bank's name -- trimmed and in any
 * case, the run's own rule ({@link BankIndex#normalise}) -- whatever their status, so a suspended member who comes
 * back is on the new day too. Those on the new day already are left as they are.
 *
 * <h2>One transaction, JDBC batches, safe to ask twice</h2>
 * The members are read and locked ({@code FOR UPDATE}), checked against the number the preview showed -- a member
 * captured or moved in between refuses the move ({@code MOVE_COUNT_CHANGED}) rather than moving someone nobody
 * previewed -- then moved by batch, each with a history row of the member's own ({@code debitDay}, the old day and the
 * new), the bank's own day with them, and one audit row. Asked again, nobody is left to move: it answers so and writes
 * nothing. A run already prepared keeps the days it was prepared with; the next preparation uses the new day.
 */
@Service
public class AcbDebitDayServiceImpl implements AcbDebitDayService {

    private static final int BATCH_SIZE = 500;

    /** The runs a move does not reach: prepared, or being prepared, and not yet sent. */
    private static final List<String> LIVE_UNSENT = List.of(AcbRunStatus.PREPARING.name(), AcbRunStatus.PREPARED.name(),
            AcbRunStatus.APPROVED.name());

    /** A debit-order member, as the member screen stores it or by the enum's own name (the run's rule 1). */
    private static final String DEBIT_ORDER = "LOWER(TRIM(m.payment_type)) IN ('"
            + PaymentType.DEBIT_ORDER.getDisplayName().toLowerCase(Locale.ROOT) + "', '"
            + PaymentType.DEBIT_ORDER.name().toLowerCase(Locale.ROOT) + "')";

    private final JdbcTemplate jdbc;
    private final AcbAuditTrail audit;
    private final Clock clock;
    private final TransactionTemplate tx;

    public AcbDebitDayServiceImpl(JdbcTemplate jdbc, AcbAuditTrail audit, Clock clock,
                                  PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactions);
    }

    private record Bank(int id, String name, Integer defaultDay) {
    }

    private record Debtor(int memberId, Integer debitDay) {
    }

    @Override
    public AcbBanksDto banks() {
        Map<Integer, String> names = new LinkedHashMap<>();
        Map<Integer, Integer> defaults = new TreeMap<>();
        Map<Integer, Map<String, Integer>> byDay = new TreeMap<>();
        jdbc.query("SELECT b.id, b.name, s.acb_debit_day, m.debit_day, COUNT(m.id) AS members FROM banks b "
                        + "LEFT JOIN subscriptions s ON s.bank_id = b.id AND s.status = ? "
                        + "LEFT JOIN members m ON LOWER(TRIM(m.bank_name)) = LOWER(TRIM(b.name)) AND " + DEBIT_ORDER
                        + " GROUP BY b.id, b.name, s.acb_debit_day, m.debit_day ORDER BY b.name, b.id",
                rs -> {
                    int id = rs.getInt(1);
                    names.putIfAbsent(id, rs.getString(2));
                    int bankDay = rs.getInt(3);
                    if (!rs.wasNull()) {
                        defaults.put(id, bankDay);
                    }
                    int members = rs.getInt(5);
                    if (members > 0) {
                        int day = rs.getInt(4);
                        String key = rs.wasNull() ? "none" : String.valueOf(day);
                        byDay.computeIfAbsent(id, k -> new TreeMap<>()).merge(key, members, Integer::sum);
                    }
                }, SubscriptionsStatus.ACTIVE.name());
        List<AcbBanksDto.Bank> banks = new ArrayList<>();
        names.forEach((id, name) -> {
            Map<String, Integer> days = byDay.getOrDefault(id, Map.of());
            banks.add(new AcbBanksDto.Bank(id, name, defaults.get(id),
                    days.values().stream().mapToInt(Integer::intValue).sum(), days));
        });
        return new AcbBanksDto(banks);
    }

    @Override
    public AcbDebitDayMoveDto preview(int bankId, Integer newDay) {
        int day = requireDay(newDay);
        Bank bank = requireBank(bankId);
        List<Debtor> debtors = debtors(bank, false);
        return answer(bank, day, debtors, null);
    }

    @Override
    public AcbDebitDayMoveDto move(int bankId, DebitDayMoveRequest request, String actor) {
        return move(bankId, request, actor, AcbTime.instant(clock));
    }

    /** The move at {@code at} -- the seed's past, or now. */
    AcbDebitDayMoveDto move(int bankId, DebitDayMoveRequest request, String actor, Instant at) {
        int day = requireDay(request.newDay());
        return tx.execute(status -> {
            Bank bank = requireBank(bankId);
            List<Debtor> debtors = debtors(bank, true);
            List<Debtor> toMove = debtors.stream().filter(d -> !Objects.equals(d.debitDay(), day)).toList();
            if (toMove.isEmpty()) {
                // Asked again: nobody is left to move, and nothing is written.
                return answer(bank, day, debtors, 0);
            }
            if (toMove.size() != request.expectedCount()) {
                Map<String, String> facts = new LinkedHashMap<>();
                facts.put("bankId", String.valueOf(bankId));
                facts.put("expected", String.valueOf(request.expectedCount()));
                facts.put("actual", String.valueOf(toMove.size()));
                throw new AcbRefusal(AcbRefusalCode.MOVE_COUNT_CHANGED, bank.name() + "'s members changed since the "
                        + "preview: " + toMove.size() + " would move now, not " + request.expectedCount()
                        + ". Nobody has been moved. Look at the preview again.", facts);
            }
            Timestamp changedAt = Timestamp.from(at);
            LocalDateTime updatedAt = LocalDateTime.ofInstant(at, AcbTime.ZONE);
            jdbc.batchUpdate("UPDATE members SET debit_day = ?, updated_at = ? WHERE id = ?", toMove, BATCH_SIZE,
                    (ps, debtor) -> {
                        ps.setInt(1, day);
                        ps.setObject(2, updatedAt);
                        ps.setInt(3, debtor.memberId());
                    });
            // Each member's history says the day moved, as the member's own saves write theirs (month end 03.5).
            jdbc.batchUpdate("INSERT INTO member_changes (member_id, save_id, field, old_value, new_value, changed_by, "
                    + "changed_at) VALUES (?, ?, 'debitDay', ?, ?, ?, ?)", toMove, BATCH_SIZE, (ps, debtor) -> {
                        ps.setInt(1, debtor.memberId());
                        ps.setString(2, UUID.randomUUID().toString());
                        ps.setString(3, debtor.debitDay() == null ? null : String.valueOf(debtor.debitDay()));
                        ps.setString(4, String.valueOf(day));
                        ps.setString(5, actor);
                        ps.setTimestamp(6, changedAt);
                    });
            // The bank's own day, so a member captured at this bank from now on starts on it.
            jdbc.update("UPDATE subscriptions SET acb_debit_day = ? WHERE bank_id = ? AND status = ?", day, bankId,
                    SubscriptionsStatus.ACTIVE.name());
            String from = toMove.stream().collect(Collectors.groupingBy(
                            d -> d.debitDay() == null ? "none" : String.valueOf(d.debitDay()), TreeMap::new,
                            Collectors.counting()))
                    .entrySet().stream().map(e -> e.getKey() + ": " + e.getValue()).collect(Collectors.joining(", "));
            audit.recordRunless(AcbRunlessEvent.DEBIT_DAY_MOVED, bank.name() + ": " + toMove.size()
                    + " debit-order member(s) moved to the " + day + "th (from " + from + "), and the bank's own day",
                    actor);
            return answer(bank, day, debtors, toMove.size());
        });
    }

    // ------------------------------------------------------------------------------------------

    /**
     * The answer, from the members as they were read: on a preview and a move that moved nobody, how many would move
     * and how many are on the day; after a move, how many it moved -- and every one of them is on the new day now.
     */
    private AcbDebitDayMoveDto answer(Bank bank, int day, List<Debtor> debtors, Integer moved) {
        Map<String, Integer> byCurrentDay = new TreeMap<>();
        int already = 0;
        for (Debtor debtor : debtors) {
            byCurrentDay.merge(debtor.debitDay() == null ? "none" : String.valueOf(debtor.debitDay()), 1, Integer::sum);
            already += Objects.equals(debtor.debitDay(), day) ? 1 : 0;
        }
        if (moved != null && moved > 0) {
            byCurrentDay = new TreeMap<>(Map.of(String.valueOf(day), debtors.size()));
        }
        List<AcbDebitDayMoveDto.LiveRun> live = jdbc.query("SELECT id, action_month, status FROM acb_runs WHERE status IN ("
                        + String.join(", ", LIVE_UNSENT.stream().map(s -> "?").toList()) + ") ORDER BY action_month, id",
                (rs, n) -> new AcbDebitDayMoveDto.LiveRun(rs.getLong(1),
                        YearMonth.from(rs.getDate(2).toLocalDate()).toString(), rs.getString(3)),
                LIVE_UNSENT.toArray());
        return new AcbDebitDayMoveDto(bank.id(), bank.name(), day, debtors.size() - already, already, byCurrentDay,
                live, moved);
    }

    /** The bank's debit-order members, read in one statement -- locked for a move. */
    private List<Debtor> debtors(Bank bank, boolean lock) {
        return jdbc.query("SELECT m.id, m.debit_day FROM members m WHERE LOWER(TRIM(m.bank_name)) = ? AND "
                        + DEBIT_ORDER + " ORDER BY m.id" + (lock ? " FOR UPDATE" : ""),
                (rs, n) -> {
                    int day = rs.getInt(2);
                    return new Debtor(rs.getInt(1), rs.wasNull() ? null : day);
                }, BankIndex.normalise(bank.name()));
    }

    private Bank requireBank(int bankId) {
        return jdbc.query("SELECT b.id, b.name, (SELECT MIN(s.acb_debit_day) FROM subscriptions s WHERE s.bank_id = b.id "
                                + "AND s.status = ?) FROM banks b WHERE b.id = ?",
                        (rs, n) -> {
                            int bankDay = rs.getInt(3);
                            return new Bank(rs.getInt(1), rs.getString(2), rs.wasNull() ? null : bankDay);
                        }, SubscriptionsStatus.ACTIVE.name(), bankId).stream().findFirst()
                .orElseThrow(() -> new AcbRefusal(AcbRefusalCode.BANK_NOT_FOUND, "There is no bank " + bankId + ".",
                        Map.of("bankId", String.valueOf(bankId))));
    }

    private static int requireDay(Integer day) {
        if (day == null || !DebitDay.isInRange(day)) {
            throw new AcbRefusal(AcbRefusalCode.DEBIT_DAY_INVALID, "A debit day is from the 20th to the 31st.",
                    Map.of("day", String.valueOf(day)));
        }
        return day;
    }
}
