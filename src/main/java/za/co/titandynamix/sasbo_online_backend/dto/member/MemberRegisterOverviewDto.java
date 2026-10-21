package za.co.titandynamix.sasbo_online_backend.dto.member;

import java.util.List;

/**
 * The register's figure strip and its Branch criterion, for the caller's own register (register row A10).
 *
 * <p>The strip was counted in the browser from every member the register held; a register read a page at a time holds
 * one page, so the figures are counted here -- over the same members the pages are drawn from (the caller's region or
 * area), never over the page on screen. They describe the REGISTER, not the current search: totals that move as you
 * type are a different instrument.
 *
 * @param total          every member in the register
 * @param active         of status Active
 * @param pending        of status Pending
 * @param suspended      of status Suspended
 * @param newThisMonth   whose Date Joined is in {@code asOf}'s month (SASBO, 18 Sep 2026: "new members captured")
 * @param newDebitOrders of those, paying by debit order ("total number of new debit orders captured for the month")
 * @param updatedToday   whose record was saved on {@code asOf}
 * @param branches       every branch the register's members are at, once, in order -- so the Branch criterion never
 *                       offers a branch with no members; at most {@code MemberRegisterService.MAX_BRANCHES}
 * @param asOf           the day the figures are of, in South Africa ({@code yyyy-MM-dd}, MD16)
 */
public record MemberRegisterOverviewDto(
        long total,
        long active,
        long pending,
        long suspended,
        long newThisMonth,
        long newDebitOrders,
        long updatedToday,
        List<String> branches,
        String asOf) {
}
