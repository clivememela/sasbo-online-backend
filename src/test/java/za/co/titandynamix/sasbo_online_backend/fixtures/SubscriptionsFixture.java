package za.co.titandynamix.sasbo_online_backend.fixtures;

import org.springframework.jdbc.core.JdbcTemplate;
import za.co.titandynamix.sasbo_online_backend.entity.bank.Bank;
import za.co.titandynamix.sasbo_online_backend.enums.SubscriptionsStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The {@code subscriptions} domain's fixture. {@link BankFixture} is the reference for the rule
 * it follows: a valid baseline, so a test overrides only the field it is about.
 *
 * <h2>Why this one writes SQL instead of returning an entity</h2>
 * {@code Subscriptions} carries an <b>assigned</b> {@code @Id} with no {@code @GeneratedValue}
 * and does not implement {@code Persistable}, so {@code repository.save()} sees a non-null id,
 * decides the entity is not new, and issues a {@code merge} — an {@code UPDATE} that matches no
 * row, which surfaces as an optimistic-locking failure rather than as an insert.
 *
 * <p>Production never hits that, because the mapper ignores the id on create and Hibernate then
 * persists. It is only a problem for a test that wants to choose its own ids, and every test that
 * seeds a subscription wants exactly that — so the insert goes through SQL, and the entity mapping
 * stays out of tests that are not about it.
 *
 * <h2>Why it is shared rather than copied</h2>
 * Two tests each writing this seven-column {@code INSERT} is two specifications of what a valid
 * subscription is, and they drift the day a required column is added: one lane reddens with a
 * constraint violation and the other does not, and the difference looks like a database problem.
 * The column list is written once, here.
 */
public final class SubscriptionsFixture {

    /** Fixed. Nothing that seeds a subscription reads the wall clock. */
    public static final LocalDateTime REGISTERED = LocalDateTime.of(2024, 3, 1, 9, 0);

    /** The baseline debit day: a day that exists in every month, and not the 20th boundary. */
    public static final int ACB_DEBIT_DAY = 25;

    /** Two decimals, and not a round number, so a dropped scale is visible. */
    public static final BigDecimal SUBSCRIPTION_AMOUNT = new BigDecimal("120.50");

    private SubscriptionsFixture() {
    }

    /**
     * Inserts one ACTIVE subscription with the given id, on the given bank.
     *
     * @param bank the bank to attach it to, or {@code null} for a subscription with none — which
     *             is a shape the schema permits and a fetch join has to survive
     */
    public static void insert(JdbcTemplate jdbc, Long id, Bank bank) {
        jdbc.update(
                "INSERT INTO subscriptions "
                        + "(id, acb_debit_day, core_banking, registration_date, status, "
                        + " subscription_amount, bank_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, ACB_DEBIT_DAY, true, REGISTERED, SubscriptionsStatus.ACTIVE.name(),
                SUBSCRIPTION_AMOUNT, bank == null ? null : bank.getId());
    }
}
