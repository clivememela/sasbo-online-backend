package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRatesDto;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbRate;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunlessEvent;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.AcbRateRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbRunRepository;
import za.co.titandynamix.sasbo_online_backend.services.AcbRateService;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The dated flat rate (Q43, D25): listed openly, added ahead of its month, never edited.
 *
 * <p>An add goes through JDBC so that two administrators adding one month at once meet the table's
 * unique index -- one row, and one {@code RATE_ALREADY_SET}, never a 500 (T2-F; F64's pattern).
 */
@Service
public class AcbRateServiceImpl implements AcbRateService {

    /** The widest amount the column holds, and more than any subscription will be. */
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("99999.99");

    private final AcbRateRepository rates;
    private final AcbRunRepository runs;
    private final AcbAuditTrail audit;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final TransactionTemplate tx;

    public AcbRateServiceImpl(AcbRateRepository rates, AcbRunRepository runs, AcbAuditTrail audit, JdbcTemplate jdbc,
                              Clock clock, PlatformTransactionManager transactions) {
        this.rates = rates;
        this.runs = runs;
        this.audit = audit;
        this.jdbc = jdbc;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactions);
    }

    @Override
    public AcbRatesDto rates() {
        List<AcbRate> all = rates.findAllByOrderByEffectiveFromAsc();
        return new AcbRatesDto(all.stream()
                .map(r -> new AcbRatesDto.Rate(AcbTime.iso(r.getEffectiveFrom()), r.getAmount(), r.getSource()))
                .toList(), januaryNotice(all, AcbTime.today(clock)));
    }

    @Override
    public AcbRatesDto add(String effectiveFrom, BigDecimal amount, String source, String actor) {
        LocalDate from = parseFirstOfMonth(effectiveFrom);
        String words = source == null ? "" : source.trim();
        if (amount == null || amount.signum() <= 0 || amount.compareTo(MAX_AMOUNT) > 0 || amount.scale() > 2
                || words.isEmpty() || words.length() > 255) {
            throw new AcbRefusal(AcbRefusalCode.REQUEST_MALFORMED, "A rate needs a positive amount in rand and cents, "
                    + "and a note of where it came from in at most 255 characters.", Map.of("effectiveFrom", from.toString()));
        }
        YearMonth month = YearMonth.from(from);
        if (!month.isAfter(AcbTime.currentMonth(clock))) {
            throw new AcbRefusal(AcbRefusalCode.RATE_NOT_FUTURE, "A new rate starts on the first of a month still to "
                    + "come; a rate already in force is never edited.", Map.of("effectiveFrom", from.toString()));
        }
        try {
            tx.executeWithoutResult(status -> {
                if (rates.findAll().stream().anyMatch(r -> r.getEffectiveFrom().equals(from))) {
                    throw alreadySet(from);
                }
                runs.findLive(from, AcbRuns.RELEASED).ifPresent(live -> {
                    Map<String, String> facts = new LinkedHashMap<>();
                    facts.put("effectiveFrom", from.toString());
                    facts.put("runId", String.valueOf(live.getId()));
                    throw new AcbRefusal(AcbRefusalCode.RATE_MONTH_CHARGED, "The run for " + month + " has already "
                            + "been prepared at the rate before it. Cancel it first, then add the rate and prepare "
                            + "the month again.", facts);
                });
                jdbc.update("INSERT INTO acb_rates (effective_from, amount, source) VALUES (?, ?, ?)", from, amount, words);
                audit.recordRunless(AcbRunlessEvent.RATE_ADDED, "R " + amount.setScale(2).toPlainString() + " from " + month,
                        actor);
            });
        } catch (DataIntegrityViolationException lostTheRace) {
            throw alreadySet(from);
        }
        return rates();
    }

    /**
     * 🧪 From the first of December: when next month is a January with no rate dated that January, which
     * amount it will charge and since when (SD3-G). {@code today} is an argument, never read here (T2-G).
     */
    static String januaryNotice(List<AcbRate> all, LocalDate today) {
        YearMonth next = YearMonth.from(today).plusMonths(1);
        if (next.getMonth() != Month.JANUARY) {
            return null;
        }
        LocalDate january = next.atDay(1);
        if (all.stream().anyMatch(r -> r.getEffectiveFrom().equals(january))) {
            return null;
        }
        Optional<AcbRate> inForce = all.stream().filter(r -> !r.getEffectiveFrom().isAfter(january))
                .reduce((earlier, later) -> later.getEffectiveFrom().isAfter(earlier.getEffectiveFrom()) ? later : earlier);
        return inForce.map(r -> next + " will charge R " + r.getAmount().setScale(2).toPlainString() + ", the rate since "
                        + YearMonth.from(r.getEffectiveFrom()) + ". If January's rate changes, enter it before January is prepared.")
                .orElse("No rate is dated for " + next + " or before it, so January cannot be priced. Enter its rate first.");
    }

    private static AcbRefusal alreadySet(LocalDate from) {
        return new AcbRefusal(AcbRefusalCode.RATE_ALREADY_SET, "A rate is already dated " + YearMonth.from(from)
                + ". Rates are added, never edited.", Map.of("effectiveFrom", from.toString()));
    }

    private static LocalDate parseFirstOfMonth(String text) {
        try {
            LocalDate date = LocalDate.parse(text == null ? "" : text.trim());
            if (date.getDayOfMonth() != 1) {
                throw new AcbRefusal(AcbRefusalCode.DATE_INVALID, "A rate starts on the first of a month.",
                        Map.of("effectiveFrom", date.toString()));
            }
            return date;
        } catch (DateTimeParseException malformed) {
            throw new AcbRefusal(AcbRefusalCode.DATE_INVALID, "Dates are written YYYY-MM-DD, for example 2027-01-01.",
                    Map.of("effectiveFrom", String.valueOf(text)));
        }
    }
}
