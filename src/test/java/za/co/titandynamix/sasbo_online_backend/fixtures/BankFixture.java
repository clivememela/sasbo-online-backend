package za.co.titandynamix.sasbo_online_backend.fixtures;

import za.co.titandynamix.sasbo_online_backend.entity.bank.Bank;

import java.util.ArrayList;

/**
 * The {@code bank} domain's fixture builder. One per domain, and the reference
 * for the other seven.
 *
 * <h2>The rule it exists to enforce</h2>
 * A builder returns a <b>valid baseline</b>. A test then overrides only the one
 * field it is about, so its intent is visible in a single line and it does not
 * redden the day an unrelated required column is added. Tests that each write
 * their own literals drift apart, and the drift shows up as a test that passes
 * in one lane and fails in the other.
 *
 * <h2>The same builder serves both lanes</h2>
 * Fast H2 tests and {@code *IT}s against the real container use this, not two
 * fixture sets for one domain. Two sets are two specifications.
 *
 * <h2>Names are collation-neutral, deliberately</h2>
 * Any value that can end up in an {@code ORDER BY} assertion is chosen so that
 * {@code C}, {@code en_ZA.UTF-8} and {@code en_US.UTF-8} all agree on the
 * order: plainly-separated ASCII, no leading lowercase particle
 * ({@code de Villiers}, {@code van Zyl}), no apostrophe or hyphen
 * ({@code O'Brien}, {@code Smit-Nel}), no diacritic, and no pair differing only
 * by case. Those are exactly the names the three collations disagree about --
 * and they are ordinary South African names, which is why this needs a rule and
 * not good intentions.
 *
 * <p>Realism is the wrong instinct here. A sort test proves the {@code ORDER
 * BY} clause is right; the names are an implementation detail of that proof,
 * and a realistic set is maximally likely to contain the disagreeing cases.
 */
public final class BankFixture {

    /** A real South African universal branch code shape: six digits. */
    public static final String UNIVERSAL_BRANCH_CODE = "632005";

    private BankFixture() {
    }

    /** A valid, unsaved bank. Override only the field the test is about. */
    public static Bank aBank() {
        return named("Absa");
    }

    /** A valid, unsaved bank with the given name. */
    public static Bank named(String name) {
        Bank bank = new Bank();
        bank.setName(name);
        bank.setUniversalBranchCode(UNIVERSAL_BRANCH_CODE);
        bank.setBranches(new ArrayList<>());
        return bank;
    }

    /**
     * A saved-looking bank, for tests that never touch a database and just need
     * an identifier present.
     */
    public static Bank withId(Integer id, String name) {
        Bank bank = named(name);
        bank.setId(id);
        return bank;
    }

    /**
     * Four names whose order is identical under {@code C},
     * {@code en_ZA.UTF-8} and {@code en_US.UTF-8}. Used by every sort
     * assertion in this domain, so that such a test can only fail because of
     * the code.
     */
    public static final String[] COLLATION_NEUTRAL_NAMES = {
        "Absa", "Capitec", "Nedbank", "Standard",
    };
}
