package za.co.titandynamix.sasbo_online_backend.acb;

/**
 * A bank on record: the three columns a run needs.
 *
 * <p>Top-level rather than nested in {@link BankIndex} because a JPQL constructor expression names
 * it by its fully qualified class name, and a nested record's binary name is one {@code $} away from
 * a query that fails at startup.
 */
public record BankRow(Integer id, String name, String universalBranchCode) {
}
